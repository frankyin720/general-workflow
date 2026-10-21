package com.mortgage.workflow.web.dto;

public record StartProcessRequest(
		String requestId,
		String applicationId,
		String applicationType,
		String integrationScenario,
		Integer demoCreditScore,
		String customerName) {

	public StartProcessRequest(String requestId, String applicationId, String applicationType, String integrationScenario) {
		this(requestId, applicationId, applicationType, integrationScenario, null, null);
	}
}
