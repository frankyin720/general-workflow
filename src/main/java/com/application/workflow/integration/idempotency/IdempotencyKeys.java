package com.application.workflow.integration.idempotency;

import org.apache.commons.lang3.StringUtils;

public final class IdempotencyKeys {

	private IdempotencyKeys() {
	}

	public static String forEvent(String eventType, String applicationId, String businessMessageId) {
		if (StringUtils.isNotBlank(businessMessageId)) {
			return eventType + ":" + applicationId + ":" + businessMessageId;
		}
		return eventType + ":" + applicationId;
	}

}
