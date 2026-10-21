package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.DocumentChecklists;
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
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 1 — application initiation and pre-screening workers. */
@Component
public class PreScreeningHandlers {

	private static final Logger log = LoggerFactory.getLogger(PreScreeningHandlers.class);

	public static final String CREDIT_OUTCOME_COMPLETED = "COMPLETED";
	public static final String AML_OUTCOME_CLEAR = "CLEAR";
	public static final String AML_OUTCOME_HIT = "HIT";

	/**
	 * Creates the mortgage file in core banking, validates mandatory input and derives the
	 * required document checklist. Financial figures default to demo values when not supplied.
	 */
	@JobWorker(type = "createMortgageFileAndValidateInput")
	public Map<String, Object> createMortgageFile(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String applicationType,
			@Variable(optional = true) String customerName,
			@Variable(optional = true) Double loanAmount,
			@Variable(optional = true) Double purchasePrice,
			@Variable(optional = true) Double annualIncome,
			@Variable(optional = true) Double monthlyDebtPayments,
			@Variable(optional = true) Double propertyTaxesAnnual,
			@Variable(optional = true) Double heatingMonthly,
			@Variable(optional = true) Double interestRate,
			@Variable(optional = true) Integer amortizationYears,
			@Variable(optional = true) String integrationScenario) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.VALIDATION_FAILED)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.VALIDATION_FAILED,
					"Mandatory applicant data missing",
					Map.of("validationErrors", "Simulated: applicant SIN and income missing"));
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Core banking unavailable while creating mortgage file",
					Map.of("integrationPoint", "createMortgageFileAndValidateInput"));
		}
		String resolvedType = Variables.or(applicationType, "Mortgage");
		double resolvedLoan = Variables.or(loanAmount, 520_000d);
		double resolvedPrice = Variables.or(purchasePrice, 650_000d);
		if (resolvedLoan <= 0 || resolvedPrice <= 0 || resolvedLoan > resolvedPrice) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.VALIDATION_FAILED,
					"Loan amount must be positive and not exceed purchase price",
					Map.of("validationErrors", "loanAmount=" + resolvedLoan + " purchasePrice=" + resolvedPrice));
		}

		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationId", Variables.or(applicationId, UUID.randomUUID().toString()));
		variables.put("applicationType", resolvedType);
		variables.put("customerName", Variables.or(customerName, "Demo Applicant"));
		variables.put("mortgageFileNumber", "MF-" + Instant.now().toEpochMilli());
		variables.put("applicationStatus", "SUBMITTED");
		variables.put("loanAmount", resolvedLoan);
		variables.put("purchasePrice", resolvedPrice);
		variables.put("annualIncome", Variables.or(annualIncome, 145_000d));
		variables.put("monthlyDebtPayments", Variables.or(monthlyDebtPayments, 600d));
		variables.put("propertyTaxesAnnual", Variables.or(propertyTaxesAnnual, 4_800d));
		variables.put("heatingMonthly", Variables.or(heatingMonthly, 150d));
		variables.put("interestRate", Variables.or(interestRate, 5.49d));
		variables.put("amortizationYears", Variables.or(amortizationYears, 25));
		variables.put("requiredDocuments", DocumentChecklists.forApplicationType(resolvedType));
		variables.put("verifiedDocumentTypes", java.util.List.of());
		variables.put("financialDocumentsVerified", false);
		variables.putAll(ProcessProgress.append(null, ProcessProgress.STEP_MORTGAGE_FILE_CREATED));
		log.info("Mortgage file created applicationId={} type={} loan={} price={}",
				variables.get("applicationId"), resolvedType, resolvedLoan, resolvedPrice);
		return variables;
	}

	/** Pulls the bureau score (Equifax / TransUnion). Hard declines raise a BPMN error. */
	@JobWorker(type = "creditBureauCheck")
	public Map<String, Object> creditBureauCheck(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) Integer demoCreditScore,
			@Variable(optional = true) String completedSteps) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Credit bureau gateway unavailable", Map.of("integrationPoint", "creditBureauCheck"));
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.CREDIT_HARD_DECLINE)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.CREDIT_HARD_DECLINE,
					"Bureau reported undischarged bankruptcy",
					Map.of("creditDeclineReason", "Undischarged bankruptcy on file"));
		}
		int creditScore = Variables.or(demoCreditScore, 720);
		log.info("Credit bureau check applicationId={} score={}", applicationId, creditScore);
		Map<String, Object> variables = new HashMap<>();
		variables.put("creditScore", creditScore);
		variables.put("creditCheckOutcome", CREDIT_OUTCOME_COMPLETED);
		variables.put("creditBureau", "Equifax");
		variables.put("creditReportId", "CR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_CREDIT_BUREAU));
		return variables;
	}

	/** AML / KYC screening (sanctions, PEP, adverse media). A hit is a business outcome, not an error. */
	@JobWorker(type = "amlKycScreening")
	public Map<String, Object> amlKycScreening(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String completedSteps) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"AML screening service unavailable", Map.of("integrationPoint", "amlKycScreening"));
		}
		boolean hit = IntegrationScenario.is(integrationScenario, IntegrationScenario.AML_HIT);
		log.info("AML/KYC screening applicationId={} hit={}", applicationId, hit);
		Map<String, Object> variables = new HashMap<>();
		variables.put("amlOutcome", hit ? AML_OUTCOME_HIT : AML_OUTCOME_CLEAR);
		variables.put("amlClear", !hit);
		variables.put("amlRiskLevel", hit ? "HIGH" : "LOW");
		variables.put("kycVerified", true);
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_AML_KYC));
		return variables;
	}

	/**
	 * Records a screening exception (timeout or hard decline) so the eligibility DMN can route the
	 * application to manual review or decline. Driven by task headers {@code screening} and {@code outcome}.
	 */
	@JobWorker(type = "recordScreeningException")
	public Map<String, Object> recordScreeningException(
			@CustomHeaders Map<String, String> headers,
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String creditDeclineReason) {
		String screening = headers.getOrDefault("screening", "CREDIT");
		String outcome = headers.getOrDefault("outcome", "TIMEOUT");
		log.warn("Screening exception applicationId={} screening={} outcome={}", applicationId, screening, outcome);
		Map<String, Object> variables = new HashMap<>();
		if ("AML".equalsIgnoreCase(screening)) {
			variables.put("amlOutcome", outcome);
			variables.put("amlClear", false);
		} else {
			variables.put("creditCheckOutcome", outcome);
			variables.put("creditExceptionReason",
					Variables.or(creditDeclineReason, "Credit bureau " + outcome.toLowerCase()));
		}
		return variables;
	}

}
