package com.mortgage.workflow.integration.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ESignCompletedMessage(
		String applicationId,
		String envelopeId,
		String status,
		String messageId) {
}
