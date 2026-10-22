package com.application.workflow.worker;

import java.util.Objects;
import com.application.workflow.worker.support.IntegrationErrorCodes;
import com.application.workflow.worker.support.IntegrationJobFailures;
import com.application.workflow.worker.support.IntegrationScenario;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TriggerCreditCheckAndAmlWorker {

	private static final Logger log = LoggerFactory.getLogger(TriggerCreditCheckAndAmlWorker.class);

	@JobWorker(type = "triggerCreditCheckAndAml")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String integrationScenario,
			@Variable Integer demoCreditScore) {
		log.info("Triggering credit check and AML screening applicationId={}", applicationId);
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			Map<String, Object> integrationPoint = new HashMap<>();
			integrationPoint.put("integrationPoint", "triggerCreditCheckAndAml");
			throw IntegrationJobFailures.technicalFailure("Credit bureau unavailable", integrationPoint);
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.CREDIT_HARD_DECLINE)) {
			Map<String, Object> declineVars = new HashMap<>();
			declineVars.put("creditDeclineReason", "Simulated hard decline");
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.CREDIT_HARD_DECLINE,
					"Credit bureau hard decline",
					declineVars);
		}
		int creditScore = Objects.nonNull(demoCreditScore) ? demoCreditScore : 720;
		// TODO: invoke credit bureau (gRPC) and AML service — demo portal sets demoCreditScore
		Map<String, Object> variables = new HashMap<>();
		variables.put("creditScore", creditScore);
		variables.put("amlClear", true);
		return variables;
	}

}
