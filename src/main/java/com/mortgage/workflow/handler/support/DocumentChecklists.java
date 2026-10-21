package com.mortgage.workflow.handler.support;

import java.util.List;

/** Required income / financial document types per product (Branch A of phase 2). */
public final class DocumentChecklists {

	public static final String NOTICE_OF_ASSESSMENT = "NOA";
	public static final String T4 = "T4";
	public static final String EMPLOYMENT_LETTER = "EMPLOYMENT_LETTER";
	public static final String BANK_STATEMENT = "BANK_STATEMENT";

	private DocumentChecklists() {
	}

	public static List<String> forApplicationType(String applicationType) {
		if (applicationType == null || applicationType.isBlank()) {
			return List.of(NOTICE_OF_ASSESSMENT, T4, EMPLOYMENT_LETTER);
		}
		return switch (applicationType.toUpperCase()) {
			case "MORTGAGE", "REFINANCE" -> List.of(NOTICE_OF_ASSESSMENT, T4, EMPLOYMENT_LETTER);
			case "LOC", "HELOC" -> List.of(NOTICE_OF_ASSESSMENT, BANK_STATEMENT);
			default -> List.of(NOTICE_OF_ASSESSMENT, T4, EMPLOYMENT_LETTER);
		};
	}

}
