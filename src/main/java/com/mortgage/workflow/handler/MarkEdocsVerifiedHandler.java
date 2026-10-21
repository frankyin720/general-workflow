package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.ProcessProgress;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MarkEdocsVerifiedHandler {

	private static final Logger log = LoggerFactory.getLogger(MarkEdocsVerifiedHandler.class);

	@JobWorker(type = "markEdocsVerified")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String completedSteps) {
		log.info("Marking eDocs verified applicationId={}", applicationId);
		Map<String, Object> variables = new HashMap<>();
		variables.put("edocsVerified", true);
		variables.put("applicationStatus", "eDocs Verified");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_EDOCS_VERIFIED));
		return variables;
	}

}
