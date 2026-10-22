package com.application.workflow.worker;

import com.application.workflow.worker.support.IntegrationErrorCodes;
import com.application.workflow.worker.support.IntegrationJobFailures;
import com.application.workflow.worker.support.IntegrationScenario;
import com.application.workflow.worker.support.ProcessProgress;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CoreBankingFundingOrchestrationWorker {

	private static final Logger log = LoggerFactory.getLogger(CoreBankingFundingOrchestrationWorker.class);

	@JobWorker(type = "coreBankingFundingOrchestration")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String integrationScenario,
			@Variable List<String> completedSteps) {
		log.info("Orchestrating core banking funding applicationId={}", applicationId);
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			Map<String, Object> integrationPoint = new HashMap<>();
			integrationPoint.put("integrationPoint", "coreBankingFundingOrchestration");
			throw IntegrationJobFailures.technicalFailure(
					"Core banking funding gateway unavailable",
					integrationPoint);
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.FUNDING_FAILED)) {
			Map<String, Object> failureVars = new HashMap<>();
			failureVars.put("fundingFailureReason", "Simulated funding failure");
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.FUNDING_FAILED,
					"Core banking rejected funding",
					failureVars);
		}
		// TODO: invoke Core Banking funding API (JMS/REST)
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "FUNDING");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_FUNDING_INITIATED));
		return variables;
	}

}
