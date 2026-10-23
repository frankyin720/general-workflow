package com.application.workflow.integration;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public final class IntegrationUtils {

	private IntegrationUtils() {
	}

	public static String idempotencyKeyForEvent(
			String eventType, String applicationId, String businessMessageId) {
		if (StringUtils.isNotBlank(businessMessageId)) {
			return eventType + ":" + applicationId + ":" + businessMessageId;
		}
		return eventType + ":" + applicationId;
	}

	public static Optional<String> firstNonBlank(String... values) {
		for (String value : values) {
			if (StringUtils.isNotBlank(value)) {
				return Optional.of(value);
			}
		}
		return Optional.empty();
	}
}
