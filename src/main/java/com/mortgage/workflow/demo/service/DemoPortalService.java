package com.mortgage.workflow.demo.service;

import com.mortgage.workflow.demo.auth.DemoRole;
import com.mortgage.workflow.demo.auth.DemoUser;
import com.mortgage.workflow.demo.integration.DemoIntegrationLog;
import com.mortgage.workflow.demo.model.DemoApplicationStore;
import com.mortgage.workflow.demo.model.DemoApplicationSummary;
import com.mortgage.workflow.demo.model.DemoFlowProfile;
import com.mortgage.workflow.demo.web.dto.DemoAppraisalRequest;
import com.mortgage.workflow.demo.web.dto.DemoApplicationStatusView;
import com.mortgage.workflow.demo.web.dto.DemoEdocUploadRequest;
import com.mortgage.workflow.demo.web.dto.DemoHumanTaskDecisionRequest;
import com.mortgage.workflow.demo.web.dto.DemoReasonRequest;
import com.mortgage.workflow.demo.web.dto.DemoStartApplicationRequest;
import com.mortgage.workflow.service.ProcessDiagnosticsService;
import com.mortgage.workflow.service.ProcessDiagnosticsService.ActiveElement;
import com.mortgage.workflow.service.ProcessDiagnosticsService.IncidentSummary;
import com.mortgage.workflow.service.ProcessDiagnosticsService.JobSummary;
import com.mortgage.workflow.service.ProcessInstanceQueryService;
import com.mortgage.workflow.service.WorkflowTaskService;
import com.mortgage.workflow.web.dto.CompleteUserTaskRequest;
import com.mortgage.workflow.web.dto.StartProcessRequest;
import com.mortgage.workflow.web.dto.StartProcessResponse;
import com.mortgage.workflow.web.dto.UserTaskView;
import io.camunda.client.api.search.response.ProcessInstance;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "application-workflow.demo", name = "enabled", havingValue = "true")
public class DemoPortalService {

	private final WorkflowTaskService workflowTaskService;
	private final ProcessInstanceQueryService processInstanceQueryService;
	private final DemoApplicationStore applicationStore;
	private final DemoIntegrationLog integrationLog;
	private final ProcessDiagnosticsService processDiagnosticsService;

	public DemoPortalService(
			WorkflowTaskService workflowTaskService,
			ProcessInstanceQueryService processInstanceQueryService,
			DemoApplicationStore applicationStore,
			DemoIntegrationLog integrationLog,
			ProcessDiagnosticsService processDiagnosticsService) {
		this.workflowTaskService = workflowTaskService;
		this.processInstanceQueryService = processInstanceQueryService;
		this.applicationStore = applicationStore;
		this.integrationLog = integrationLog;
		this.processDiagnosticsService = processDiagnosticsService;
	}

	// ---------------------------------------------------------------- start

	public DemoApplicationSummary startApplication(DemoUser user, DemoStartApplicationRequest request) {
		requireRole(user, DemoRole.ADVISOR);
		DemoFlowProfile profile = request.flowProfile() != null ? request.flowProfile() : DemoFlowProfile.HAPPY_AUTO;
		String applicationType = request.applicationType() != null && !request.applicationType().isBlank()
				? request.applicationType()
				: "Mortgage";
		String applicationId = "APP-" + Instant.now().toEpochMilli() + "-"
				+ UUID.randomUUID().toString().substring(0, 4).toUpperCase();

		StartProcessResponse started = workflowTaskService.startApplicationWorkflow(new StartProcessRequest(
				null,
				applicationId,
				applicationType,
				profile.integrationScenario(),
				profile.demoCreditScore(),
				request.customerName(),
				request.financials()));

		DemoApplicationSummary summary = new DemoApplicationSummary(
				applicationId,
				started.processInstanceKey(),
				applicationType,
				profile,
				user.username(),
				Instant.now());
		applicationStore.save(summary);
		integrationLog.record(
				applicationId,
				"Core Banking",
				"createMortgageFileAndValidateInput",
				"Advisor " + user.displayName() + " submitted " + applicationType + " (" + profile.name() + ")");
		return summary;
	}

