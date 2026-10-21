package com.mortgage.workflow.demo.integration;

import java.time.Instant;

public record DemoIntegrationEvent(
		Instant at,
		String applicationId,
		String source,
		String action,
		String detail) {
}
