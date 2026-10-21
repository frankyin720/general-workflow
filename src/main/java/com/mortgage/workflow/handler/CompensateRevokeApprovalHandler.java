package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.ProcessProgress;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompensateRevokeApprovalHandler {

	private static final Logger log = LoggerFactory.getLogger(CompensateRevokeApprovalHandler.class);

	@JobWorker(type = "compensateRevokeApproval")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String completedSteps,
			@Variable(optional = true) String approvalPath) {
		if (!ProcessProgress.includes(completedSteps, ProcessProgress.STEP_APPROVAL_RECORDED)
				&& (approvalPath == null || approvalPath.isBlank())) {
			log.info("Skipping approval revocation applicationId={} (no approval recorded)", applicationId);
			return Map.of("compensationSkippedRevokeApproval", true);
		}
		log.info("Compensating: revoking approval applicationId={}", applicationId);
		// TODO: call Core Banking to revoke approval hold
		return Map.of(
				"applicationStatus", "APPROVAL_REVOKED",
				"compensationRevokeApprovalDone", true);
	}

}
