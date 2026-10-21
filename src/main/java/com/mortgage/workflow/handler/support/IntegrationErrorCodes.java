package com.mortgage.workflow.handler.support;

/** BPMN error codes thrown by workers and caught by boundary events in application-workflow.bpmn. */
public final class IntegrationErrorCodes {

	public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
	public static final String CREDIT_HARD_DECLINE = "CREDIT_HARD_DECLINE";
	public static final String ECM_SERVICE_UNAVAILABLE = "ECM_SERVICE_UNAVAILABLE";
	public static final String DOCUMENT_GENERATION_FAILED = "DOCUMENT_GENERATION_FAILED";
	public static final String FUNDING_FAILED = "FUNDING_FAILED";

	private IntegrationErrorCodes() {
	}

}
