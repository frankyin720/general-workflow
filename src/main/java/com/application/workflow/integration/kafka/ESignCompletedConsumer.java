package com.application.workflow.integration.kafka;

import com.application.workflow.integration.IntegrationUtils;
import com.application.workflow.integration.camunda.CamundaMessageProvider;
import com.application.workflow.integration.idempotency.IdempotencyService;
import com.application.workflow.integration.kafka.dto.ESignCompletedMessage;
import com.application.workflow.service.WorkflowTaskService;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ConditionalOnProperty(prefix = "general-workflow.integration.kafka", name = "enabled", havingValue = "true")
@Component
public class ESignCompletedConsumer {
    private static final Logger log = LoggerFactory.getLogger(ESignCompletedConsumer.class);

    private final ObjectMapper objectMapper;
    private final CamundaMessageProvider camundaMessageProvider;
    private final IdempotencyService idempotencyService;

    public ESignCompletedConsumer(ObjectMapper objectMapper, CamundaMessageProvider camundaMessageProvider, IdempotencyService idempotencyService) {
        this.objectMapper = objectMapper;
        this.camundaMessageProvider = camundaMessageProvider;
        this.idempotencyService = idempotencyService;
    }

    @KafkaListener(
            topics = "${general-workflow.integration.kafka.topics.esign-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onESignCompleted(ConsumerRecord<String, String> record,
                                 @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey) {
        ESignCompletedMessage message = parse(record.value());
        validate(message);

        String dedupKey = IntegrationUtils.firstNonBlank(message.messageId(), kafkaKey, record.offset() + "@" + record.partition()).orElseGet(() -> UUID.randomUUID().toString());
        String redisKey = IntegrationUtils.idempotencyKeyForEvent(WorkflowTaskService.MESSAGE_ESIGN_COMPLETED, message.applicationId(), dedupKey);

        if (!this.idempotencyService.tryAcquire(redisKey)) {
            return;
        }

        try {
            Map<String, Object> variables = new HashMap<>();
            if (StringUtils.isNotBlank(message.envelopeId())) {
                variables.put("eSignEnvelopeId", message.envelopeId());
            }
            if (StringUtils.isNotBlank(message.status())) {
                variables.put("eSignStatus", message.status());
            }

            this.camundaMessageProvider.correlateOrBuffer(WorkflowTaskService.MESSAGE_ESIGN_COMPLETED, message.applicationId(), variables);

        } catch (Exception e) {
            idempotencyService.release(redisKey);
            throw e;
        }
    }

    private void validate(ESignCompletedMessage message) {
        if (StringUtils.isBlank(message.applicationId())) {
            throw new IllegalArgumentException("applicationId is required");
        }
    }

    private ESignCompletedMessage parse(String json) {
        try {
            return this.objectMapper.readValue(json, ESignCompletedMessage.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse message from " + json, e);
            throw new IllegalArgumentException(e);
        }
    }
}
