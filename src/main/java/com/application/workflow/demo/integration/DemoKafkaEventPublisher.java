package com.application.workflow.demo.integration;

import com.application.workflow.integration.config.IntegrationConfig;
import com.application.workflow.integration.kafka.dto.EDocUploadedMessage;
import com.application.workflow.integration.kafka.dto.ESignCompletedMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "general-workflow.integration.kafka", name = "enabled", havingValue = "true")
public class DemoKafkaEventPublisher {

	private static final Logger log = LoggerFactory.getLogger(DemoKafkaEventPublisher.class);

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;
	private final IntegrationConfig integrationConfig;

	public DemoKafkaEventPublisher(
			KafkaTemplate<String, String> kafkaTemplate,
			ObjectMapper kafkaMessageObjectMapper,
			IntegrationConfig integrationConfig) {
		this.kafkaTemplate = kafkaTemplate;
		this.objectMapper = kafkaMessageObjectMapper;
		this.integrationConfig = integrationConfig;
	}

	public String publishEdocUploaded(
			String applicationId, String documentId, String documentType, String status) {
		String messageId = UUID.randomUUID().toString();
		EDocUploadedMessage message = new EDocUploadedMessage(
				applicationId, documentId, documentType, status, messageId);
		String topic = integrationConfig.kafka().topics().edocUploaded();
		send(topic, applicationId, message);
		return topic;
	}

	public String publishEsignCompleted(String applicationId, String envelopeId, String status) {
		String messageId = UUID.randomUUID().toString();
		ESignCompletedMessage message =
				new ESignCompletedMessage(applicationId, envelopeId, status, messageId);
		String topic = integrationConfig.kafka().topics().esignCompleted();
		send(topic, applicationId, message);
		return topic;
	}

	private void send(String topic, String key, Object payload) {
		try {
			String json = objectMapper.writeValueAsString(payload);
			kafkaTemplate.send(topic, key, json);
			log.info("Demo published Kafka message topic={} applicationId={}", topic, key);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Failed to serialize Kafka demo message", exception);
		}
	}
}
