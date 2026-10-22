package com.application.workflow.worker;

import com.application.workflow.worker.support.IntegrationErrorCodes;
import com.application.workflow.worker.support.IntegrationJobFailures;
import com.application.workflow.worker.support.IntegrationScenario;
import com.application.workflow.worker.support.ProcessProgress;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CreateApplicationRecordWorker {

	private static final Logger log = LoggerFactory.getLogger(CreateApplicationRecordWorker.class);

	@JobWorker(type = "createApplicationRecord")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String applicationType,
			@Variable String integrationScenario) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.CREATE_RECORD_FAILED)) {
			Map<String, Object> failureVars = new HashMap<>();
			failureVars.put("integrationFailureReason", "Simulated create-record failure");
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.CREATE_RECORD_FAILED,
					"Core banking rejected application creation",
					failureVars);
		}
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			Map<String, Object> integrationPoint = new HashMap<>();
			integrationPoint.put("integrationPoint", "createApplicationRecord");
			throw IntegrationJobFailures.technicalFailure(
					"Core banking unavailable during create record",
					integrationPoint);
		}
		Map<String, Object> variables = new HashMap<>();
		if (StringUtils.isNotBlank(applicationId)) {
			log.info("Creating application record applicationId={} applicationType={}", applicationId, applicationType);
			variables.put("applicationStatus", "SUBMITTED");
		} else {
			String generatedId = UUID.randomUUID().toString();
			log.info("Creating application record applicationId={} applicationType={}", generatedId, applicationType);
			variables.put("applicationId", generatedId);
			variables.put("applicationStatus", "SUBMITTED");
		}
		variables.putAll(ProcessProgress.append(new ArrayList<String>(), ProcessProgress.STEP_CREATE_RECORD));
		return variables;
	}

}
