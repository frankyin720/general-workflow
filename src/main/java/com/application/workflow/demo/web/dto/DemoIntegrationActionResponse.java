package com.application.workflow.demo.web.dto;

public record DemoIntegrationActionResponse(
		String integrationChannel,
		String delivery,
		String messageName,
		String applicationId,
		Long processInstanceKey,
		String outcome,
		String detail) {
}
