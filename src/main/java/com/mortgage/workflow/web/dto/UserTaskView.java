package com.mortgage.workflow.web.dto;

import java.util.List;

public record UserTaskView(
		long userTaskKey,
		String name,
		String state,
		String assignee,
		List<String> candidateGroups,
		String elementId,
		Long processInstanceKey,
		String bpmnProcessId,
		String businessId,
		String creationDate) {
}
