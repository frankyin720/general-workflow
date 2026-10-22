package com.application.workflow.worker;

import com.application.workflow.worker.support.ProcessProgress;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompensateReleaseEdocHoldWorker {

	private static final Logger log = LoggerFactory.getLogger(CompensateReleaseEdocHoldWorker.class);

	@JobWorker(type = "compensateReleaseEdocHold")
	public Map<String, Object> handle(
			@Variable String applicationId,
			@Variable List<String> completedSteps) {
		if (!ProcessProgress.includes(completedSteps, ProcessProgress.STEP_EDOCS_VERIFIED)) {
			log.info("Skipping eDoc hold release applicationId={} (eDocs not verified)", applicationId);
			Map<String, Object> skipped = new HashMap<>();
			skipped.put("compensationSkippedReleaseEdocHold", true);
			return skipped;
		}
		log.info("Compensating: releasing eDoc hold applicationId={}", applicationId);
		// TODO: call ECM to release document lock
		Map<String, Object> variables = new HashMap<>();
		variables.put("edocsVerified", false);
		variables.put("compensationReleaseEdocHoldDone", true);
		return variables;
	}

}
