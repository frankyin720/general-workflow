package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.IntegrationErrorCodes;
import com.mortgage.workflow.handler.support.IntegrationJobFailures;
import com.mortgage.workflow.handler.support.IntegrationScenario;
import com.mortgage.workflow.handler.support.ProcessProgress;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CoreBankingFundingOrchestrationHandler {

	private static final Logger log = LoggerFactory.getLogger(CoreBankingFundingOrchestrationHandler.class);

	@JobWorker(type = "coreBankingFundingOrchestration")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String completedSteps) {
		log.info("Orchestrating core banking funding applicationId={}", applicationId);
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Core banking funding gateway unavailable",
					Map.of("integrationPoint", "coreBankingFundingOrchestration"));
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.FUNDING_FAILED)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.FUNDING_FAILED,
					"Core banking rejected funding",
					Map.of("fundingFailureReason", "Simulated funding failure"));
		}
		// TODO: invoke Core Banking funding API (JMS/REST)
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "FUNDING");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_FUNDING_INITIATED));
		return variables;
	}

}
