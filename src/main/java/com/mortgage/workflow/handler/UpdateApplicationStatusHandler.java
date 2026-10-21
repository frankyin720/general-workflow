package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.ProcessProgress;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UpdateApplicationStatusHandler {

	private static final Logger log = LoggerFactory.getLogger(UpdateApplicationStatusHandler.class);

	@JobWorker(type = "updateApplicationStatus")
	public Map<String, Object> handle(
			@CustomHeaders Map<String, String> headers,
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String completedSteps) {
		String targetStatus = headers.getOrDefault("targetStatus", "APPROVAL");
		String approvalPath = headers.get("approvalPath");
		log.info(
				"Updating application status applicationId={} targetStatus={} approvalPath={}",
				applicationId,
				targetStatus,
				approvalPath);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", targetStatus);
		if (approvalPath != null && !approvalPath.isBlank()) {
			variables.put("approvalPath", approvalPath);
			variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_APPROVAL_RECORDED));
		}
		return variables;
	}

}
