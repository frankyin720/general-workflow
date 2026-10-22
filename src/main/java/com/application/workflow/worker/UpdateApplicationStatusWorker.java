package com.application.workflow.worker;

import com.application.workflow.worker.support.ProcessProgress;
import io.camunda.client.annotation.CustomHeaders;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UpdateApplicationStatusWorker {

	private static final Logger log = LoggerFactory.getLogger(UpdateApplicationStatusWorker.class);

	@JobWorker(type = "updateApplicationStatus")
	public Map<String, Object> handle(
			@CustomHeaders Map<String, String> headers,
			@Variable String applicationId,
			@Variable List<String> completedSteps) {
		String targetStatus = headers.getOrDefault("targetStatus", "APPROVAL");
		String approvalPath = headers.get("approvalPath");
		log.info(
				"Updating application status applicationId={} targetStatus={} approvalPath={}",
				applicationId,
				targetStatus,
				approvalPath);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", targetStatus);
		if (StringUtils.isNotBlank(approvalPath)) {
			variables.put("approvalPath", approvalPath);
			variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_APPROVAL_RECORDED));
		}
		return variables;
	}

}
