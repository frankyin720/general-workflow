package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorDocumentTimeoutHandler {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorDocumentTimeoutHandler.class);

	@JobWorker(type = "notifyAdvisorDocumentTimeout")
	public void handle(@Variable(optional = true) String applicationId) {
		log.info("Notifying advisor of document upload timeout applicationId={}", applicationId);
		// TODO: push timeout reminder to Advisor Portal (REST)
	}

}
