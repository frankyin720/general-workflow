package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorDocumentIssueHandler {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorDocumentIssueHandler.class);

	@JobWorker(type = "notifyAdvisorDocumentIssue")
	public void handle(@Variable(optional = true) String applicationId) {
		log.info("Notifying advisor of document issue applicationId={}", applicationId);
		// TODO: push notification to Advisor Portal (REST)
	}

}
