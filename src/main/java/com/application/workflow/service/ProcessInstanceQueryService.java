package com.application.workflow.service;

import java.util.Objects;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Service
public class ProcessInstanceQueryService {

	private static final List<String> STATUS_VARIABLES = statusVariableNames();

	private static final ObjectMapper VARIABLE_JSON = new ObjectMapper().findAndRegisterModules();

	private final CamundaClient camundaClient;

	public ProcessInstanceQueryService(CamundaClient camundaClient) {
		this.camundaClient = camundaClient;
	}

	public Optional<ProcessInstance> findLatestByBusinessId(String businessId) {
		if (StringUtils.isBlank(businessId)) {
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

	public Optional<ProcessInstance> findByProcessInstanceKey(long processInstanceKey) {
		if (processInstanceKey <= 0) {
			return Optional.empty();
		}
		var items = camundaClient.newProcessInstanceSearchRequest()
				.filter(filter -> filter.processInstanceKey(processInstanceKey))
				.page(p -> p.limit(1))
				.execute()
				.items();
		if (items.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(items.getFirst());
	}

	public Optional<ProcessInstance> findLatestByVariable(String name, String value) {
		if (StringUtils.isBlank(name)
				|| StringUtils.isBlank(value)) {
			return Optional.empty();
		}
		var items = camundaClient.newProcessInstanceSearchRequest()
				.filter(filter -> {
					Map<String, Object> variableFilter = new HashMap<>();
					variableFilter.put(name, value);
					filter.variables(variableFilter);
				})
				.sort(sort -> sort.startDate().desc())
				.page(p -> p.limit(1))
				.execute()
				.items();
		if (items.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(items.getFirst());
	}

	/** Resolves a demo application to its Camunda process (businessId is most reliable on c8run). */
	public Optional<ProcessInstance> resolveDemoApplication(String applicationId, long processInstanceKey) {
		Optional<ProcessInstance> byBusinessId = findLatestByBusinessId(applicationId);
		if (byBusinessId.isPresent()) {
			return byBusinessId;
		}
		Optional<ProcessInstance> byVariable = findLatestByVariable("applicationId", applicationId);
		if (byVariable.isPresent()) {
			return byVariable;
		}
		return findByProcessInstanceKey(processInstanceKey);
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
		if (Objects.isNull(raw)) {
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
			if (raw.startsWith("[") && raw.endsWith("]")) {
				try {
					return VARIABLE_JSON.readValue(raw, new TypeReference<java.util.List<String>>() {});
				} catch (Exception ignoredJson) {
					return raw;
				}
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

	private static List<String> statusVariableNames() {
		List<String> names = new ArrayList<>();
		names.add("applicationId");
		names.add("applicationType");
		names.add("applicationStatus");
		names.add("creditScore");
		names.add("amlClear");
		names.add("edocsVerified");
		names.add("documentValid");
		names.add("documentType");
		names.add("requiredDocuments");
		names.add("pendingDocuments");
		names.add("verifiedDocuments");
		names.add("riskDecision");
		names.add("underwritingDecision");
		names.add("completedSteps");
		names.add("integrationScenario");
		names.add("demoCreditScore");
		return names;
	}
}
