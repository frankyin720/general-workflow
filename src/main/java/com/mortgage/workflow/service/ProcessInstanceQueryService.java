package com.mortgage.workflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.Variable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ProcessInstanceQueryService {

	/** Process-scope variables surfaced to the demo portal status view. */
	private static final Set<String> STATUS_VARIABLES = Set.of(
			"applicationId", "applicationType", "applicationStatus", "customerName", "mortgageFileNumber",
			"loanAmount", "purchasePrice", "annualIncome",
			"creditScore", "creditCheckOutcome", "amlOutcome", "amlClear", "preScreening", "complianceDecision",
			"requiredDocuments", "verifiedDocumentTypes", "outstandingDocuments", "financialDocumentsVerified",
			"documentValid", "documentReminderCount", "documentExpiryDecision",
			"appraisalRequestId", "appraisedValue", "ltvRatio", "propertyAppraisalComplete", "appraisalFollowUpDecision",
			"qualifyingRate", "gdsRatio", "tdsRatio", "underwriting", "underwritingDecision", "approvalPath",
			"approvalConditionsText", "insuranceRequired", "escalationCount", "lastEscalation",
			"declineStage", "declineReason",
			"commitmentLetterId", "commitmentVersion", "commitmentExpiresAt", "eSignStatus", "esignReminderCount",
			"commitmentExpiryDecision", "clientDeclineReason", "solicitorInstructionsSent",
			"closingConfirmed", "closingStatus", "closingDelayDecision", "fundingReadiness", "outstandingConditions",
			"disbursementAttempt", "disbursementReference", "fundingFailureReason", "fundingInterventionDecision",
			"mortgageAccountNumber", "cancelReason",
			"completedSteps", "integrationScenario", "demoCreditScore", "lastNotification");

	private final CamundaClient camundaClient;
	private final ObjectMapper objectMapper = new ObjectMapper();

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

	/** Reads all process-scope variables in one query and keeps the ones relevant for the status view. */
	public Map<String, Object> snapshotVariables(long processInstanceKey) {
		Map<String, Object> values = new HashMap<>();
		List<Variable> items = camundaClient.newVariableSearchRequest()
				.filter(filter -> {
					filter.processInstanceKey(processInstanceKey);
					filter.scopeKey(processInstanceKey);
				})
				.page(p -> p.limit(200))
				.execute()
				.items();
		for (Variable variable : items) {
			if (STATUS_VARIABLES.contains(variable.getName())) {
				Object value = coerceValue(variable.getValue());
				if (value != null) {
					values.put(variable.getName(), value);
				}
			}
		}
		return values;
	}

	private Object coerceValue(String raw) {
		if (raw == null || raw.isBlank() || "null".equals(raw)) {
			return null;
		}
		try {
			return objectMapper.readValue(raw, Object.class);
		} catch (JsonProcessingException ignored) {
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
