package com.mortgage.workflow.integration.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@EnableKafka
@ConditionalOnProperty(prefix = "application-workflow.integration.kafka", name = "enabled", havingValue = "true")
public class WorkflowKafkaIntegrationConfiguration {
}
