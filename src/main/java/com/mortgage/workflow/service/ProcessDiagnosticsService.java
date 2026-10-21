package com.mortgage.workflow.service;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.Job;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProcessDiagnosticsService {

	private final CamundaClient camundaClient;

	public ProcessDiagnosticsService(CamundaClient camundaClient) {
		this.camundaClient = camundaClient;
	}

	public List<JobSummary> activeJobs(long processInstanceKey) {
		return camundaClient.newJobSearchRequest()
				.filter(filter -> {
					filter.processInstanceKey(processInstanceKey);
					filter.state(JobState.CREATED);
				})
				.page(p -> p.limit(20))
				.execute()
				.items()
				.stream()
				.map(this::toJobSummary)
				.toList();
	}

	public List<JobSummary> failedJobs(long processInstanceKey) {
		return camundaClient.newJobSearchRequest()
				.filter(filter -> {
					filter.processInstanceKey(processInstanceKey);
					filter.state(JobState.FAILED);
				})
				.page(p -> p.limit(20))
				.execute()
				.items()
				.stream()
				.map(this::toJobSummary)
				.toList();
	}

	public List<IncidentSummary> openIncidents(long processInstanceKey) {
		return camundaClient.newIncidentSearchRequest()
				.filter(filter -> filter.processInstanceKey(processInstanceKey))
				.page(p -> p.limit(20))
				.execute()
				.items()
				.stream()
				.map(this::toIncidentSummary)
				.toList();
	}

	private JobSummary toJobSummary(Job job) {
		return new JobSummary(
				job.getType(),
				job.getState() != null ? job.getState().name() : null,
				job.getRetries() != null ? job.getRetries() : 0);
	}

	private IncidentSummary toIncidentSummary(Incident incident) {
		return new IncidentSummary(
				incident.getElementId(),
				incident.getErrorMessage());
	}

	public record JobSummary(String type, String state, int retries) {
	}

	public record IncidentSummary(String elementId, String errorMessage) {
	}
}
