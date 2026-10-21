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
public class TriggerCreditCheckAndAmlHandler {

	private static final Logger log = LoggerFactory.getLogger(TriggerCreditCheckAndAmlHandler.class);

	@JobWorker(type = "triggerCreditCheckAndAml")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) Integer demoCreditScore) {
		log.info("Triggering credit check and AML screening applicationId={}", applicationId);
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Credit bureau unavailable",
					Map.of("integrationPoint", "triggerCreditCheckAndAml"));
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.CREDIT_HARD_DECLINE)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.CREDIT_HARD_DECLINE,
					"Credit bureau hard decline",
					Map.of("creditDeclineReason", "Simulated hard decline"));
		}
		int creditScore = demoCreditScore != null ? demoCreditScore : 720;
		// TODO: invoke credit bureau (gRPC) and AML service — demo portal sets demoCreditScore
		return Map.of(
				"creditScore", creditScore,
				"amlClear", true);
	}

}
