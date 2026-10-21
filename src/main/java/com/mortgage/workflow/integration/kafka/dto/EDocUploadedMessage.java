package com.mortgage.workflow.integration.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * @param documentType NOA / T4 / EMPLOYMENT_LETTER / BANK_STATEMENT — drives the required-document
 *     checklist; when absent the upload is treated as covering the whole checklist
 * @param status UPLOADED (default) or INVALID
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EDocUploadedMessage(
		String applicationId,
		String documentId,
		String documentType,
		String status,
		String messageId) {
}
