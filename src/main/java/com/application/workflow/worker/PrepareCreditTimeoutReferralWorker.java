package com.application.workflow.worker;

import io.camunda.client.annotation.JobWorker;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PrepareCreditTimeoutReferralWorker {

	@JobWorker(type = "prepareCreditTimeoutReferral")
	public Map<String, Object> handle() {
		Map<String, Object> variables = new HashMap<>();
		variables.put("creditReferralReason", "Credit check timed out (15 minute SLA)");
		return variables;
	}

}
