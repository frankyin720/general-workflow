package com.application.workflow.worker;

import com.application.workflow.worker.support.ProcessProgress;
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
public class CompensateRevokeApprovalWorker {

	private static final Logger log = LoggerFactory.getLogger(CompensateRevokeApprovalWorker.class);

	@JobWorker(type = "compensateRevokeApproval")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable List<String> completedSteps,
			@Variable String approvalPath) {
		if (!ProcessProgress.includes(completedSteps, ProcessProgress.STEP_APPROVAL_RECORDED)
				&& StringUtils.isBlank(approvalPath)) {
			log.info("Skipping approval revocation applicationId={} (no approval recorded)", applicationId);
			Map<String, Object> skipped = new HashMap<>();
			skipped.put("compensationSkippedRevokeApproval", true);
			return skipped;
		}
		log.info("Compensating: revoking approval applicationId={}", applicationId);
		// TODO: call Core Banking to revoke approval hold
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "APPROVAL_REVOKED");
		variables.put("compensationRevokeApprovalDone", true);
		return variables;
	}

}
