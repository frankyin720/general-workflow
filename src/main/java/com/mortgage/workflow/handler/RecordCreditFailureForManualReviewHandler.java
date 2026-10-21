package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecordCreditFailureForManualReviewHandler {

	private static final Logger log = LoggerFactory.getLogger(RecordCreditFailureForManualReviewHandler.class);

	@JobWorker(type = "recordCreditFailureForManualReview")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String creditReferralReason,
			@Variable(optional = true) String creditDeclineReason) {
		String reason = firstNonBlank(creditReferralReason, creditDeclineReason,
				"Credit check requires manual underwriting review");
		log.info("Recording credit manual referral applicationId={} reason={}", applicationId, reason);
		Map<String, Object> variables = new HashMap<>();
		variables.put("creditScore", 580);
		variables.put("amlClear", false);
		variables.put("creditManualReferral", true);
		variables.put("creditReferralReason", reason);
		return variables;
	}

	private static String firstNonBlank(String... candidates) {
		for (String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate;
			}
		}
		return "";
	}

}
