package com.application.workflow.worker;

import com.application.workflow.worker.support.DocumentChecklists;
import org.apache.commons.lang3.StringUtils;
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
public class RecordDocumentVerifiedWorker {

	private static final Logger log = LoggerFactory.getLogger(RecordDocumentVerifiedWorker.class);

	@JobWorker(type = "recordDocumentVerified")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String documentType,
			@Variable String documentId,
			@Variable List<String> pendingDocuments,
			@Variable List<String> verifiedDocuments) {
		String type = DocumentChecklists.resolveDocumentType(documentType, pendingDocuments);
		if (StringUtils.isBlank(type)) {
			throw new IllegalStateException("No documentType and no pending documents for applicationId=" + applicationId);
		}
		List<String> pending = DocumentChecklists.copyPending(pendingDocuments);
		List<String> verified = DocumentChecklists.copyVerified(verifiedDocuments);
		if (!pending.remove(type)) {
			log.warn("Document type {} was not in pending list applicationId={} pending={}", type, applicationId, pending);
		}
		if (!verified.contains(type)) {
			verified.add(type);
		}
		log.info(
				"Recorded verified document applicationId={} type={} documentId={} pending={} verified={}",
				applicationId,
				type,
				documentId,
				pending,
				verified);
		Map<String, Object> variables = new HashMap<>();
		variables.put("documentType", type);
		variables.put("pendingDocuments", pending);
		variables.put("verifiedDocuments", verified);
		return variables;
	}

}
