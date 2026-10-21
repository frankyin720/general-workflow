package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.ProcessProgress;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CompensateReleaseEdocHoldHandler {

	private static final Logger log = LoggerFactory.getLogger(CompensateReleaseEdocHoldHandler.class);

	@JobWorker(type = "compensateReleaseEdocHold")
	public Map<String, Object> handle(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String completedSteps) {
		if (!ProcessProgress.includes(completedSteps, ProcessProgress.STEP_EDOCS_VERIFIED)) {
			log.info("Skipping eDoc hold release applicationId={} (eDocs not verified)", applicationId);
			return Map.of("compensationSkippedReleaseEdocHold", true);
		}
		log.info("Compensating: releasing eDoc hold applicationId={}", applicationId);
		// TODO: call ECM to release document lock
		return Map.of(
				"edocsVerified", false,
				"compensationReleaseEdocHoldDone", true);
	}

}
