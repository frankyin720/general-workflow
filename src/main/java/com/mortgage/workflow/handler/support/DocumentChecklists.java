package com.mortgage.workflow.handler.support;

public final class DocumentChecklists {

	private DocumentChecklists() {
	}

	public static String forApplicationType(String applicationType) {
		if (applicationType == null || applicationType.isBlank()) {
			return "ID,Income,Property";
		}
		return switch (applicationType.toUpperCase()) {
			case "MORTGAGE" -> "ID,Income,PropertyAssessment";
			case "LOC" -> "ID,Income";
			default -> "ID,Income,Property";
		};
	}

}
