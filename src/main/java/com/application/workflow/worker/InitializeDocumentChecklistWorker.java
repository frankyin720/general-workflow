package com.application.workflow.worker;

import com.application.workflow.worker.support.DocumentChecklists;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InitializeDocumentChecklistWorker {

	private static final Logger log = LoggerFactory.getLogger(InitializeDocumentChecklistWorker.class);

	@JobWorker(type = "initializeDocumentChecklist")
	public Map<String, Object> handle(
			@Variable String applicationType,
			@Variable String demoRequiredDocuments) {
		log.info(
				"Initializing document checklist applicationType={} demoRequiredDocuments={}",
				applicationType,
				demoRequiredDocuments);
		List<String> required = DocumentChecklists.resolveRequiredDocuments(applicationType, demoRequiredDocuments);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "IN_REVIEW");
		variables.put("requiredDocuments", DocumentChecklists.toCsv(required));
		variables.put("pendingDocuments", new ArrayList<>(required));
		variables.put("verifiedDocuments", new ArrayList<>());
		return variables;
	}

}
