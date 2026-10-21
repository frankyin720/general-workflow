package com.mortgage.workflow.demo.web.dto;

import com.mortgage.workflow.service.ProcessDiagnosticsService.ActiveElement;
import com.mortgage.workflow.service.ProcessDiagnosticsService.IncidentSummary;
import com.mortgage.workflow.service.ProcessDiagnosticsService.JobSummary;
import com.mortgage.workflow.web.dto.UserTaskView;
import java.util.List;
import java.util.Map;

public record DemoApplicationStatusView(
		String applicationId,
		Long processInstanceKey,
		String processState,
		String currentStage,
		String stageKey,
		String suggestedRole,
		String systemHint,
		List<ActiveElement> activeElements,
		List<UserTaskView> openHumanTasks,
		List<JobSummary> activeJobs,
		List<JobSummary> failedJobs,
		List<IncidentSummary> incidents,
		Map<String, Object> variables) {
}
