package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.IntegrationErrorCodes;
import com.mortgage.workflow.handler.support.IntegrationJobFailures;
import com.mortgage.workflow.handler.support.IntegrationScenario;
import com.mortgage.workflow.handler.support.ProcessProgress;
import com.mortgage.workflow.handler.support.Variables;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 5 — funding, registration and cancellation workers. */
@Component
public class FundingHandlers {

	private static final Logger log = LoggerFactory.getLogger(FundingHandlers.class);

	/**
	 * Normalises funding preconditions so the funding-readiness DMN never sees nulls.
	 * Approval conditions may arrive as a list ({@code approvalConditions}) or as free text from the
	 * underwriting form ({@code approvalConditionsText}, one condition per line).
	 */
	@JobWorker(type = "prepareFundingChecklist")
	public Map<String, Object> prepareFundingChecklist(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Boolean closingConfirmed,
			@Variable(optional = true) String closingStatus,
			@Variable(optional = true) Boolean solicitorInstructionsSent,
			@Variable(optional = true) Boolean insuranceRequired,
			@Variable(optional = true) Boolean insuranceConfirmed,
			@Variable(optional = true) Object approvalConditions,
			@Variable(optional = true) String approvalConditionsText,
			@Variable(optional = true) Integer outstandingConditions,
			@Variable(optional = true) Boolean fundingHold,
			@Variable(optional = true) String completedSteps) {
		boolean closing = Variables.or(closingConfirmed, false) || "CONFIRMED".equalsIgnoreCase(closingStatus);
		int conditions;
		if (outstandingConditions != null) {
			conditions = outstandingConditions;
		} else if (approvalConditions != null) {
			conditions = countConditions(approvalConditions);
		} else {
			conditions = countConditions(approvalConditionsText);
		}
		Map<String, Object> variables = new HashMap<>();
		variables.put("closingConfirmed", closing);
		variables.put("solicitorInstructionsSent", Variables.or(solicitorInstructionsSent, false));
		variables.put("insuranceRequired", Variables.or(insuranceRequired, false));
		variables.put("insuranceConfirmed", Variables.or(insuranceConfirmed, false));
		variables.put("outstandingConditions", conditions);
		variables.put("fundingHold", Variables.or(fundingHold, false));
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_FUNDING_CHECKLIST));
		log.info("Funding checklist applicationId={} {}", applicationId, variables);
		return variables;
	}

	/** Accepts a list of conditions, a single condition or free text (one condition per line). */
	static int countConditions(Object conditions) {
		if (conditions instanceof List<?> list) {
			return (int) list.stream().filter(item -> item != null && !item.toString().isBlank()).count();
		}
		if (conditions instanceof String text) {
			return (int) text.lines().filter(line -> !line.isBlank()).count();
		}
		return 0;
	}

	@JobWorker(type = "coreBankingMortgageDisbursement")
	public Map<String, Object> disburseMortgage(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Double loanAmount,
			@Variable(optional = true) Integer disbursementAttempt,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String completedSteps) {
		int attempt = Variables.or(disbursementAttempt, 0) + 1;
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Core banking payment gateway unavailable",
					Map.of("integrationPoint", "coreBankingMortgageDisbursement"));
		}
		// Simulated: the first disbursement attempt fails; a manual retry succeeds.
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.FUNDING_FAILED) && attempt == 1) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.FUNDING_FAILED,
					"Core banking rejected disbursement: trust account mismatch",
					Map.of("disbursementAttempt", attempt, "fundingFailureReason", "Trust account mismatch"));
		}
		log.info("Mortgage disbursed applicationId={} amount={} attempt={}", applicationId, loanAmount, attempt);
		Map<String, Object> variables = new HashMap<>();
		variables.put("disbursementAttempt", attempt);
		variables.put("disbursementReference", "DSB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		variables.put("disbursedAt", Instant.now().toString());
		variables.put("disbursementComplete", true);
		variables.put("applicationStatus", "FUNDING");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_FUNDING_INITIATED));
		return variables;
	}

	/** Compensation after a failed disbursement: reverse partial transfers and release holds. */
	@JobWorker(type = "reverseFundingAttempt")
	public Map<String, Object> reverseFundingAttempt(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String fundingFailureReason) {
		log.warn("Reversing funding attempt applicationId={} reason={}", applicationId, fundingFailureReason);
		return Map.of(
				"fundingReversalReference", "REV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
				"applicationStatus", "FUNDING_ON_HOLD");
	}

	@JobWorker(type = "activateMortgageAccount")
	public Map<String, Object> activateMortgageAccount(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String completedSteps) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Core banking account service unavailable", Map.of("integrationPoint", "activateMortgageAccount"));
		}
		log.info("Mortgage account activated applicationId={}", applicationId);
		Map<String, Object> variables = new HashMap<>();
		variables.put("mortgageAccountNumber", "MTG-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());
		variables.put("mortgageActive", true);
		variables.put("applicationStatus", "ACTIVE");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_MORTGAGE_ACTIVE));
		return variables;
	}

	/** Cancels the application (expiry, follow-up cancel, closing cancel, client withdrawal). */
	@JobWorker(type = "cancelApplication")
	public Map<String, Object> cancelApplication(
			@CustomHeaders Map<String, String> headers,
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String cancelReason,
			@Variable(optional = true) String documentExpiryDecision,
			@Variable(optional = true) String appraisalFollowUpDecision,
			@Variable(optional = true) String commitmentExpiryDecision,
			@Variable(optional = true) String closingDelayDecision) {
		String reason = headers.get("cancelReason");
		if (reason == null) {
			reason = cancelReason;
		}
		if (reason == null) {
			if ("CANCEL".equalsIgnoreCase(documentExpiryDecision)) {
				reason = "DOCUMENTS_EXPIRED";
			} else if ("CANCEL".equalsIgnoreCase(appraisalFollowUpDecision)) {
				reason = "APPRAISAL_NOT_RECEIVED";
			} else if ("WITHDRAW".equalsIgnoreCase(commitmentExpiryDecision)) {
				reason = "COMMITMENT_EXPIRED";
			} else if ("CANCEL".equalsIgnoreCase(closingDelayDecision)) {
				reason = "CLOSING_CANCELLED";
			} else {
				reason = "CANCELLED";
			}
		}
		String status = headers.getOrDefault("targetStatus", "CANCELLED");
		log.info("Application cancelled applicationId={} status={} reason={}", applicationId, status, reason);
		return Map.of(
				"applicationStatus", status,
				"cancelReason", reason,
				"cancelledAt", Instant.now().toString());
	}

}
