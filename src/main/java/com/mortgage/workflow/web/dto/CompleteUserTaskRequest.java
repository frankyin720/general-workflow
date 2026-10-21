package com.mortgage.workflow.web.dto;

import java.util.Map;

public record CompleteUserTaskRequest(String action, Map<String, Object> variables) {
}
