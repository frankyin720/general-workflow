package com.mortgage.workflow.demo.web.dto;

import com.mortgage.workflow.service.ProcessDiagnosticsService.IncidentSummary;
import com.mortgage.workflow.service.ProcessDiagnosticsService.JobSummary;
import java.util.List;
import java.util.Map;

public record DemoApplicationStatusView(
		String applicationId,
		Long processInstanceKey,
		String processState,
		String currentStage,
		String suggestedRole,
		String systemHint,
		List<JobSummary> activeJobs,
		List<JobSummary> failedJobs,
		List<IncidentSummary> incidents,
		Map<String, Object> variables) {
}
