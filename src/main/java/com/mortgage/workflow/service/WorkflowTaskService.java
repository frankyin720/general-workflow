package com.mortgage.workflow.service;

import com.mortgage.workflow.integration.camunda.CamundaMessageBridge;
import com.mortgage.workflow.web.dto.CompleteUserTaskRequest;
import com.mortgage.workflow.web.dto.MessageCorrelationResponse;
import com.mortgage.workflow.web.dto.StartProcessRequest;
import com.mortgage.workflow.web.dto.StartProcessResponse;
import com.mortgage.workflow.web.dto.UserTaskView;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTaskService {

	public static final String PROCESS_ID = "mortgage_workflow";

	/** Branch A: a financial document was uploaded to ECM (variables: documentId, documentType, documentStatus). */
	public static final String MESSAGE_EDOC_UPLOADED = "eDocUploadedEvent";
	/** Branch B: appraisal report received (variables: appraisedValue, appraisalReportId). */
	public static final String MESSAGE_APPRAISAL_COMPLETED = "appraisalCompletedEvent";
	/** Client signed the commitment letter (variables: eSignEnvelopeId, eSignStatus). */
	public static final String MESSAGE_ESIGN_COMPLETED = "eSignCompletedEvent";
	/** Client explicitly declined the commitment letter (variables: clientDeclineReason). */
	public static final String MESSAGE_CLIENT_DECLINED_COMMITMENT = "clientDeclinedCommitmentEvent";
	/** Solicitor / notary confirmed closing day (variables: closingStatus, closingDate). */
	public static final String MESSAGE_CLOSING_DAY_CONFIRMED = "closingDayConfirmedEvent";
	/** Client withdrew the application at any point (variables: cancelReason). */
	public static final String MESSAGE_APPLICATION_WITHDRAWN = "applicationWithdrawnEvent";

	private final CamundaClient camundaClient;
	private final CamundaMessageBridge camundaMessageBridge;

	public WorkflowTaskService(CamundaClient camundaClient, CamundaMessageBridge camundaMessageBridge) {
		this.camundaClient = camundaClient;
		this.camundaMessageBridge = camundaMessageBridge;
	}

	public StartProcessResponse startApplicationWorkflow(StartProcessRequest request) {
		String applicationId = request.applicationId();
		var command = camundaClient.newCreateInstanceCommand()
				.bpmnProcessId(PROCESS_ID)
				.latestVersion();

		Map<String, Object> startVariables = new HashMap<>();
		if (applicationId != null && !applicationId.isBlank()) {
			command = command.businessId(applicationId);
			startVariables.put("applicationId", applicationId);
		}
		if (request.applicationType() != null && !request.applicationType().isBlank()) {
			startVariables.put("applicationType", request.applicationType());
		}
		if (request.requestId() != null && !request.requestId().isBlank()) {
			startVariables.put("requestId", request.requestId());
		}
		if (request.integrationScenario() != null && !request.integrationScenario().isBlank()) {
			startVariables.put("integrationScenario", request.integrationScenario());
		}
		if (request.demoCreditScore() != null) {
			startVariables.put("demoCreditScore", request.demoCreditScore());
		}
		if (request.customerName() != null && !request.customerName().isBlank()) {
			startVariables.put("customerName", request.customerName());
		}
		if (request.financials() != null) {
			request.financials().forEach((key, value) -> {
				if (value != null) {
					startVariables.put(key, value);
				}
			});
		}
		if (!startVariables.isEmpty()) {
			command = command.variables(startVariables);
		}

		var instance = command.execute();
		return new StartProcessResponse(instance.getProcessInstanceKey(), instance.getBpmnProcessId());
	}

	public List<UserTaskView> listOpenUserTasks(String assignee) {
		return camundaClient.newUserTaskSearchRequest()
				.filter(filter -> {
					filter.state(UserTaskState.CREATED);
					if (assignee != null && !assignee.isBlank()) {
						filter.assignee(assignee);
					}
				})
				.execute()
				.items()
				.stream()
				.map(this::toView)
				.toList();
	}

	public List<UserTaskView> listOpenUserTasksForCandidateGroup(String candidateGroup) {
		return camundaClient.newUserTaskSearchRequest()
				.filter(filter -> {
					filter.state(UserTaskState.CREATED);
					filter.candidateGroup(candidateGroup);
				})
				.execute()
				.items()
				.stream()
				.map(this::toView)
				.toList();
	}

	/** All open human tasks of the process (any candidate group), oldest first. */
	public List<UserTaskView> listOpenUserTasksForProcess() {
		return camundaClient.newUserTaskSearchRequest()
				.filter(filter -> {
					filter.state(UserTaskState.CREATED);
					filter.bpmnProcessId(PROCESS_ID);
				})
				.sort(sort -> sort.creationDate().asc())
				.page(p -> p.limit(100))
				.execute()
				.items()
				.stream()
				.map(this::toView)
				.toList();
	}

	public List<UserTaskView> listOpenUserTasksForInstance(long processInstanceKey) {
		return camundaClient.newUserTaskSearchRequest()
				.filter(filter -> {
					filter.state(UserTaskState.CREATED);
					filter.processInstanceKey(processInstanceKey);
				})
				.execute()
				.items()
				.stream()
				.map(this::toView)
				.toList();
	}

	public void assignUserTask(long userTaskKey, String assignee) {
		camundaClient.newAssignUserTaskCommand(userTaskKey).assignee(assignee).allowOverride(true).execute();
	}

	public MessageCorrelationResponse correlateMessage(String messageName, String applicationId) {
		return correlateMessage(messageName, applicationId, Map.of());
	}

	public MessageCorrelationResponse correlateMessage(
			String messageName, String applicationId, Map<String, Object> variables) {
		if (applicationId == null || applicationId.isBlank()) {
			throw new IllegalArgumentException("applicationId is required for message correlation");
		}
		camundaMessageBridge.correlateOrBuffer(messageName, applicationId, variables);
		var instance = camundaClient.newProcessInstanceSearchRequest()
				.filter(filter -> filter.businessId(applicationId))
				.page(p -> p.limit(1))
				.execute()
				.items()
				.stream()
				.findFirst();
		long processInstanceKey = instance.map(i -> i.getProcessInstanceKey()).orElse(0L);
		return new MessageCorrelationResponse(processInstanceKey, messageName, applicationId);
	}

	public void completeUserTask(long userTaskKey, CompleteUserTaskRequest request) {
		var command = camundaClient.newCompleteUserTaskCommand(userTaskKey);
		if (request.action() != null && !request.action().isBlank()) {
			command = command.action(request.action());
		}
		if (request.variables() != null && !request.variables().isEmpty()) {
			command = command.variables(new HashMap<>(request.variables()));
		}
		command.execute();
	}

	private UserTaskView toView(UserTask task) {
		return new UserTaskView(
				task.getUserTaskKey(),
				task.getName(),
				task.getState() != null ? task.getState().name() : null,
				task.getAssignee(),
				task.getCandidateGroups() != null ? task.getCandidateGroups() : List.of(),
				task.getElementId(),
				task.getProcessInstanceKey(),
				task.getBpmnProcessId(),
				task.getBusinessId(),
				task.getCreationDate() != null ? task.getCreationDate().toString() : null);
	}

}
