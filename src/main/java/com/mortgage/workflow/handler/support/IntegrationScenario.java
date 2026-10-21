package com.mortgage.workflow.handler.support;

/**
 * Optional process variable {@code integrationScenario} for local/testing simulation:
 * TECHNICAL_FAILURE, CREDIT_HARD_DECLINE, ECM_UNAVAILABLE, FUNDING_FAILED, CREATE_RECORD_FAILED.
 */
public final class IntegrationScenario {

	public static final String TECHNICAL_FAILURE = "TECHNICAL_FAILURE";
	public static final String CREDIT_HARD_DECLINE = "CREDIT_HARD_DECLINE";
	public static final String ECM_UNAVAILABLE = "ECM_UNAVAILABLE";
	public static final String FUNDING_FAILED = "FUNDING_FAILED";
	public static final String CREATE_RECORD_FAILED = "CREATE_RECORD_FAILED";
	public static final String ECM_HARD_FAILURE = "ECM_HARD_FAILURE";

	private IntegrationScenario() {
	}

	public static boolean is(String integrationScenario, String expected) {
		return integrationScenario != null && integrationScenario.equalsIgnoreCase(expected);
	}

}
