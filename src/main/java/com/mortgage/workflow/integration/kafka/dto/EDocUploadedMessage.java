package com.mortgage.workflow.integration.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EDocUploadedMessage(
		String applicationId,
		String documentId,
		String status,
		String messageId) {
}
