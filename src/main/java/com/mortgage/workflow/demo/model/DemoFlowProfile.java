package com.mortgage.workflow.demo.model;

import com.mortgage.workflow.handler.support.IntegrationScenario;

/** Pre-canned demo scenarios: credit score + simulated integration outcome. */
public enum DemoFlowProfile {
	HAPPY_AUTO(740, null,
			"Straight-through auto-approval (score 740, clean AML, LTV 80%)"),
	MANUAL_UNDERWRITING(650, null,
			"Manual underwriting (score 650 → underwriter review, 48h SLA escalation)"),
	AML_HIT_COMPLIANCE(740, IntegrationScenario.AML_HIT,
			"AML/KYC hit → compliance exception review before documentation"),
	CREDIT_HARD_DECLINE(740, IntegrationScenario.CREDIT_HARD_DECLINE,
			"Bureau hard decline → adverse action notice"),
	ECM_FALLBACK(740, IntegrationScenario.ECM_HARD_FAILURE,
			"ECM hard failure → manual document review by Operations"),
	COMMITMENT_LETTER_FAILURE(740, IntegrationScenario.COMMITMENT_GENERATION_FAILED,
			"Commitment letter generation fails → Operations resolves and retries"),
	FUNDING_FAILURE(740, IntegrationScenario.FUNDING_FAILED,
			"Core banking disbursement fails → reversal + manual funding intervention"),
	TECHNICAL_FAILURE(740, IntegrationScenario.TECHNICAL_FAILURE,
			"Every integration fails technically → retries, then incident in Operate");

	private final int demoCreditScore;
	private final String integrationScenario;
	private final String description;

	DemoFlowProfile(int demoCreditScore, String integrationScenario, String description) {
		this.demoCreditScore = demoCreditScore;
		this.integrationScenario = integrationScenario;
		this.description = description;
	}

	public int demoCreditScore() {
		return demoCreditScore;
	}

	public String integrationScenario() {
		return integrationScenario;
	}

	public String description() {
		return description;
	}
}
