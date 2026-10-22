package com.application.workflow.worker;

import java.util.Objects;
import com.application.workflow.worker.support.IntegrationErrorCodes;
import com.application.workflow.worker.support.IntegrationJobFailures;
import com.application.workflow.worker.support.IntegrationScenario;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import com.application.workflow.worker.support.DocumentChecklists;
import org.apache.commons.lang3.StringUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidateDocumentViaEcmWorker {

	private static final Logger log = LoggerFactory.getLogger(ValidateDocumentViaEcmWorker.class);

	@JobWorker(type = "validateDocumentViaEcm")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String integrationScenario,
			@Variable String documentStatus,
			@Variable String documentType,
			@Variable List<String> pendingDocuments) {
		String type = DocumentChecklists.resolveDocumentType(documentType, pendingDocuments);
		log.info("Validating document via ECM applicationId={} documentType={}", applicationId, type);
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.ECM_UNAVAILABLE)
				|| IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			Map<String, Object> integrationVars = new HashMap<>();
			integrationVars.put("integrationPoint", "validateDocumentViaEcm");
			integrationVars.put("applicationId", applicationId);
			throw IntegrationJobFailures.technicalFailure("ECM service unavailable", integrationVars);
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.ECM_HARD_FAILURE)) {
			Map<String, Object> failureVars = new HashMap<>();
			failureVars.put("integrationFailureReason", "Simulated ECM hard failure");
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.ECM_SERVICE_UNAVAILABLE,
					"ECM validation cannot proceed",
					failureVars);
		}
		boolean statusOk = StringUtils.isBlank(documentStatus)
				|| !documentStatus.equalsIgnoreCase("INVALID");
		boolean typePending = StringUtils.isNotBlank(type)
				&& Objects.nonNull(pendingDocuments)
				&& pendingDocuments.contains(type);
		boolean documentValid = statusOk && typePending;
		// TODO: call Alfresco/OnBase REST API (OCR, virus scan, format checks)
		Map<String, Object> variables = new HashMap<>();
		variables.put("documentValid", documentValid);
		if (StringUtils.isNotBlank(type)) {
			variables.put("documentType", type);
		}
		return variables;
	}

}
