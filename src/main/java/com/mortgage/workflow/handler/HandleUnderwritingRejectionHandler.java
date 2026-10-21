package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class HandleUnderwritingRejectionHandler {

	private static final Logger log = LoggerFactory.getLogger(HandleUnderwritingRejectionHandler.class);

	@JobWorker(type = "handleUnderwritingRejection")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String rejectionReason,
			@Variable(optional = true) String underwritingDecision) {
		String reason = rejectionReason != null && !rejectionReason.isBlank()
				? rejectionReason
				: "Underwriting declined";
		log.info(
				"Handling underwriting rejection applicationId={} decision={} reason={}",
				applicationId,
				underwritingDecision,
				reason);
		return Map.of(
				"applicationStatus", "REJECTED",
				"rejectionReason", reason);
	}

}
