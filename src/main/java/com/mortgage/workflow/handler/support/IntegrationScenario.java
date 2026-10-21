package com.mortgage.workflow.handler.support;

/**
 * Optional process variable {@code integrationScenario} used to simulate integration outcomes in
 * local / demo environments. Production integrations ignore it.
 */
public final class IntegrationScenario {

	/** Worker fails with retries/backoff; after retries are exhausted Camunda raises an incident. */
	public static final String TECHNICAL_FAILURE = "TECHNICAL_FAILURE";
	/** Create mortgage file rejects input (BPMN error VALIDATION_FAILED). */
	public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
	/** Credit bureau hard decline (BPMN error CREDIT_HARD_DECLINE). */
	public static final String CREDIT_HARD_DECLINE = "CREDIT_HARD_DECLINE";
	/** AML / KYC returns a sanctions / PEP hit → pre-screening manual review. */
	public static final String AML_HIT = "AML_HIT";
	/** ECM is unavailable as a technical failure (retries). */
	public static final String ECM_UNAVAILABLE = "ECM_UNAVAILABLE";
	/** ECM hard failure (BPMN error ECM_SERVICE_UNAVAILABLE → manual document review). */
	public static final String ECM_HARD_FAILURE = "ECM_HARD_FAILURE";
	/** Commitment letter generation fails (BPMN error DOCUMENT_GENERATION_FAILED). */
	public static final String COMMITMENT_GENERATION_FAILED = "COMMITMENT_GENERATION_FAILED";
	/** Core banking disbursement fails (BPMN error FUNDING_FAILED → manual intervention). */
	public static final String FUNDING_FAILED = "FUNDING_FAILED";

	private IntegrationScenario() {
	}

	public static boolean is(String integrationScenario, String expected) {
		return integrationScenario != null && integrationScenario.equalsIgnoreCase(expected);
	}

}
