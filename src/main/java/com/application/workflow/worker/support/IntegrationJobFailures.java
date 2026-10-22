package com.application.workflow.worker.support;

import java.util.Objects;
import io.camunda.client.exception.CamundaError;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public final class IntegrationJobFailures {

	private static final int DEFAULT_RETRIES = 3;

	private IntegrationJobFailures() {
	}

	public static RuntimeException technicalFailure(String message, Map<String, Object> variables) {
		return CamundaError.jobError(
				message,
				variables,
				DEFAULT_RETRIES,
				(retry) -> {
					int attempt = Objects.nonNull(retry) ? retry : 1;
					long seconds = (long) Math.pow(2, Math.min(attempt, 6));
					return Duration.ofSeconds(seconds);
				});
	}

	public static RuntimeException technicalFailure(String message) {
		return technicalFailure(message, new HashMap<>());
	}
}
