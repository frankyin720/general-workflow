package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.IntegrationErrorCodes;
import com.mortgage.workflow.handler.support.IntegrationJobFailures;
import com.mortgage.workflow.handler.support.IntegrationScenario;
import com.mortgage.workflow.handler.support.MortgageMath;
import com.mortgage.workflow.handler.support.ProcessProgress;
import com.mortgage.workflow.handler.support.Variables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 2 — documentation (Branch A) and property appraisal (Branch B) workers. */
@Component
public class DocumentationHandlers {

	private static final Logger log = LoggerFactory.getLogger(DocumentationHandlers.class);

	/**
	 * OCR + compliance check of an uploaded document via ECM. Message variables: documentId,
	 * documentType (NOA / T4 / EMPLOYMENT_LETTER / ...), documentStatus (UPLOADED | INVALID).
	 */
	@JobWorker(type = "validateFinancialDocumentsViaEcm")
	public Map<String, Object> validateDocument(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String documentId,
			@Variable(optional = true) String documentType,
			@Variable(optional = true) String documentStatus,
			@Variable(optional = true) String integrationScenario) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.ECM_UNAVAILABLE)
				|| IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"ECM service unavailable",
					Map.of("integrationPoint", "validateFinancialDocumentsViaEcm"));
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.ECM_HARD_FAILURE)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.ECM_SERVICE_UNAVAILABLE,
					"ECM OCR pipeline rejected the document batch",
					Map.of("ecmFailureReason", "Simulated ECM hard failure"));
		}
		boolean valid = documentStatus == null || !documentStatus.equalsIgnoreCase("INVALID");
		log.info("ECM validation applicationId={} documentId={} type={} valid={}",
				applicationId, documentId, documentType, valid);
		Map<String, Object> variables = new HashMap<>();
		variables.put("documentValid", valid);
		variables.put("lastValidatedDocumentId", documentId);
		variables.put("lastValidatedDocumentType", documentType);
		variables.put("documentRejectionReason", valid ? null : "OCR could not read document or document expired");
		return variables;
	}

	/**
	 * Marks the validated document type as received and determines whether all required documents
	 * have been verified. Documents without a type (demo uploads) satisfy the whole checklist.
	 */
	@JobWorker(type = "updateDocumentChecklist")
	public Map<String, Object> updateDocumentChecklist(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) List<String> requiredDocuments,
			@Variable(optional = true) List<String> verifiedDocumentTypes,
			@Variable(optional = true) String lastValidatedDocumentType,
			@Variable(optional = true) String completedSteps) {
		List<String> required = requiredDocuments != null ? requiredDocuments : List.of();
		Set<String> verified = new LinkedHashSet<>(verifiedDocumentTypes != null ? verifiedDocumentTypes : List.of());
		boolean allVerified;
		if (lastValidatedDocumentType == null || lastValidatedDocumentType.isBlank()) {
			verified.addAll(required);
			allVerified = true;
		} else {
			verified.add(lastValidatedDocumentType.toUpperCase());
			allVerified = verified.containsAll(required);
		}
		List<String> outstanding = new ArrayList<>(required);
		outstanding.removeAll(verified);
		log.info("Document checklist applicationId={} verified={} outstanding={}", applicationId, verified, outstanding);
		Map<String, Object> variables = new HashMap<>();
		variables.put("verifiedDocumentTypes", new ArrayList<>(verified));
		variables.put("outstandingDocuments", outstanding);
		variables.put("financialDocumentsVerified", allVerified);
		if (allVerified) {
			variables.put("applicationStatus", "DOCUMENTS_VERIFIED");
			variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_FINANCIAL_DOCS_VERIFIED));
		}
		return variables;
	}

	/** Orders a property appraisal from the third-party appraisal management company. */
	@JobWorker(type = "requestPropertyAppraisal")
	public Map<String, Object> requestPropertyAppraisal(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String completedSteps) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Appraisal vendor API unavailable", Map.of("integrationPoint", "requestPropertyAppraisal"));
		}
		String requestId = "APR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		log.info("Appraisal requested applicationId={} appraisalRequestId={}", applicationId, requestId);
		Map<String, Object> variables = new HashMap<>();
		variables.put("appraisalRequestId", requestId);
		variables.put("appraisalRequestedAt", Instant.now().toString());
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_APPRAISAL_REQUESTED));
		return variables;
	}

	/** LTV = loan / min(appraised value, purchase price). Message variable: appraisedValue. */
	@JobWorker(type = "calculateLoanToValue")
	public Map<String, Object> calculateLoanToValue(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Double loanAmount,
			@Variable(optional = true) Double purchasePrice,
			@Variable(optional = true) Double appraisedValue,
			@Variable(optional = true) String completedSteps) {
		double loan = Variables.or(loanAmount, 520_000d);
		double price = Variables.or(purchasePrice, 650_000d);
		double appraised = Variables.or(appraisedValue, price);
		double lendingValue = Math.min(appraised, price);
		double ltv = MortgageMath.round1(loan / lendingValue * 100);
		log.info("LTV applicationId={} loan={} lendingValue={} ltv={}", applicationId, loan, lendingValue, ltv);
		Map<String, Object> variables = new HashMap<>();
		variables.put("appraisedValue", appraised);
		variables.put("lendingValue", lendingValue);
		variables.put("ltvRatio", ltv);
		variables.put("propertyAppraisalComplete", true);
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_LTV_CALCULATED));
		return variables;
	}

}
