package com.application.workflow.integration.kafka;

import java.util.Objects;
import com.application.workflow.integration.camunda.CamundaMessageBridge;
import com.application.workflow.integration.idempotency.IdempotencyKeys;
import com.application.workflow.integration.idempotency.MessageIdempotencyService;
import com.application.workflow.integration.kafka.dto.ESignCompletedMessage;
import com.application.workflow.service.WorkflowTaskService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "general-workflow.integration.kafka", name = "enabled", havingValue = "true")
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
			topics = "${general-workflow.integration.kafka.topics.esign-completed}",
			groupId = "${spring.kafka.consumer.group-id}")
	public void onEsignCompleted(
			ConsumerRecord<String, String> record,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey) {
		ESignCompletedMessage message = parse(record.value());
		if (StringUtils.isBlank(message.applicationId())) {
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
			if (Objects.nonNull(message.envelopeId())) {
				variables.put("eSignEnvelopeId", message.envelopeId());
			}
			if (Objects.nonNull(message.status())) {
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
			if (StringUtils.isNotBlank(candidate)) {
				return candidate;
			}
		}
		return "";
	}

}
