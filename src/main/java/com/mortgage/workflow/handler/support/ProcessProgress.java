package com.mortgage.workflow.handler.support;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ProcessProgress {

	public static final String STEP_CREATE_RECORD = "CREATE_RECORD";
	public static final String STEP_EDOCS_VERIFIED = "EDOCS_VERIFIED";
	public static final String STEP_APPROVAL_RECORDED = "APPROVAL_RECORDED";
	public static final String STEP_FUNDING_INITIATED = "FUNDING_INITIATED";

	private ProcessProgress() {
	}

	public static Map<String, Object> append(String completedSteps, String step) {
		Set<String> steps = new LinkedHashSet<>();
		if (completedSteps != null && !completedSteps.isBlank()) {
			for (String part : completedSteps.split(",")) {
				if (!part.isBlank()) {
					steps.add(part.trim());
				}
			}
		}
		steps.add(step);
		return Map.of("completedSteps", String.join(",", steps));
	}

	public static boolean includes(String completedSteps, String step) {
		if (completedSteps == null || completedSteps.isBlank()) {
			return false;
		}
		for (String part : completedSteps.split(",")) {
			if (step.equals(part.trim())) {
				return true;
			}
		}
		return false;
	}

}
