package com.application.workflow.demo.auth;

public enum DemoRole {
	ADVISOR("Financial Advisor", "Start applications and upload documents"),
	CUSTOMER("Customer / Applicant", "Complete eSign"),
	UNDERWRITER("Bank Underwriter", "Manual underwriting decisions"),
	ECM_SYSTEM("ECM System", "Simulate document repository events"),
	OPERATIONS("Operations", "Monitor applications and integration activity"),
	CONDUCTOR("Workflow conductor", "Single-screen demo — all steps without switching users");

	private final String label;
	private final String description;

	DemoRole(String label, String description) {
		this.label = label;
		this.description = description;
	}

	public String label() {
		return label;
	}

	public String description() {
		return description;
	}

	public static final String UNDERWRITER_CAMUNDA_GROUP = "Bank Underwriter";
}
