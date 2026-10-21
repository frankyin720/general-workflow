package com.mortgage.workflow.handler;

import io.camunda.client.annotation.JobWorker;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PrepareCreditTimeoutReferralHandler {

	@JobWorker(type = "prepareCreditTimeoutReferral")
	public Map<String, Object> handle() {
		return Map.of("creditReferralReason", "Credit check timed out (15 minute SLA)");
	}

}
