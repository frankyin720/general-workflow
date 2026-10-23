package com.application.workflow.integration.camunda;

public record CorrelationResult(MessageCorrelationOutcome outcome, long processInstanceKey) {
}
