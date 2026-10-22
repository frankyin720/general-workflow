package com.application.workflow.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorFundedWorker {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorFundedWorker.class);

	@JobWorker(type = "notifyAdvisorFunded")
	public Map<String, Object> handle(@Variable String applicationId) {
		log.info("Notifying advisor application funded applicationId={}", applicationId);
		// TODO: push FUNDED status to Advisor Portal (REST)
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "FUNDED");
		return variables;
	}

}
