package com.application.workflow.integration.kafka;

import java.util.Objects;
import com.application.workflow.integration.camunda.CamundaMessageBridge;
import com.application.workflow.integration.idempotency.IdempotencyKeys;
import com.application.workflow.integration.idempotency.MessageIdempotencyService;
import com.application.workflow.integration.kafka.dto.EDocUploadedMessage;
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

/**
 * Consumes ECM eDoc upload events (AWS MSK) and correlates {@code eDocUploadedEvent} to the General Workflow process.
 */
@Component
@ConditionalOnProperty(prefix = "general-workflow.integration.kafka", name = "enabled", havingValue = "true")
public class EDocUploadedKafkaConsumer {

	private static final Logger log = LoggerFactory.getLogger(EDocUploadedKafkaConsumer.class);

	private final ObjectMapper objectMapper;
	private final MessageIdempotencyService idempotencyService;
	private final CamundaMessageBridge camundaMessageBridge;

	public EDocUploadedKafkaConsumer(
			ObjectMapper objectMapper,
			MessageIdempotencyService idempotencyService,
			CamundaMessageBridge camundaMessageBridge) {
		this.objectMapper = objectMapper;
		this.idempotencyService = idempotencyService;
		this.camundaMessageBridge = camundaMessageBridge;
	}

	@KafkaListener(
			topics = "${general-workflow.integration.kafka.topics.edoc-uploaded}",
			groupId = "${spring.kafka.consumer.group-id}")
	public void onEdocUploaded(
			ConsumerRecord<String, String> record,
			@Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey) {
		EDocUploadedMessage message = parse(record.value());
		validate(message);

		String dedupeToken = firstNonBlank(message.messageId(), kafkaKey, record.offset() + "@" + record.partition());
		String idempotencyKey = IdempotencyKeys.forEvent(
				WorkflowKafkaEventTypes.EDOC_UPLOADED, message.applicationId(), dedupeToken);

		if (!idempotencyService.tryAcquire(idempotencyKey)) {
			return;
		}

		try {
			Map<String, Object> variables = new HashMap<>();
			variables.put("documentId", message.documentId());
			variables.put("documentStatus", message.status());
			if (StringUtils.isNotBlank(message.documentType())) {
				variables.put("documentType", message.documentType().trim());
			}
			camundaMessageBridge.correlateOrBuffer(
					WorkflowTaskService.MESSAGE_EDOC_UPLOADED,
					message.applicationId(),
					variables);
		} catch (RuntimeException exception) {
			idempotencyService.release(idempotencyKey);
			throw exception;
		}
	}

	private EDocUploadedMessage parse(String json) {
		try {
			return objectMapper.readValue(json, EDocUploadedMessage.class);
		} catch (Exception exception) {
			throw new IllegalArgumentException("Invalid EDocUploadedMessage JSON", exception);
		}
	}

	private static void validate(EDocUploadedMessage message) {
		if (StringUtils.isBlank(message.applicationId())) {
			throw new IllegalArgumentException("applicationId is required");
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
