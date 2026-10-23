package com.application.workflow.integration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "general-workflow.integration")
public record IntegrationConfig(Kafka kafka, Idempotency idempotency) {

    public record Kafka(boolean enabled, Topics topics) {
        public record Topics(String edocUploaded, String esignCompleted) {

        }
    }

    public record Idempotency(boolean enabled, Duration ttl, String keyPrefix) {

    }
}
