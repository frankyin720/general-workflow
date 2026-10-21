package com.mortgage.workflow.handler;

import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbound notifications and escalations (advisor portal, client messaging, team leads).
 * All are idempotent and side-effect only; they record what was sent as process variables.
 */
@Component
public class NotificationHandlers {

	private static final Logger log = LoggerFactory.getLogger(NotificationHandlers.class);

	@JobWorker(type = "notifyAdvisorInvalidApplication")
	public Map<String, Object> notifyAdvisorInvalidApplication(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String validationErrors) {
		log.warn("Application returned to advisor applicationId={} errors={}", applicationId, validationErrors);
		return Map.of("applicationStatus", "RETURNED_TO_ADVISOR", "lastNotification", "INVALID_APPLICATION");
	}

	/** SLA breach escalation for compliance / underwriting reviews. Header: reviewType. */
	@JobWorker(type = "escalateReview")
	public Map<String, Object> escalateReview(
			@CustomHeaders Map<String, String> headers,
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Integer escalationCount) {
		String reviewType = headers.getOrDefault("reviewType", "REVIEW");
		int count = (escalationCount != null ? escalationCount : 0) + 1;
		log.warn("SLA escalation applicationId={} reviewType={} count={}", applicationId, reviewType, count);
		Map<String, Object> variables = new HashMap<>();
		variables.put("escalationCount", count);
		variables.put("lastEscalation", reviewType);
		variables.put("lastEscalatedAt", Instant.now().toString());
		return variables;
	}

	@JobWorker(type = "notifyAdvisorDocumentIssue")
	public Map<String, Object> notifyAdvisorDocumentIssue(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String lastValidatedDocumentId,
			@Variable(optional = true) String documentRejectionReason) {
		log.info("Document rejected applicationId={} documentId={} reason={}",
				applicationId, lastValidatedDocumentId, documentRejectionReason);
		return Map.of("lastNotification", "DOCUMENT_REJECTED");
	}

	@JobWorker(type = "sendDocumentReminder")
	public Map<String, Object> sendDocumentReminder(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Integer documentReminderCount) {
		int count = (documentReminderCount != null ? documentReminderCount : 0) + 1;
		log.info("Document reminder sent applicationId={} count={}", applicationId, count);
		return Map.of("documentReminderCount", count, "lastNotification", "DOCUMENT_REMINDER");
	}

	@JobWorker(type = "sendAdverseActionNotice")
	public Map<String, Object> sendAdverseActionNotice(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String declineReason) {
		log.info("Adverse action notice sent applicationId={} reason={}", applicationId, declineReason);
		return Map.of("adverseActionNoticeSentAt", Instant.now().toString(), "lastNotification", "ADVERSE_ACTION");
	}

	@JobWorker(type = "sendEsignReminder")
	public Map<String, Object> sendEsignReminder(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Integer esignReminderCount) {
		int count = (esignReminderCount != null ? esignReminderCount : 0) + 1;
		log.info("eSign reminder sent applicationId={} count={}", applicationId, count);
		return Map.of("esignReminderCount", count, "lastNotification", "ESIGN_REMINDER");
	}

	@JobWorker(type = "notifyAdvisorFundingFailed")
	public Map<String, Object> notifyAdvisorFundingFailed(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String fundingFailureReason) {
		log.warn("Funding failed notification applicationId={} reason={}", applicationId, fundingFailureReason);
		return Map.of("applicationStatus", "FUNDING_FAILED", "lastNotification", "FUNDING_FAILED");
	}

	@JobWorker(type = "notifyMortgageFundedActive")
	public Map<String, Object> notifyMortgageFundedActive(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String mortgageAccountNumber) {
		log.info("Mortgage funded & active notification applicationId={} account={}", applicationId, mortgageAccountNumber);
		return Map.of("fundedNotificationSentAt", Instant.now().toString(), "lastNotification", "MORTGAGE_ACTIVE");
	}

}
