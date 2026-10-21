package com.mortgage.workflow.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mortgage.workflow.handler.support.IntegrationErrorCodes;
import com.mortgage.workflow.handler.support.IntegrationScenario;
import io.camunda.client.exception.BpmnError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Pure-logic checks of the job workers (no Zeebe). */
class WorkerLogicTest {

	private final PreScreeningHandlers preScreening = new PreScreeningHandlers();
	private final DocumentationHandlers documentation = new DocumentationHandlers();
	private final UnderwritingHandlers underwriting = new UnderwritingHandlers();
	private final FundingHandlers funding = new FundingHandlers();

	@Test
	void defaultApplicantIsAutoApprovable() {
		Map<String, Object> file = preScreening.createMortgageFile(
				"APP-1", "Mortgage", "Jamie", null, null, null, null, null, null, null, null, null);
		assertThat(file).containsEntry("applicationStatus", "SUBMITTED")
				.containsEntry("requiredDocuments", List.of("NOA", "T4", "EMPLOYMENT_LETTER"));

		Map<String, Object> ratios = underwriting.calculateDebtServiceRatios(
				"APP-1", 520_000d, 145_000d, 600d, 4_800d, 150d, 5.49d, 25, null);
		double gds = (double) ratios.get("gdsRatio");
		double tds = (double) ratios.get("tdsRatio");
		assertThat((double) ratios.get("qualifyingRate")).isEqualTo(7.49);
		assertThat(gds).isBetween(28d, 39d);
		assertThat(tds).isBetween(gds, 44d);

		Map<String, Object> ltv = documentation.calculateLoanToValue("APP-1", 520_000d, 650_000d, 660_000d, null);
		assertThat(ltv).containsEntry("ltvRatio", 80.0).containsEntry("lendingValue", 650_000d);
	}

	@Test
	void createMortgageFileRejectsInvalidInput() {
		assertThatThrownBy(() -> preScreening.createMortgageFile(
				"APP-2", "Mortgage", null, 700_000d, 650_000d, null, null, null, null, null, null, null))
				.isInstanceOf(BpmnError.class)
				.hasMessageContaining("exceed");
		assertThatThrownBy(() -> preScreening.createMortgageFile(
				"APP-3", "Mortgage", null, null, null, null, null, null, null, null, null, IntegrationScenario.VALIDATION_FAILED))
				.isInstanceOf(BpmnError.class)
				.extracting("errorCode").isEqualTo(IntegrationErrorCodes.VALIDATION_FAILED);
	}

	@Test
	void screeningExceptionsFeedTheEligibilityDecision() {
		Map<String, Object> credit = preScreening.recordScreeningException(
				Map.of("screening", "CREDIT", "outcome", "TIMEOUT"), "APP-1", null);
		assertThat(credit).containsEntry("creditCheckOutcome", "TIMEOUT");
		Map<String, Object> aml = preScreening.recordScreeningException(
				Map.of("screening", "AML", "outcome", "TIMEOUT"), "APP-1", null);
		assertThat(aml).containsEntry("amlOutcome", "TIMEOUT").containsEntry("amlClear", false);
		assertThat(preScreening.amlKycScreening("APP-1", IntegrationScenario.AML_HIT, null))
				.containsEntry("amlOutcome", "HIT");
	}

	@Test
	void documentChecklistTracksRequiredTypes() {
		List<String> required = List.of("NOA", "T4", "EMPLOYMENT_LETTER");
		Map<String, Object> first = documentation.updateDocumentChecklist("APP-1", required, List.of(), "NOA", null);
		assertThat(first).containsEntry("financialDocumentsVerified", false)
				.containsEntry("outstandingDocuments", List.of("T4", "EMPLOYMENT_LETTER"));
		Map<String, Object> all = documentation.updateDocumentChecklist("APP-1", required, List.of("NOA", "T4"), "employment_letter", null);
		assertThat(all).containsEntry("financialDocumentsVerified", true).containsEntry("outstandingDocuments", List.of());
		Map<String, Object> shortcut = documentation.updateDocumentChecklist("APP-1", required, List.of(), null, null);
		assertThat(shortcut).containsEntry("financialDocumentsVerified", true);
	}

	@Test
	void fundingChecklistNeverLeavesNullsForTheDmn() {
		Map<String, Object> checklist = funding.prepareFundingChecklist(
				"APP-1", null, "CONFIRMED", true, null, null, null, "Fire insurance\n\nVoid cheque", null, null, null);
		assertThat(checklist)
				.containsEntry("closingConfirmed", true)
				.containsEntry("solicitorInstructionsSent", true)
				.containsEntry("insuranceRequired", false)
				.containsEntry("insuranceConfirmed", false)
				.containsEntry("outstandingConditions", 2)
				.containsEntry("fundingHold", false);
	}

	@Test
	void approvalConditionsAcceptListOrFreeText() {
		// list from a form / API client
		assertThat(funding.prepareFundingChecklist("APP-1", true, null, true, null, null,
				List.of("Proof of down payment", "Void cheque"), null, null, null, null))
				.containsEntry("outstandingConditions", 2);
		// single string typed by an underwriter (previously crashed the worker with a deserialization error)
		assertThat(funding.prepareFundingChecklist("APP-1", true, null, true, null, null,
				"Proof of down payment", null, null, null, null))
				.containsEntry("outstandingConditions", 1);
		assertThat(FundingHandlers.countConditions(null)).isZero();
		assertThat(FundingHandlers.countConditions("  \n ")).isZero();
	}

	@Test
	void disbursementFailsOnceThenSucceedsOnRetry() {
		assertThatThrownBy(() -> funding.disburseMortgage("APP-1", 520_000d, null, IntegrationScenario.FUNDING_FAILED, null))
				.isInstanceOf(BpmnError.class)
				.extracting("errorCode").isEqualTo(IntegrationErrorCodes.FUNDING_FAILED);
		Map<String, Object> retry = funding.disburseMortgage("APP-1", 520_000d, 1, IntegrationScenario.FUNDING_FAILED, null);
		assertThat(retry).containsEntry("disbursementAttempt", 2).containsEntry("disbursementComplete", true);
	}

	@Test
	void cancelReasonIsDerivedFromTheDecisionThatLedHere() {
		assertThat(funding.cancelApplication(Map.of(), "APP-1", null, "CANCEL", null, null, null))
				.containsEntry("cancelReason", "DOCUMENTS_EXPIRED").containsEntry("applicationStatus", "CANCELLED");
		assertThat(funding.cancelApplication(Map.of("targetStatus", "WITHDRAWN", "cancelReason", "CLIENT_WITHDRAWN"),
				"APP-1", null, null, null, null, null))
				.containsEntry("cancelReason", "CLIENT_WITHDRAWN").containsEntry("applicationStatus", "WITHDRAWN");
	}

}
