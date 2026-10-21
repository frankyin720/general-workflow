package com.mortgage.workflow.web.dto;

public record UserTaskView(
		long userTaskKey,
		String name,
		String state,
		String assignee,
		String elementId,
		Long processInstanceKey,
		String bpmnProcessId,
		String businessId) {
}
