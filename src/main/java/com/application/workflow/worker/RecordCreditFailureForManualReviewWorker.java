package com.application.workflow.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RecordCreditFailureForManualReviewWorker {

	private static final Logger log = LoggerFactory.getLogger(RecordCreditFailureForManualReviewWorker.class);

	@JobWorker(type = "recordCreditFailureForManualReview")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String creditReferralReason,
			@Variable String creditDeclineReason) {
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
			if (StringUtils.isNotBlank(candidate)) {
				return candidate;
			}
		}
		return "";
	}

}
