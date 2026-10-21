package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.DocumentChecklists;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InitializeDocumentChecklistHandler {

	private static final Logger log = LoggerFactory.getLogger(InitializeDocumentChecklistHandler.class);

	@JobWorker(type = "initializeDocumentChecklist")
	public Map<String, Object> handle(@Variable(optional = true) String applicationType) {
		log.info("Initializing document checklist applicationType={}", applicationType);
		return Map.of(
				"applicationStatus", "IN_REVIEW",
				"requiredDocuments", DocumentChecklists.forApplicationType(applicationType));
	}

}
