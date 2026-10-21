package com.mortgage.workflow.demo.service;

import com.mortgage.workflow.demo.auth.DemoRole;
import com.mortgage.workflow.demo.auth.DemoUser;
import com.mortgage.workflow.demo.integration.DemoIntegrationLog;
import com.mortgage.workflow.demo.model.DemoApplicationStore;
import com.mortgage.workflow.demo.model.DemoApplicationSummary;
import com.mortgage.workflow.demo.model.DemoFlowProfile;
import com.mortgage.workflow.demo.web.dto.DemoApplicationStatusView;
import com.mortgage.workflow.demo.web.dto.DemoEdocUploadRequest;
import com.mortgage.workflow.demo.web.dto.DemoStartApplicationRequest;
import com.mortgage.workflow.demo.web.dto.DemoUnderwritingDecisionRequest;
import com.mortgage.workflow.handler.support.ProcessProgress;
import com.mortgage.workflow.service.ProcessDiagnosticsService;
import com.mortgage.workflow.service.ProcessInstanceQueryService;
import com.mortgage.workflow.service.WorkflowTaskService;
import com.mortgage.workflow.web.dto.CompleteUserTaskRequest;
import com.mortgage.workflow.web.dto.StartProcessRequest;
import com.mortgage.workflow.web.dto.StartProcessResponse;
import com.mortgage.workflow.web.dto.UserTaskView;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.response.ProcessInstance;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(prefix = "application-workflow.demo", name = "enabled", havingValue = "true")
public class DemoPortalService {

	private static final Logger log = LoggerFactory.getLogger(DemoPortalService.class);

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

