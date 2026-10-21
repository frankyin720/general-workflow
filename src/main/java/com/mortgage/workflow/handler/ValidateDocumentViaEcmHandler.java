package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.IntegrationErrorCodes;
import com.mortgage.workflow.handler.support.IntegrationJobFailures;
import com.mortgage.workflow.handler.support.IntegrationScenario;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ValidateDocumentViaEcmHandler {

	private static final Logger log = LoggerFactory.getLogger(ValidateDocumentViaEcmHandler.class);

	@JobWorker(type = "validateDocumentViaEcm")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String documentStatus) {
		log.info("Validating documents via ECM applicationId={}", applicationId);
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.ECM_UNAVAILABLE)
				|| IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"ECM service unavailable",
					Map.of("integrationPoint", "validateDocumentViaEcm", "applicationId", applicationId));
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.ECM_HARD_FAILURE)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.ECM_SERVICE_UNAVAILABLE,
					"ECM validation cannot proceed",
					Map.of("integrationFailureReason", "Simulated ECM hard failure"));
		}
		boolean documentValid = documentStatus == null
				|| documentStatus.isBlank()
				|| !documentStatus.equalsIgnoreCase("INVALID");
		// TODO: call Alfresco/OnBase REST API (OCR, virus scan, format checks)
		return Map.of("documentValid", documentValid);
	}

}
