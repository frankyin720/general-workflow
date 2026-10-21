package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EscalateUnderwritingReviewHandler {

	private static final Logger log = LoggerFactory.getLogger(EscalateUnderwritingReviewHandler.class);

	@JobWorker(type = "escalateUnderwritingReview")
	public void handle(@Variable(optional = true) String applicationId) {
		log.info("Escalating underwriting review (48h SLA) applicationId={}", applicationId);
		// TODO: notify underwriting manager / escalation channel (REST)
	}

}
