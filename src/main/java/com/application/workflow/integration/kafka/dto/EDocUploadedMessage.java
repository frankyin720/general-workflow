package com.application.workflow.integration.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EDocUploadedMessage(
		String applicationId,
		String documentId,
		String documentType,
		String status,
		String messageId) {
}
