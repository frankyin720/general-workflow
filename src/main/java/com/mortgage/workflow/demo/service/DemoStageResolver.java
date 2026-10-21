package com.mortgage.workflow.demo.service;

import com.mortgage.workflow.demo.auth.DemoRole;
import com.mortgage.workflow.service.ProcessDiagnosticsService.ActiveElement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the active BPMN elements of a process instance to a demo stage (pipeline step), a human
 * readable label and the persona that should act next. Human tasks win over system/wait states.
 */
final class DemoStageResolver {

	static final String SYSTEM = "SYSTEM";

	record Stage(String stageKey, String label, String suggestedRole) {
	}

	private static final Map<String, Stage> HUMAN_STAGES = new LinkedHashMap<>();
	private static final Map<String, Stage> WAIT_STAGES = new LinkedHashMap<>();
	private static final Map<String, Stage> SYSTEM_STAGES = new LinkedHashMap<>();

	static {
		// Human tasks (highest priority)
		HUMAN_STAGES.put("Task_complianceReview",
				new Stage("PRE_SCREENING", "Compliance exception review (24h SLA)", DemoRole.COMPLIANCE.name()));
		HUMAN_STAGES.put("Task_manualDocumentReview",
				new Stage("DOCUMENTATION", "Manual document review (ECM fallback)", DemoRole.OPERATIONS.name()));
		HUMAN_STAGES.put("Task_decideDocumentExpiry",
				new Stage("DOCUMENTATION", "Documents overdue — advisor must extend or cancel", DemoRole.ADVISOR.name()));
		HUMAN_STAGES.put("Task_followUpAppraisal",
				new Stage("APPRAISAL", "Appraisal overdue — follow up with appraiser", DemoRole.OPERATIONS.name()));
		HUMAN_STAGES.put("Task_manualUnderwritingReview",
				new Stage("UNDERWRITING", "Manual underwriting review (48h SLA)", DemoRole.UNDERWRITER.name()));
		HUMAN_STAGES.put("Task_seniorUnderwriterReview",
				new Stage("UNDERWRITING", "Senior underwriter review (reassigned after 5 days)", DemoRole.UNDERWRITER.name()));
		HUMAN_STAGES.put("Task_fixCommitmentLetter",
				new Stage("COMMITMENT", "Resolve commitment letter generation issue", DemoRole.OPERATIONS.name()));
		HUMAN_STAGES.put("Task_decideCommitmentExpiry",
				new Stage("COMMITMENT", "Commitment expired — advisor must extend or withdraw", DemoRole.ADVISOR.name()));
		HUMAN_STAGES.put("Task_resolveClosingDelay",
				new Stage("CLOSING", "Closing overdue — resolve with solicitor", DemoRole.OPERATIONS.name()));
		HUMAN_STAGES.put("Task_clearFundingConditions",
				new Stage("FUNDING", "Clear outstanding funding conditions", DemoRole.OPERATIONS.name()));
		HUMAN_STAGES.put("Task_manualFundingIntervention",
				new Stage("FUNDING", "Funding failed — manual intervention", DemoRole.OPERATIONS.name()));

		// External wait states (messages / event-based gateways)
		WAIT_STAGES.put("Event_waitEdoc",
				new Stage("DOCUMENTATION", "Waiting for income & financial eDocs (NOA / T4 / employment letter)", DemoRole.ADVISOR.name()));
		WAIT_STAGES.put("Gateway_awaitAppraisal",
				new Stage("APPRAISAL", "Waiting for property appraisal report", DemoRole.ECM_SYSTEM.name()));
		WAIT_STAGES.put("Gateway_awaitEsign",
				new Stage("COMMITMENT", "Waiting for client eSign of commitment letter (14-day expiry)", DemoRole.CUSTOMER.name()));
		WAIT_STAGES.put("Gateway_awaitClosing",
				new Stage("CLOSING", "Waiting for closing day confirmation from solicitor / notary", DemoRole.OPERATIONS.name()));

		// System activities
		SYSTEM_STAGES.put("Task_createMortgageFile", new Stage("SUBMITTED", "Creating mortgage file & validating input", SYSTEM));
		SYSTEM_STAGES.put("Task_notifyAdvisorInvalidApplication", new Stage("SUBMITTED", "Returning invalid application to advisor", SYSTEM));
		SYSTEM_STAGES.put("Task_creditBureauCheck", new Stage("PRE_SCREENING", "Credit bureau check", SYSTEM));
		SYSTEM_STAGES.put("Task_amlKycScreening", new Stage("PRE_SCREENING", "AML / KYC screening", SYSTEM));
		SYSTEM_STAGES.put("Task_recordCreditTimeout", new Stage("PRE_SCREENING", "Credit bureau timed out", SYSTEM));
		SYSTEM_STAGES.put("Task_recordCreditHardDecline", new Stage("PRE_SCREENING", "Recording bureau hard decline", SYSTEM));
		SYSTEM_STAGES.put("Task_recordAmlTimeout", new Stage("PRE_SCREENING", "AML screening timed out", SYSTEM));
		SYSTEM_STAGES.put("Task_preScreeningEligibility", new Stage("PRE_SCREENING", "Pre-screening eligibility decision (DMN)", SYSTEM));
		SYSTEM_STAGES.put("Task_escalateComplianceReview", new Stage("PRE_SCREENING", "Escalating compliance review (SLA breached)", SYSTEM));
		SYSTEM_STAGES.put("Task_validateDocumentViaEcm", new Stage("DOCUMENTATION", "Validating document via ECM (OCR)", SYSTEM));
		SYSTEM_STAGES.put("Task_updateDocumentChecklist", new Stage("DOCUMENTATION", "Updating document checklist", SYSTEM));
		SYSTEM_STAGES.put("Task_notifyAdvisorDocumentIssue", new Stage("DOCUMENTATION", "Document rejected — notifying advisor", SYSTEM));
		SYSTEM_STAGES.put("Task_sendDocumentReminder", new Stage("DOCUMENTATION", "Sending document reminder", SYSTEM));
		SYSTEM_STAGES.put("Task_requestPropertyAppraisal", new Stage("APPRAISAL", "Requesting property appraisal", SYSTEM));
		SYSTEM_STAGES.put("Task_calculateLoanToValue", new Stage("APPRAISAL", "Calculating loan-to-value", SYSTEM));
		SYSTEM_STAGES.put("Task_calculateDebtServiceRatios", new Stage("UNDERWRITING", "Calculating GDS / TDS (stress-tested)", SYSTEM));
		SYSTEM_STAGES.put("Task_underwritingDecision", new Stage("UNDERWRITING", "Debt service underwriting decision (DMN)", SYSTEM));
		SYSTEM_STAGES.put("Task_escalateUnderwriting", new Stage("UNDERWRITING", "Escalating underwriting review (SLA breached)", SYSTEM));
		SYSTEM_STAGES.put("Task_recordAutoApproval", new Stage("UNDERWRITING", "Recording auto-approval", SYSTEM));
		SYSTEM_STAGES.put("Task_recordManualApproval", new Stage("UNDERWRITING", "Recording manual approval", SYSTEM));
		SYSTEM_STAGES.put("Task_recordDecline", new Stage("UNDERWRITING", "Recording decline", SYSTEM));
		SYSTEM_STAGES.put("Task_sendAdverseActionNotice", new Stage("UNDERWRITING", "Sending adverse action notice", SYSTEM));
		SYSTEM_STAGES.put("Task_generateCommitmentLetter", new Stage("COMMITMENT", "Generating commitment letter", SYSTEM));
		SYSTEM_STAGES.put("Task_sendCommitmentForEsign", new Stage("COMMITMENT", "Sending commitment letter for eSign", SYSTEM));
		SYSTEM_STAGES.put("Task_sendEsignReminder", new Stage("COMMITMENT", "Sending eSign reminder", SYSTEM));
		SYSTEM_STAGES.put("Task_recordClientDeclined", new Stage("COMMITMENT", "Recording client decline", SYSTEM));
		SYSTEM_STAGES.put("Task_sendSolicitorInstructions", new Stage("CLOSING", "Sending instructions to solicitor / notary", SYSTEM));
		SYSTEM_STAGES.put("Task_prepareFundingChecklist", new Stage("FUNDING", "Preparing funding checklist", SYSTEM));
		SYSTEM_STAGES.put("Task_fundingReadiness", new Stage("FUNDING", "Funding readiness decision (DMN)", SYSTEM));
		SYSTEM_STAGES.put("Task_disburseMortgage", new Stage("FUNDING", "Core banking disbursement", SYSTEM));
		SYSTEM_STAGES.put("Task_reverseFundingAttempt", new Stage("FUNDING", "Reversing failed funding attempt", SYSTEM));
		SYSTEM_STAGES.put("Task_notifyAdvisorFundingFailed", new Stage("FUNDING", "Notifying advisor of funding failure", SYSTEM));
		SYSTEM_STAGES.put("Task_activateMortgageAccount", new Stage("FUNDING", "Activating mortgage account", SYSTEM));
		SYSTEM_STAGES.put("Task_notifyFundedActive", new Stage("FUNDING", "Notifying mortgage funded & active", SYSTEM));
		SYSTEM_STAGES.put("Task_cancelApplication", new Stage("CANCELLED", "Cancelling application", SYSTEM));
		SYSTEM_STAGES.put("Task_cancelWithdrawn", new Stage("CANCELLED", "Processing client withdrawal", SYSTEM));
	}

