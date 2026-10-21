package com.mortgage.workflow.web.dto;

import java.util.Map;

/** Generic message correlation payload: business id plus optional message variables. */
public record PublishMessageRequest(String applicationId, Map<String, Object> variables) {

	public PublishMessageRequest(String applicationId) {
		this(applicationId, null);
	}

	public Map<String, Object> variablesOrEmpty() {
		return variables != null ? variables : Map.of();
	}
}
