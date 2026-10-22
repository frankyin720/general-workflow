package com.application.workflow.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotifyAdvisorDocumentTimeoutWorker {

	private static final Logger log = LoggerFactory.getLogger(NotifyAdvisorDocumentTimeoutWorker.class);

	@JobWorker(type = "notifyAdvisorDocumentTimeout")
	public void handle(@Variable String applicationId) {
		log.info("Notifying advisor of document upload timeout applicationId={}", applicationId);
		// TODO: push timeout reminder to Advisor Portal (REST)
	}

}
