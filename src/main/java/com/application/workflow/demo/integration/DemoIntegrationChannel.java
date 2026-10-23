package com.application.workflow.demo.integration;

import org.apache.commons.lang3.StringUtils;

public enum DemoIntegrationChannel {
	CAMUNDA_CLIENT,
	KAFKA;

	public static DemoIntegrationChannel fromRequestValue(String raw) {
		if (StringUtils.isBlank(raw)) {
			return CAMUNDA_CLIENT;
		}
		try {
			return DemoIntegrationChannel.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException exception) {
			throw new IllegalArgumentException(
					"integrationChannel must be CAMUNDA_CLIENT or KAFKA, got: " + raw);
		}
	}
}