	// ------------------------------------------------------------ messages

	public void simulateEdocUpload(DemoUser user, String applicationId, DemoEdocUploadRequest request) {
		requireRole(user, DemoRole.ADVISOR, DemoRole.ECM_SYSTEM);
		ensureKnownApplication(applicationId);
		String documentId = blankToNull(request.documentId()) != null
				? request.documentId()
				: "DOC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String status = blankToNull(request.documentStatus()) != null ? request.documentStatus() : "UPLOADED";

		Map<String, Object> variables = new HashMap<>();
		variables.put("documentId", documentId);
		variables.put("documentStatus", status);
		variables.put("documentType", blankToNull(request.documentType()));

		workflowTaskService.correlateMessage(WorkflowTaskService.MESSAGE_EDOC_UPLOADED, applicationId, variables);
		integrationLog.record(
				applicationId,
				user.role() == DemoRole.ECM_SYSTEM ? "ECM (Alfresco/OnBase)" : "Advisor portal",
				WorkflowTaskService.MESSAGE_EDOC_UPLOADED,
				"documentId=" + documentId + ", type=" + (request.documentType() != null ? request.documentType() : "ALL")
						+ ", status=" + status);
	}

	public void simulateAppraisalCompleted(DemoUser user, String applicationId, DemoAppraisalRequest request) {
		requireRole(user, DemoRole.ECM_SYSTEM, DemoRole.OPERATIONS, DemoRole.ADVISOR);
		ensureKnownApplication(applicationId);
		Map<String, Object> variables = new HashMap<>();
		variables.put("appraisalReportId", "RPT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		if (request != null && request.appraisedValue() != null) {
			variables.put("appraisedValue", request.appraisedValue());
		}
		workflowTaskService.correlateMessage(WorkflowTaskService.MESSAGE_APPRAISAL_COMPLETED, applicationId, variables);
		integrationLog.record(applicationId, "Appraisal management company",
				WorkflowTaskService.MESSAGE_APPRAISAL_COMPLETED,
				"Appraisal report received" + (variables.containsKey("appraisedValue")
						? " (value " + variables.get("appraisedValue") + ")" : ""));
	}

	public void simulateEsignComplete(DemoUser user, String applicationId) {
		requireRole(user, DemoRole.CUSTOMER);
		ensureKnownApplication(applicationId);
		Map<String, Object> variables = Map.of("eSignStatus", "COMPLETED", "eSignedAt", Instant.now().toString());
		workflowTaskService.correlateMessage(WorkflowTaskService.MESSAGE_ESIGN_COMPLETED, applicationId, variables);
		integrationLog.record(applicationId, "eSign provider", WorkflowTaskService.MESSAGE_ESIGN_COMPLETED,
				"Customer completed signing");
	}

	public void simulateClientDeclined(DemoUser user, String applicationId, DemoReasonRequest request) {
		requireRole(user, DemoRole.CUSTOMER);
		ensureKnownApplication(applicationId);
		String reason = request != null && blankToNull(request.reason()) != null
				? request.reason() : "Client chose a competing offer";
		workflowTaskService.correlateMessage(WorkflowTaskService.MESSAGE_CLIENT_DECLINED_COMMITMENT, applicationId,
				Map.of("clientDeclineReason", reason));
		integrationLog.record(applicationId, "eSign provider", WorkflowTaskService.MESSAGE_CLIENT_DECLINED_COMMITMENT,
				"Customer declined the commitment letter: " + reason);
	}

