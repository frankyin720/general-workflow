package com.mortgage.workflow.service;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProcessInstanceQueryService {

	private static final List<String> STATUS_VARIABLES = List.of(
			"applicationId",
			"applicationType",
			"applicationStatus",
			"creditScore",
			"amlClear",
			"edocsVerified",
			"documentValid",
			"riskDecision",
			"underwritingDecision",
			"completedSteps",
			"integrationScenario",
			"demoCreditScore");

	private final CamundaClient camundaClient;

	public ProcessInstanceQueryService(CamundaClient camundaClient) {
		this.camundaClient = camundaClient;
	}

	public Optional<ProcessInstance> findLatestByBusinessId(String businessId) {
		if (businessId == null || businessId.isBlank()) {
			return Optional.empty();
		}
		var items = camundaClient.newProcessInstanceSearchRequest()
				.filter(filter -> filter.businessId(businessId))
				.sort(sort -> sort.startDate().desc())
				.page(p -> p.limit(1))
				.execute()
				.items();
		if (items.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(items.getFirst());
	}

	public Map<String, Object> snapshotVariables(long processInstanceKey) {
		Map<String, Object> values = new HashMap<>();
		for (String name : STATUS_VARIABLES) {
			readVariable(processInstanceKey, name).ifPresent(value -> values.put(name, value));
		}
		return values;
	}

	private Optional<Object> readVariable(long processInstanceKey, String name) {
		var items = camundaClient.newVariableSearchRequest()
				.filter(filter -> {
					filter.processInstanceKey(processInstanceKey);
					filter.name(name);
				})
				.page(p -> p.limit(1))
				.execute()
				.items();
		if (items.isEmpty()) {
			return Optional.empty();
		}
		Variable variable = items.getFirst();
		return Optional.ofNullable(coerceValue(variable.getValue()));
	}

	private static Object coerceValue(String raw) {
		if (raw == null) {
			return null;
		}
		if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
			return Boolean.parseBoolean(raw);
		}
		try {
			if (raw.contains(".")) {
				return Double.parseDouble(raw);
			}
			return Long.parseLong(raw);
		} catch (NumberFormatException ignored) {
			if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
				return raw.substring(1, raw.length() - 1);
			}
			return raw;
		}
	}

	public static boolean isActive(ProcessInstance instance) {
		return instance.getState() == ProcessInstanceState.ACTIVE;
	}

	public static boolean isCompleted(ProcessInstance instance) {
		return instance.getState() == ProcessInstanceState.COMPLETED;
	}
}
