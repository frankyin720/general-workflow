package com.mortgage.workflow.web.dto;

public record MessageCorrelationResponse(Long processInstanceKey, String messageName, String correlationKey) {
}