	public void simulateClosingDayConfirmation(DemoUser user, String applicationId) {
		requireRole(user, DemoRole.OPERATIONS, DemoRole.ADVISOR);
		ensureKnownApplication(applicationId);
		Map<String, Object> variables = Map.of(
				"closingConfirmedBy", user.username(),
				"closingStatus", "CONFIRMED",
				"closingConfirmed", true,
				"closingDate", Instant.now().toString());
		workflowTaskService.correlateMessage(WorkflowTaskService.MESSAGE_CLOSING_DAY_CONFIRMED, applicationId, variables);
		integrationLog.record(applicationId, "Solicitor / notary", WorkflowTaskService.MESSAGE_CLOSING_DAY_CONFIRMED,
				"Closing day confirmed for disbursement");
	}

	public void simulateWithdrawal(DemoUser user, String applicationId, DemoReasonRequest request) {
		requireRole(user, DemoRole.CUSTOMER, DemoRole.ADVISOR);
		ensureKnownApplication(applicationId);
		String reason = request != null && blankToNull(request.reason()) != null ? request.reason() : "CLIENT_WITHDRAWN";
		workflowTaskService.correlateMessage(WorkflowTaskService.MESSAGE_APPLICATION_WITHDRAWN, applicationId,
				Map.of("cancelReason", reason));
		integrationLog.record(applicationId, "Advisor portal", WorkflowTaskService.MESSAGE_APPLICATION_WITHDRAWN,
				"Application withdrawn: " + reason);
	}

	// --------------------------------------------------------- human tasks

	/** Open human tasks visible to the persona (filtered by its Camunda candidate groups). */
	public List<UserTaskView> listHumanTasks(DemoUser user, String applicationId) {
		List<UserTaskView> all = workflowTaskService.listOpenUserTasksForProcess();
		List<String> groups = user.role().camundaGroups();
		return all.stream()
				.filter(task -> applicationId == null || applicationId.isBlank() || applicationId.equals(task.businessId()))
				.filter(task -> user.role() == DemoRole.CONDUCTOR || user.role() == DemoRole.OPERATIONS
						|| task.candidateGroups().isEmpty()
						|| task.candidateGroups().stream().anyMatch(groups::contains))
				.toList();
	}

