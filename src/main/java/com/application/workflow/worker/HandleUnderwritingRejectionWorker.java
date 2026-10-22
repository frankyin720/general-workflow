package com.application.workflow.worker;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HandleUnderwritingRejectionWorker {

	private static final Logger log = LoggerFactory.getLogger(HandleUnderwritingRejectionWorker.class);

	@JobWorker(type = "handleUnderwritingRejection")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable String rejectionReason,
			@Variable String underwritingDecision) {
		String reason = StringUtils.isNotBlank(rejectionReason)
				? rejectionReason
				: "Underwriting declined";
		log.info(
				"Handling underwriting rejection applicationId={} decision={} reason={}",
				applicationId,
				underwritingDecision,
				reason);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "REJECTED");
		variables.put("rejectionReason", reason);
		return variables;
	}

}
