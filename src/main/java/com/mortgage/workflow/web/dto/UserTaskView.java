package com.mortgage.workflow.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record UserTaskView(
		@JsonSerialize(using = ToStringSerializer.class) long userTaskKey,
		String name,
		String state,
		String assignee,
		String elementId,
		@JsonSerialize(using = ToStringSerializer.class) Long processInstanceKey,
		String bpmnProcessId,
		String businessId) {
}