	public DemoApplicationSummary startApplication(DemoUser user, DemoStartApplicationRequest request) {
		requireRole(user, DemoRole.ADVISOR);
		DemoFlowProfile profile = request.flowProfile() != null ? request.flowProfile() : DemoFlowProfile.HAPPY_AUTO;
		String applicationType = request.applicationType() != null && !request.applicationType().isBlank()
				? request.applicationType()
				: "Mortgage";
		String applicationId = "APP-" + Instant.now().toEpochMilli() + "-"
				+ UUID.randomUUID().toString().substring(0, 4).toUpperCase();

		StartProcessResponse started = workflowTaskService.startMortgageWorkflow(new StartProcessRequest(
				null,
				applicationId,
				applicationType,
				null,
				profile.demoCreditScore(),
				request.customerName()));

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
				"createApplicationRecord",
				"Advisor " + user.displayName() + " submitted " + applicationType + " (" + profile.name() + ")");
		return summary;
	}

	public void simulateEdocUpload(DemoUser user, String applicationId, DemoEdocUploadRequest request) {
		requireRole(user, DemoRole.ADVISOR, DemoRole.ECM_SYSTEM);
		ensureKnownApplication(applicationId);
		String documentId = request.documentId() != null && !request.documentId().isBlank()
				? request.documentId()
				: "DOC-" + UUID.randomUUID().toString().substring(0, 8);
		String status = request.documentStatus() != null && !request.documentStatus().isBlank()
				? request.documentStatus()
				: "UPLOADED";

		Map<String, Object> variables = new HashMap<>();
		variables.put("documentId", documentId);
		variables.put("documentStatus", status);

		workflowTaskService.correlateMessage(
				WorkflowTaskService.MESSAGE_EDOC_UPLOADED, applicationId, variables);
		integrationLog.record(
				applicationId,
				user.role() == DemoRole.ECM_SYSTEM ? "ECM (Alfresco/OnBase)" : "Advisor portal",
				"eDocUploadedEvent",
				"documentId=" + documentId + ", status=" + status);
	}

	public void simulateEsignComplete(DemoUser user, String applicationId) {
		requireRole(user, DemoRole.CUSTOMER);
		ensureKnownApplication(applicationId);
		Map<String, Object> variables = Map.of(
				"eSignEnvelopeId", "ENV-" + UUID.randomUUID().toString().substring(0, 8),
				"eSignStatus", "COMPLETED");
		workflowTaskService.correlateMessage(
				WorkflowTaskService.MESSAGE_ESIGN_COMPLETED, applicationId, variables);
		integrationLog.record(applicationId, "eSign provider", "eSignCompletedEvent", "Customer completed signing");
	}

	public List<UserTaskView> listUnderwritingTasks(DemoUser user) {
		requireRole(user, DemoRole.UNDERWRITER, DemoRole.OPERATIONS);
		return workflowTaskService.listOpenUserTasksForCandidateGroup(DemoRole.UNDERWRITER_CAMUNDA_GROUP);
	}

	public void completeUnderwritingTask(
			DemoUser user, long userTaskKey, DemoUnderwritingDecisionRequest request) {
		requireRole(user, DemoRole.UNDERWRITER);
		String decision = request.underwritingDecision();
		if (decision == null || decision.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "underwritingDecision is required");
		}
		String assignee = user.role() == DemoRole.CONDUCTOR ? "underwriter.jones" : user.username();
		workflowTaskService.assignUserTask(userTaskKey, assignee);
		Map<String, Object> variables = new HashMap<>();
		variables.put("underwritingDecision", decision);
		if (request.rejectionReason() != null && !request.rejectionReason().isBlank()) {
			variables.put("rejectionReason", request.rejectionReason());
		}
		workflowTaskService.completeUserTask(
				userTaskKey, new CompleteUserTaskRequest("complete", variables));
		integrationLog.record(
				"-",
				"Underwriting",
				"manualDecision",
				user.displayName() + " decided " + decision + " on task " + userTaskKey);
	}

	public List<DemoApplicationSummary> listApplications(DemoUser user) {
		boolean viewAll = user.role() == DemoRole.CONDUCTOR
				|| user.role() == DemoRole.OPERATIONS
				|| user.role() == DemoRole.UNDERWRITER
				|| user.role() == DemoRole.ECM_SYSTEM
				|| user.role() == DemoRole.CUSTOMER;
		return applicationStore.listForUser(user.username(), viewAll);
	}

	public DemoApplicationStatusView getStatus(DemoUser user, String applicationId) {
		DemoApplicationSummary summary = applicationStore.find(applicationId).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown demo application: " + applicationId));

		Optional<ProcessInstance> instance = resolveProcessInstance(applicationId, summary.processInstanceKey());
		if (instance.isEmpty()) {
			return unavailableStatus(summary);
		}

		ProcessInstance process = instance.get();
		long processInstanceKey = process.getProcessInstanceKey();
		if (processInstanceKey != summary.processInstanceKey()) {
			applicationStore.save(new DemoApplicationSummary(
					summary.applicationId(),
					processInstanceKey,
					summary.applicationType(),
					summary.flowProfile(),
					summary.startedBy(),
					summary.startedAt()));
		}
		Map<String, Object> variables = safeSnapshotVariables(processInstanceKey);
		StageHint stage = inferStage(process, variables);
		var activeJobs = safeActiveJobs(processInstanceKey);
		var failedJobs = safeFailedJobs(processInstanceKey);
		var incidents = safeOpenIncidents(processInstanceKey);
		return new DemoApplicationStatusView(
				applicationId,
				processInstanceKey,
				process.getState() != null ? process.getState().name() : "UNKNOWN",
				stage.label(),
				stage.suggestedRole(),
				buildSystemHint(variables, activeJobs, failedJobs, incidents),
				activeJobs,
				failedJobs,
				incidents,
				variables);
	}

	private Optional<ProcessInstance> resolveProcessInstance(String applicationId, long processInstanceKey) {
		try {
			return processInstanceQueryService.resolveDemoApplication(applicationId, processInstanceKey);
		} catch (ProblemException e) {
			log.warn("Camunda process search failed for applicationId={}: {}", applicationId, e.getMessage());
			return Optional.empty();
		}
	}

	private DemoApplicationStatusView unavailableStatus(DemoApplicationSummary summary) {
		String hint = "No Camunda process found for "
				+ summary.applicationId()
				+ " (instance key "
				+ summary.processInstanceKey()
				+ "). If you restarted c8run, start a new application from step 1.";
		return new DemoApplicationStatusView(
				summary.applicationId(),
				summary.processInstanceKey(),
				"UNAVAILABLE",
				"Process unavailable",
				DemoRole.ADVISOR.name(),
				hint,
				List.of(),
				List.of(),
				List.of(),
				Map.of(
						"applicationId", summary.applicationId(),
						"applicationType", summary.applicationType(),
						"flowProfile", summary.flowProfile().name()));
	}

	private Map<String, Object> safeSnapshotVariables(long processInstanceKey) {
		try {
			return processInstanceQueryService.snapshotVariables(processInstanceKey);
		} catch (ProblemException e) {
			log.warn("Camunda variable search failed for processInstanceKey={}: {}", processInstanceKey, e.getMessage());
			return Map.of();
		}
	}

	private List<ProcessDiagnosticsService.JobSummary> safeActiveJobs(long processInstanceKey) {
		try {
			return processDiagnosticsService.activeJobs(processInstanceKey);
		} catch (ProblemException e) {
			log.warn("Camunda job search failed for processInstanceKey={}: {}", processInstanceKey, e.getMessage());
			return List.of();
		}
	}

	private List<ProcessDiagnosticsService.JobSummary> safeFailedJobs(long processInstanceKey) {
		try {
			return processDiagnosticsService.failedJobs(processInstanceKey);
		} catch (ProblemException e) {
			return List.of();
		}
	}

	private List<ProcessDiagnosticsService.IncidentSummary> safeOpenIncidents(long processInstanceKey) {
		try {
			return processDiagnosticsService.openIncidents(processInstanceKey);
		} catch (ProblemException e) {
			return List.of();
		}
	}

	private static String buildSystemHint(
			Map<String, Object> variables,
			java.util.List<ProcessDiagnosticsService.JobSummary> activeJobs,
			java.util.List<ProcessDiagnosticsService.JobSummary> failedJobs,
			java.util.List<ProcessDiagnosticsService.IncidentSummary> incidents) {
		if (!incidents.isEmpty()) {
			return "Incident open: " + incidents.getFirst().errorMessage()
					+ " — check Operate or application-workflow logs.";
		}
		if (!failedJobs.isEmpty()) {
			ProcessDiagnosticsService.JobSummary job = failedJobs.getFirst();
			return "Failed job " + job.type() + " (retries left: " + job.retries()
					+ "). Ensure application-workflow is running and connected to Zeebe :26500.";
		}
		if (!activeJobs.isEmpty()) {
			return "Waiting on application-workflow worker: " + activeJobs.stream()
					.map(ProcessDiagnosticsService.JobSummary::type)
					.distinct()
					.reduce((a, b) -> a + ", " + b)
					.orElse("unknown");
		}
		boolean edocsVerified = Boolean.TRUE.equals(variables.get("edocsVerified"));
		if (!edocsVerified) {
			if (variables.get("creditScore") == null) {
				return "Parallel branch: credit bureau mock (triggerCreditCheckAndAml) should complete in seconds.";
			}
			return "Credit/AML finished — correlate eDoc upload (step 2) to continue.";
		}
		if (variables.get("riskDecision") == null) {
			return "Running automated risk assessment (DMN) after eDocs + credit complete.";
		}
		return "System processing — status refreshes every 3s.";
	}

	private record StageHint(String label, String suggestedRole) {
	}

	private static StageHint inferStage(ProcessInstance instance, Map<String, Object> variables) {
		if (ProcessInstanceQueryService.isCompleted(instance)) {
			Object status = variables.get("applicationStatus");
			if (status != null && status.toString().toUpperCase().contains("REJECT")) {
				return new StageHint("Rejected / closed", DemoRole.ADVISOR.name());
			}
			return new StageHint("Funded / completed", DemoRole.OPERATIONS.name());
		}

		String completedSteps = stringVar(variables, "completedSteps");
		Object underwritingDecision = variables.get("underwritingDecision");
		if (underwritingDecision != null && "REJECTED".equalsIgnoreCase(underwritingDecision.toString())) {
			return new StageHint("Underwriting rejected", DemoRole.ADVISOR.name());
		}

		Object riskDecision = variables.get("riskDecision");
		if ("MANUAL_REVIEW".equals(stringVar(variables, "riskDecision")) && underwritingDecision == null) {
			return new StageHint("Manual underwriting review", DemoRole.UNDERWRITER.name());
		}

		boolean edocsVerified = Boolean.TRUE.equals(variables.get("edocsVerified"))
				|| ProcessProgress.includes(completedSteps, ProcessProgress.STEP_EDOCS_VERIFIED);
		if (!edocsVerified) {
			return new StageHint("Waiting for eDoc upload", DemoRole.ADVISOR.name());
		}

		if (riskDecision == null) {
			if (variables.get("creditScore") == null) {
				return new StageHint("Credit bureau & AML (automatic)", "SYSTEM");
			}
			return new StageHint("Automated risk assessment (DMN)", "SYSTEM");
		}

		if ("AUTO_APPROVE".equals(riskDecision) || "APPROVED".equalsIgnoreCase(String.valueOf(underwritingDecision))) {
			if (!ProcessProgress.includes(completedSteps, ProcessProgress.STEP_FUNDING_INITIATED)) {
				return new StageHint("Waiting for eSign completion", DemoRole.CUSTOMER.name());
			}
			return new StageHint("Funding in progress", "SYSTEM");
		}

		return new StageHint("In progress", DemoRole.OPERATIONS.name());
	}

	private static String stringVar(Map<String, Object> variables, String key) {
		Object value = variables.get(key);
		return value != null ? value.toString() : null;
	}

	private void ensureKnownApplication(String applicationId) {
		if (applicationId == null || applicationId.isBlank()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "applicationId is required");
		}
		applicationStore.find(applicationId).orElseThrow(
				() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown demo application: " + applicationId));
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
