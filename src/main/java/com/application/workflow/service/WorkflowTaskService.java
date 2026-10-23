package com.application.workflow.service;

import com.application.workflow.integration.camunda.CamundaMessageProvider;
import com.application.workflow.integration.camunda.CorrelationResult;
import com.application.workflow.web.dto.CompleteUserTaskRequest;
import com.application.workflow.web.dto.MessageCorrelationResponse;
import com.application.workflow.web.dto.StartProcessRequest;
import com.application.workflow.web.dto.StartProcessResponse;
import com.application.workflow.web.dto.UserTaskView;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.UserTask;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class WorkflowTaskService {

	public static final String MESSAGE_EDOC_UPLOADED = "eDocUploadedEvent";
	public static final String MESSAGE_ESIGN_COMPLETED = "eSignCompletedEvent";

	private final CamundaClient camundaClient;
	private final CamundaMessageProvider camundaMessageProvider;

	public WorkflowTaskService(CamundaClient camundaClient, CamundaMessageProvider camundaMessageProvider) {
		this.camundaClient = camundaClient;
		this.camundaMessageProvider = camundaMessageProvider;
	}

	public StartProcessResponse startGeneralWorkflow(StartProcessRequest request) {
		String applicationId = request.applicationId();
		var command = camundaClient.newCreateInstanceCommand()
				.bpmnProcessId("general_workflow")
				.latestVersion();

		Map<String, Object> startVariables = new HashMap<>();
		if (StringUtils.isNotBlank(applicationId)) {
			command = command.businessId(applicationId);
			startVariables.put("applicationId", applicationId);
		}
		if (StringUtils.isNotBlank(request.applicationType())) {
			startVariables.put("applicationType", request.applicationType());
		}
		if (StringUtils.isNotBlank(request.requestId())) {
			startVariables.put("requestId", request.requestId());
		}
		if (StringUtils.isNotBlank(request.integrationScenario())) {
			startVariables.put("integrationScenario", request.integrationScenario());
		}
		if (Objects.nonNull(request.demoCreditScore())) {
			startVariables.put("demoCreditScore", request.demoCreditScore());
		}
		if (StringUtils.isNotBlank(request.customerName())) {
			startVariables.put("customerName", request.customerName());
		}
		if (StringUtils.isNotBlank(request.demoRequiredDocuments())) {
			startVariables.put("demoRequiredDocuments", request.demoRequiredDocuments());
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
					if (StringUtils.isNotBlank(assignee)) {
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

	public void assignUserTask(long userTaskKey, String assignee) {
		camundaClient.newAssignUserTaskCommand(userTaskKey).assignee(assignee).execute();
	}

	public MessageCorrelationResponse correlateMessage(String messageName, String applicationId) {
		return correlateMessage(messageName, applicationId, new HashMap<>());
	}

	public MessageCorrelationResponse correlateMessage(
			String messageName, String applicationId, Map<String, Object> variables) {
		if (StringUtils.isBlank(applicationId)) {
			throw new IllegalArgumentException("applicationId is required for message correlation");
		}
		CorrelationResult correlation = camundaMessageProvider.correlateOrBuffer(messageName, applicationId, variables);
		long processInstanceKey = correlation.processInstanceKey();
		if (processInstanceKey <= 0L) {
			processInstanceKey = camundaClient.newProcessInstanceSearchRequest()
					.filter(filter -> filter.businessId(applicationId))
					.page(p -> p.limit(1))
					.execute()
					.items()
					.stream()
					.findFirst()
					.map(i -> i.getProcessInstanceKey())
					.orElse(0L);
		}
		return new MessageCorrelationResponse(
				processInstanceKey > 0L ? processInstanceKey : null,
				messageName,
				applicationId,
				correlation.outcome().name(),
				correlation.outcome().detailMessage());
	}

	public void completeUserTask(long userTaskKey, CompleteUserTaskRequest request) {
		var command = camundaClient.newCompleteUserTaskCommand(userTaskKey);
		if (StringUtils.isNotBlank(request.action())) {
			command = command.action(request.action());
		}
		if (Objects.nonNull(request.variables()) && !request.variables().isEmpty()) {
			command = command.variables(new HashMap<>(request.variables()));
		}
		command.execute();
	}

	private UserTaskView toView(UserTask task) {
		return new UserTaskView(
				task.getUserTaskKey(),
				task.getName(),
				Objects.nonNull(task.getState()) ? task.getState().name() : "",
				task.getAssignee(),
				task.getElementId(),
				task.getProcessInstanceKey(),
				task.getBpmnProcessId(),
				task.getBusinessId());
	}

}
