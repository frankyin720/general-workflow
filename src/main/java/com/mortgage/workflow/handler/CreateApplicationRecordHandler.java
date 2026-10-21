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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreateApplicationRecordHandler {

	private static final Logger log = LoggerFactory.getLogger(CreateApplicationRecordHandler.class);

	@JobWorker(type = "createApplicationRecord")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String applicationType,
			@Variable(optional = true) String integrationScenario) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.CREATE_RECORD_FAILED)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.CREATE_RECORD_FAILED,
					"Core banking rejected application creation",
					Map.of("integrationFailureReason", "Simulated create-record failure"));
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Core banking unavailable during create record",
					Map.of("integrationPoint", "createApplicationRecord"));
		}
		Map<String, Object> variables = new HashMap<>();
		if (applicationId != null && !applicationId.isBlank()) {
			log.info("Creating application record applicationId={} applicationType={}", applicationId, applicationType);
			variables.put("applicationStatus", "SUBMITTED");
		} else {
			String generatedId = UUID.randomUUID().toString();
			log.info("Creating application record applicationId={} applicationType={}", generatedId, applicationType);
			variables.put("applicationId", generatedId);
			variables.put("applicationStatus", "SUBMITTED");
		}
		variables.putAll(ProcessProgress.append(null, ProcessProgress.STEP_CREATE_RECORD));
		return variables;
	}

}