	private DemoStageResolver() {
	}

	static Stage resolve(List<ActiveElement> activeElements) {
		for (ActiveElement element : activeElements) {
			Stage stage = HUMAN_STAGES.get(element.elementId());
			if (stage != null) {
				return stage;
			}
		}
		Stage docsWait = null;
		Stage appraisalWait = null;
		for (ActiveElement element : activeElements) {
			Stage stage = WAIT_STAGES.get(element.elementId());
			if (stage == null) {
				continue;
			}
			if ("Event_waitEdoc".equals(element.elementId())) {
				docsWait = stage;
			} else if ("Gateway_awaitAppraisal".equals(element.elementId())) {
				appraisalWait = stage;
			} else {
				return stage;
			}
		}
		if (docsWait != null && appraisalWait != null) {
			return new Stage("DOCUMENTATION",
					"Waiting for financial eDocs and property appraisal (parallel)", DemoRole.ADVISOR.name());
		}
		if (docsWait != null) {
			return docsWait;
		}
		if (appraisalWait != null) {
			return appraisalWait;
		}
		for (ActiveElement element : activeElements) {
			Stage stage = SYSTEM_STAGES.get(element.elementId());
			if (stage != null) {
				return stage;
			}
		}
		return null;
	}

	static Stage terminal(Object applicationStatus) {
		String status = applicationStatus != null ? applicationStatus.toString().toUpperCase() : "";
		return switch (status) {
			case "ACTIVE" -> new Stage("FUNDED", "Mortgage funded & active", DemoRole.OPERATIONS.name());
			case "DECLINED" -> new Stage("DECLINED", "Declined — adverse action notice sent", DemoRole.ADVISOR.name());
			case "CLIENT_DECLINED" -> new Stage("DECLINED", "Client declined the commitment letter", DemoRole.ADVISOR.name());
			case "RETURNED_TO_ADVISOR" -> new Stage("DECLINED", "Application invalid — returned to advisor", DemoRole.ADVISOR.name());
			case "FUNDING_FAILED" -> new Stage("DECLINED", "Funding failed — closed after manual intervention", DemoRole.OPERATIONS.name());
			case "WITHDRAWN" -> new Stage("CANCELLED", "Withdrawn by client", DemoRole.ADVISOR.name());
			case "CANCELLED" -> new Stage("CANCELLED", "Cancelled", DemoRole.ADVISOR.name());
			default -> new Stage("FUNDED", "Completed (" + status + ")", DemoRole.OPERATIONS.name());
		};
	}

}
