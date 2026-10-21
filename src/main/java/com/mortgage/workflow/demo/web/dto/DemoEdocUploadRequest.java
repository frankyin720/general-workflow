package com.mortgage.workflow.demo.web.dto;

/**
 * @param documentType NOA / T4 / EMPLOYMENT_LETTER / BANK_STATEMENT; null = "all documents" shortcut
 * @param documentStatus UPLOADED (default) or INVALID to simulate an ECM rejection
 */
public record DemoEdocUploadRequest(String documentId, String documentType, String documentStatus) {
}
