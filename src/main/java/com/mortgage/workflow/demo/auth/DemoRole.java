package com.mortgage.workflow.demo.auth;

import java.util.List;

public enum DemoRole {
	ADVISOR("Financial Advisor", "Start applications, upload documents, decide on expiries",
			List.of("Advisor")),
	CUSTOMER("Customer / Applicant", "Complete or decline eSign, withdraw application",
			List.of()),
	UNDERWRITER("Bank Underwriter", "Manual underwriting decisions",
			List.of("Bank Underwriter", "Senior Underwriter")),
	COMPLIANCE("Compliance Officer", "Pre-screening exception reviews (AML hits, bureau timeouts)",
			List.of("Compliance")),
	ECM_SYSTEM("ECM System", "Simulate document repository and appraisal events",
			List.of()),
	OPERATIONS("Operations", "Monitor applications, resolve exceptions, confirm closing",
			List.of("Operations")),
	CONDUCTOR("Workflow conductor", "Single-screen demo — all steps without switching users",
			List.of("Advisor", "Bank Underwriter", "Senior Underwriter", "Compliance", "Operations"));

	private final String label;
	private final String description;
	private final List<String> camundaGroups;

	DemoRole(String label, String description, List<String> camundaGroups) {
		this.label = label;
		this.description = description;
		this.camundaGroups = camundaGroups;
	}

	public String label() {
		return label;
	}

	public String description() {
		return description;
	}

	/** Camunda candidate groups whose user tasks this persona may claim and complete. */
	public List<String> camundaGroups() {
		return camundaGroups;
	}

	public static final String UNDERWRITER_CAMUNDA_GROUP = "Bank Underwriter";
}
