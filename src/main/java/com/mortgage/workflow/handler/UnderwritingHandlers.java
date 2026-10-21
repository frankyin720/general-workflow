package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.MortgageMath;
import com.mortgage.workflow.handler.support.ProcessProgress;
import com.mortgage.workflow.handler.support.Variables;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 3 — debt service calculation and decision recording workers. */
@Component
public class UnderwritingHandlers {

	private static final Logger log = LoggerFactory.getLogger(UnderwritingHandlers.class);

	/**
	 * GDS = (P&amp;I + property taxes + heating) / income; TDS = GDS obligations + other debt payments.
	 * Uses the OSFI qualifying rate (greater of contract + 2% or 5.25%) for the stress test.
	 */
	@JobWorker(type = "calculateDebtServiceRatios")
	public Map<String, Object> calculateDebtServiceRatios(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Double loanAmount,
			@Variable(optional = true) Double annualIncome,
			@Variable(optional = true) Double monthlyDebtPayments,
			@Variable(optional = true) Double propertyTaxesAnnual,
			@Variable(optional = true) Double heatingMonthly,
			@Variable(optional = true) Double interestRate,
			@Variable(optional = true) Integer amortizationYears,
			@Variable(optional = true) String completedSteps) {
		double loan = Variables.or(loanAmount, 520_000d);
		double income = Variables.or(annualIncome, 145_000d);
		double debts = Variables.or(monthlyDebtPayments, 600d);
		double taxesMonthly = Variables.or(propertyTaxesAnnual, 4_800d) / 12;
		double heat = Variables.or(heatingMonthly, 150d);
		double contractRate = Variables.or(interestRate, 5.49d);
		int amortization = Variables.or(amortizationYears, 25);

		double qualifyingRate = Math.max(contractRate + 2.0, 5.25);
		double stressedPayment = MortgageMath.monthlyPayment(loan, qualifyingRate, amortization);
		double contractPayment = MortgageMath.monthlyPayment(loan, contractRate, amortization);
		double housingCosts = stressedPayment + taxesMonthly + heat;
		double gds = MortgageMath.ratioPercent(housingCosts, income);
		double tds = MortgageMath.ratioPercent(housingCosts + debts, income);
		log.info("Debt service applicationId={} qualifyingRate={} GDS={} TDS={}", applicationId, qualifyingRate, gds, tds);

		Map<String, Object> variables = new HashMap<>();
		variables.put("qualifyingRate", qualifyingRate);
		variables.put("stressedMonthlyPayment", MortgageMath.round1(stressedPayment));
		variables.put("contractMonthlyPayment", MortgageMath.round1(contractPayment));
		variables.put("gdsRatio", gds);
		variables.put("tdsRatio", tds);
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_DEBT_SERVICE_CALCULATED));
		return variables;
	}

	/** Writes approval status back to the system of record. Headers: targetStatus, approvalPath. */
	@JobWorker(type = "updateApplicationStatus")
	public Map<String, Object> updateApplicationStatus(
			@CustomHeaders Map<String, String> headers,
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Map<String, Object> underwriting,
			@Variable(optional = true) String completedSteps) {
		String targetStatus = headers.getOrDefault("targetStatus", "APPROVED");
		String approvalPath = headers.getOrDefault("approvalPath", "AUTO");
		log.info("Status update applicationId={} status={} path={}", applicationId, targetStatus, approvalPath);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", targetStatus);
		variables.put("approvalPath", approvalPath);
		if (underwriting != null && Boolean.TRUE.equals(underwriting.get("insuranceRequired"))) {
			variables.put("insuranceRequired", true);
		} else {
			variables.put("insuranceRequired", false);
		}
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_APPROVED));
		return variables;
	}

	/** Records a decline from pre-screening, DMN, compliance or an underwriter. */
	@JobWorker(type = "recordDeclineDecision")
	public Map<String, Object> recordDeclineDecision(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Map<String, Object> preScreening,
			@Variable(optional = true) Map<String, Object> underwriting,
			@Variable(optional = true) String complianceDecision,
			@Variable(optional = true) String underwritingDecision,
			@Variable(optional = true) String declineReason) {
		String reason = declineReason;
		String stage;
		if (reason == null && underwritingDecision != null) {
			stage = "MANUAL_UNDERWRITING";
			reason = "Declined by underwriter";
		} else if (reason == null && underwriting != null && "DECLINE".equals(underwriting.get("route"))) {
			stage = "DEBT_SERVICE_UNDERWRITING";
			reason = String.valueOf(underwriting.get("reason"));
		} else if (reason == null && "DECLINED".equalsIgnoreCase(complianceDecision)) {
			stage = "COMPLIANCE_REVIEW";
			reason = "Declined by compliance";
		} else if (reason == null && preScreening != null) {
			stage = "PRE_SCREENING";
			reason = String.valueOf(preScreening.get("reason"));
		} else {
			stage = underwritingDecision != null ? "MANUAL_UNDERWRITING" : "UNDERWRITING";
		}
		log.info("Decline recorded applicationId={} stage={} reason={}", applicationId, stage, reason);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "DECLINED");
		variables.put("declineStage", stage);
		variables.put("declineReason", Variables.or(reason, "Declined"));
		return variables;
	}

}