	public void completeHumanTask(DemoUser user, long userTaskKey, DemoHumanTaskDecisionRequest request) {
		if (user.role() == DemoRole.CUSTOMER || user.role() == DemoRole.ECM_SYSTEM) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role " + user.role() + " cannot complete bank tasks");
		}
		if (request == null || request.variables() == null || request.variables().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "variables with the decision are required");
		}
		Map<String, Object> variables = new HashMap<>(request.variables());
		variables.values().removeIf(v -> v == null || (v instanceof String s && s.isBlank()));
		if (blankToNull(request.comment()) != null) {
			variables.put("lastReviewComment", request.comment());
		}
		variables.put("lastReviewedBy", user.displayName());
		workflowTaskService.assignUserTask(userTaskKey, user.username());
		workflowTaskService.completeUserTask(userTaskKey, new CompleteUserTaskRequest("complete", variables));
		integrationLog.record("-", "Human task", "completeUserTask",
				user.displayName() + " completed task " + userTaskKey + " with " + request.variables());
	}

	// -------------------------------------------------------------- status

	public List<DemoApplicationSummary> listApplications(DemoUser user) {
		boolean viewAll = user.role() != DemoRole.ADVISOR;
		return applicationStore.listForUser(user.username(), viewAll);
	}

	public DemoApplicationStatusView getStatus(DemoUser user, String applicationId) {
		ensureKnownApplication(applicationId);
		var instance = processInstanceQueryService.findLatestByBusinessId(applicationId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Process instance not found"));

		long processInstanceKey = instance.getProcessInstanceKey();
		Map<String, Object> variables = processInstanceQueryService.snapshotVariables(processInstanceKey);
		List<ActiveElement> activeElements = ProcessInstanceQueryService.isActive(instance)
				? processDiagnosticsService.activeElements(processInstanceKey)
				: List.of();
		List<UserTaskView> openTasks = ProcessInstanceQueryService.isActive(instance)
				? workflowTaskService.listOpenUserTasksForInstance(processInstanceKey)
				: List.of();
		var activeJobs = processDiagnosticsService.activeJobs(processInstanceKey);
		var failedJobs = processDiagnosticsService.failedJobs(processInstanceKey);
		var incidents = processDiagnosticsService.openIncidents(processInstanceKey);

		DemoStageResolver.Stage stage = inferStage(instance, activeElements, variables);
		return new DemoApplicationStatusView(
				applicationId,
				processInstanceKey,
				instance.getState() != null ? instance.getState().name() : "UNKNOWN",
				stage.label(),
				stage.stageKey(),
				stage.suggestedRole(),
				buildSystemHint(stage, variables, activeJobs, failedJobs, incidents),
				activeElements,
				openTasks,
				activeJobs,
				failedJobs,
				incidents,
				variables);
	}

	private static DemoStageResolver.Stage inferStage(
			ProcessInstance instance, List<ActiveElement> activeElements, Map<String, Object> variables) {
		if (!ProcessInstanceQueryService.isActive(instance)) {
			return DemoStageResolver.terminal(variables.get("applicationStatus"));
		}
		DemoStageResolver.Stage resolved = DemoStageResolver.resolve(activeElements);
		if (resolved != null) {
			return resolved;
		}
		return new DemoStageResolver.Stage("SUBMITTED", "In progress", DemoStageResolver.SYSTEM);
	}

	private static String buildSystemHint(
			DemoStageResolver.Stage stage,
			Map<String, Object> variables,
			List<JobSummary> activeJobs,
			List<JobSummary> failedJobs,
			List<IncidentSummary> incidents) {
		if (!incidents.isEmpty()) {
			return "Incident open at " + incidents.getFirst().elementId() + ": " + incidents.getFirst().errorMessage()
					+ " — resolve in Operate (retry) or fix the integration.";
		}
		if (!failedJobs.isEmpty()) {
			JobSummary job = failedJobs.getFirst();
			return "Job " + job.type() + " failed, retries left: " + job.retries()
					+ " (exponential backoff). Ensure application-workflow is connected to Zeebe.";
		}
		if (!activeJobs.isEmpty()) {
			return "Worker running: " + activeJobs.stream().map(JobSummary::type).distinct()
					.reduce((a, b) -> a + ", " + b).orElse("unknown");
		}
		return switch (stage.stageKey()) {
			case "DOCUMENTATION" -> "Outstanding documents: " + variables.getOrDefault("outstandingDocuments", "all")
					+ ". Reminders every 7 days (x3); documents expire after 30 days.";
			case "APPRAISAL" -> "Appraisal follow-up task is created automatically after 10 days.";
			case "UNDERWRITING" -> "GDS " + variables.get("gdsRatio") + "% / TDS " + variables.get("tdsRatio")
					+ "% / LTV " + variables.get("ltvRatio") + "%. Underwriter SLA 48h, reassigned after 5 days.";
			case "COMMITMENT" -> "eSign reminders every 3 days; commitment expires after 14 days.";
			case "CLOSING" -> "Closing delay task is raised after 45 days without confirmation.";
			case "FUNDING" -> "Funding readiness DMN checks closing, solicitor instructions, insurance and conditions.";
			default -> "Status refreshes every 3s.";
		};
	}

	// ------------------------------------------------------------- helpers

	private void ensureKnownApplication(String applicationId) {
		if (applicationId == null || applicationId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "applicationId is required");
		}
		applicationStore.find(applicationId).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown demo application: " + applicationId));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static void requireRole(DemoUser user, DemoRole... allowed) {
		if (user.role() == DemoRole.CONDUCTOR) {
			return;
		}
		for (DemoRole role : allowed) {
			if (user.role() == role) {
				return;
			}
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role " + user.role() + " cannot perform this action");
	}
}
