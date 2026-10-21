package com.mortgage.workflow.web.dto;

public record StartProcessResponse(long processInstanceKey, String bpmnProcessId) {
}
