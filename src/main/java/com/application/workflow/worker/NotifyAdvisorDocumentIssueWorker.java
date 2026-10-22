package com.application.workflow.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorDocumentIssueWorker {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorDocumentIssueWorker.class);

	@JobWorker(type = "notifyAdvisorDocumentIssue")
	public void handle(@Variable String applicationId) {
		log.info("Notifying advisor of document issue applicationId={}", applicationId);
		// TODO: push notification to Advisor Portal (REST)
	}

}
