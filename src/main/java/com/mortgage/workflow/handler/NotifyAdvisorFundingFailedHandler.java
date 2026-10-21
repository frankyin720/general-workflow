package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorFundingFailedHandler {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorFundingFailedHandler.class);

	@JobWorker(type = "notifyAdvisorFundingFailed")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String fundingFailureReason) {
		log.info(
				"Notifying advisor of funding failure applicationId={} reason={}",
				applicationId,
				fundingFailureReason);
		return Map.of(
				"applicationStatus", "FUNDING_FAILED",
				"advisorNotifiedFundingFailed", true);
	}

}
