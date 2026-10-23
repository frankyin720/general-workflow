package com.application.workflow.integration.camunda;

public enum MessageCorrelationOutcome {
	CORRELATED,
	BUFFERED;

	public String detailMessage() {
		return switch (this) {
			case CORRELATED -> "Correlated to an active message catch on the process instance.";
			case BUFFERED ->
					"No active subscriber for this message — published to buffer (TTL ~1 hour). "
							+ "The running instance will not move unless it reaches a matching wait state in time.";
		};
	}
}
