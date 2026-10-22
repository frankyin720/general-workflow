package com.application.workflow.integration.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "general-workflow.integration")
public record IntegrationProperties(Kafka kafka, Idempotency idempotency) {

	public record Kafka(
			boolean enabled,
			Topics topics) {
	}

	public record Topics(
			String edocUploaded,
			String esignCompleted) {
	}

	public record Idempotency(
			boolean enabled,
			Duration ttl,
			String keyPrefix) {
	}

}
