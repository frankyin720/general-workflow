package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorFundedHandler {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorFundedHandler.class);

	@JobWorker(type = "notifyAdvisorFunded")
	public Map<String, Object> handle(@Variable(optional = true) String applicationId) {
		log.info("Notifying advisor application funded applicationId={}", applicationId);
		// TODO: push FUNDED status to Advisor Portal (REST)
		return Map.of("applicationStatus", "FUNDED");
	}

}
