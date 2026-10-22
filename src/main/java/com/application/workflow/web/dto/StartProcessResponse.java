package com.application.workflow.web.dto;

public record StartProcessResponse(long processInstanceKey, String bpmnProcessId) {
}
