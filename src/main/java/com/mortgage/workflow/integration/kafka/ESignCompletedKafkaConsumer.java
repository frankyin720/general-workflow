package com.mortgage.workflow.integration.kafka;

import com.mortgage.workflow.integration.camunda.CamundaMessageBridge;
import com.mortgage.workflow.integration.idempotency.IdempotencyKeys;
import com.mortgage.workflow.integration.idempotency.MessageIdempotencyService;
import com.mortgage.workflow.integration.kafka.dto.ESignCompletedMessage;
import com.mortgage.workflow.service.WorkflowTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application-workflow.integration.kafka", name = "enabled", havingValue = "true")
public class ESignCompletedKafkaConsumer {

	private static final Logger log = LoggerFactory.getLogger(ESignCompletedKafkaConsumer.class);

	private final ObjectMapper objectMapper;
	private final MessageIdempotencyService idempotencyService;
	private final CamundaMessageBridge camundaMessageBridge;

	public ESignCompletedKafkaConsumer(
			ObjectMapper objectMapper,
			MessageIdempotencyService idempotencyService,
			CamundaMessageBridge camundaMessageBridge) {
		this.objectMapper = objectMapper;
		this.idempotencyService = idempotencyService;
		this.camundaMessageBridge = camundaMessageBridge;
	}

	@KafkaListener(
			topics = "${application-workflow.integration.kafka.topics.esign-completed}",
			groupId = "${spring.kafka.consumer.group-id}")
	public void onEsignCompleted(
			ConsumerRecord<String, String> record,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey) {
		ESignCompletedMessage message = parse(record.value());
		if (message.applicationId() == null || message.applicationId().isBlank()) {
			throw new IllegalArgumentException("applicationId is required");
		}

		String dedupeToken = firstNonBlank(message.messageId(), kafkaKey, record.offset() + "@" + record.partition());
		String idempotencyKey = IdempotencyKeys.forEvent(
				WorkflowKafkaEventTypes.ESIGN_COMPLETED, message.applicationId(), dedupeToken);

		if (!idempotencyService.tryAcquire(idempotencyKey)) {
			return;
		}

		try {
			Map<String, Object> variables = new HashMap<>();
			if (message.envelopeId() != null) {
				variables.put("eSignEnvelopeId", message.envelopeId());
			}
			if (message.status() != null) {
				variables.put("eSignStatus", message.status());
			}
			camundaMessageBridge.correlateOrBuffer(
					WorkflowTaskService.MESSAGE_ESIGN_COMPLETED,
					message.applicationId(),
					variables);
		} catch (RuntimeException exception) {
			idempotencyService.release(idempotencyKey);
			throw exception;
		}
	}

	private ESignCompletedMessage parse(String json) {
		try {
			return objectMapper.readValue(json, ESignCompletedMessage.class);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Invalid ESignCompletedMessage JSON", exception);
		}
	}

	private static String firstNonBlank(String... candidates) {
		for (String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate;
			}
		}
		return null;
	}

}
