package com.mortgage.workflow.integration.idempotency;

public final class IdempotencyKeys {

	private IdempotencyKeys() {
	}

	public static String forEvent(String eventType, String applicationId, String businessMessageId) {
		if (businessMessageId != null && !businessMessageId.isBlank()) {
			return eventType + ":" + applicationId + ":" + businessMessageId;
		}
		return eventType + ":" + applicationId;
	}

}
