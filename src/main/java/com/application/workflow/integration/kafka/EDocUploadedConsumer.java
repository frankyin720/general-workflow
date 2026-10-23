package com.application.workflow.integration.kafka;

import com.application.workflow.integration.IntegrationUtils;
import com.application.workflow.integration.camunda.CamundaMessageProvider;
import com.application.workflow.integration.idempotency.IdempotencyService;
import com.application.workflow.integration.kafka.dto.EDocUploadedMessage;
import com.application.workflow.service.WorkflowTaskService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
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
public class EDocUploadedConsumer {
    private static final Logger log = LoggerFactory.getLogger(EDocUploadedConsumer.class);

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final CamundaMessageProvider camundaMessageProvider;

    private EDocUploadedConsumer(ObjectMapper objectMapper, IdempotencyService idempotencyService, CamundaClient camundaClient, CamundaMessageProvider camundaMessageProvider) {
        this.objectMapper = objectMapper;
        this.idempotencyService = idempotencyService;
        this.camundaMessageProvider = camundaMessageProvider;
    }

    @KafkaListener(
            topics = "${general-workflow.integration.kafka.topics.edoc-uploaded}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onEDocUploaded(ConsumerRecord<String, String> record,
                               @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String kafkaKey) {
        EDocUploadedMessage message = parse(record.value());
        validate(message);

        String dedupToken = IntegrationUtils.firstNonBlank(message.messageId(), kafkaKey, record.offset() + "@" + record.partition()).orElseGet(() -> UUID.randomUUID().toString());
        String idempotencyKey = IntegrationUtils.idempotencyKeyForEvent(WorkflowKafkaEventTypes.EDOC_UPLOADED, message.applicationId(), dedupToken);

        if (!this.idempotencyService.tryAcquire(idempotencyKey)) {
            return;
        }

        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("documentId", message.documentId());
            variables.put("documentStatus", message.status());
            if (StringUtils.isNotBlank(message.documentType())) {
                variables.put("documentType", message.documentType());
            }

            this.camundaMessageProvider.correlateOrBuffer(
                    WorkflowTaskService.MESSAGE_EDOC_UPLOADED,
                    message.applicationId(),
                    variables);
        } catch (RuntimeException e) {
            idempotencyService.release(idempotencyKey);
            throw e;
        }
    }

    private void validate(EDocUploadedMessage message) {
        if (StringUtils.isBlank(message.applicationId())) {
            throw new IllegalArgumentException("applicationId is required");
        }
    }

    private EDocUploadedMessage parse(String json) {
        try {
            return this.objectMapper.readValue(json, EDocUploadedMessage.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid EDocUploadedMessage JSON", e);
        }
    }
}
