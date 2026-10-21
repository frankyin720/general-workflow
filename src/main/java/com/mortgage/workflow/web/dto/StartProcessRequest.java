package com.mortgage.workflow.web.dto;

import java.util.Map;

/**
 * @param financials optional applicant financials (loanAmount, purchasePrice, annualIncome,
 *     monthlyDebtPayments, propertyTaxesAnnual, heatingMonthly, interestRate, amortizationYears)
 */
public record StartProcessRequest(
		String requestId,
		String applicationId,
		String applicationType,
		String integrationScenario,
		Integer demoCreditScore,
		String customerName,
		Map<String, Object> financials) {

	public StartProcessRequest(String requestId, String applicationId, String applicationType, String integrationScenario) {
		this(requestId, applicationId, applicationType, integrationScenario, null, null, null);
	}

	public StartProcessRequest(
			String requestId,
			String applicationId,
			String applicationType,
			String integrationScenario,
			Integer demoCreditScore,
			String customerName) {
		this(requestId, applicationId, applicationType, integrationScenario, demoCreditScore, customerName, null);
	}
}
