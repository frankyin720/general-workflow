package com.application.workflow.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorFundingFailedWorker {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorFundingFailedWorker.class);

	@JobWorker(type = "notifyAdvisorFundingFailed")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String fundingFailureReason) {
		log.info(
				"Notifying advisor of funding failure applicationId={} reason={}",
				applicationId,
				fundingFailureReason);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "FUNDING_FAILED");
		variables.put("advisorNotifiedFundingFailed", true);
		return variables;
	}

}
