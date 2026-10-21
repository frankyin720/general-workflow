package com.mortgage.workflow.handler.support;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Comma-separated milestone tracker stored in process variable {@code completedSteps}. */
public final class ProcessProgress {

	public static final String STEP_MORTGAGE_FILE_CREATED = "MORTGAGE_FILE_CREATED";
	public static final String STEP_CREDIT_BUREAU = "CREDIT_BUREAU";
	public static final String STEP_AML_KYC = "AML_KYC";
	public static final String STEP_PRE_SCREENING_PASSED = "PRE_SCREENING_PASSED";
	public static final String STEP_FINANCIAL_DOCS_VERIFIED = "FINANCIAL_DOCS_VERIFIED";
	public static final String STEP_APPRAISAL_REQUESTED = "APPRAISAL_REQUESTED";
	public static final String STEP_LTV_CALCULATED = "LTV_CALCULATED";
	public static final String STEP_DEBT_SERVICE_CALCULATED = "DEBT_SERVICE_CALCULATED";
	public static final String STEP_APPROVED = "APPROVED";
	public static final String STEP_COMMITMENT_LETTER = "COMMITMENT_LETTER";
	public static final String STEP_COMMITMENT_SENT = "COMMITMENT_SENT";
	public static final String STEP_SOLICITOR_INSTRUCTIONS = "SOLICITOR_INSTRUCTIONS";
	public static final String STEP_FUNDING_CHECKLIST = "FUNDING_CHECKLIST";
	public static final String STEP_FUNDING_INITIATED = "FUNDING_INITIATED";
	public static final String STEP_MORTGAGE_ACTIVE = "MORTGAGE_ACTIVE";

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
