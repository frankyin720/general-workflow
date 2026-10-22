package com.application.workflow.worker.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public final class DocumentChecklists {

	public static final int DEMO_MAX_DOCUMENTS = 3;

	public static final List<String> DEMO_DOCUMENT_TYPES = mutableList("ID", "Income", "PropertyAssessment");

	private DocumentChecklists() {
	}

	public static List<String> typesForApplicationType(String applicationType) {
		return switch (normalizeApplicationTypeKey(applicationType)) {
			case "GENERAL" -> mutableList("ID", "Income", "PropertyAssessment");
			case "LOC" -> mutableList("ID", "Income");
			default -> mutableList("ID", "Income", "Property");
		};
	}

	static String normalizeApplicationTypeKey(String applicationType) {
		if (StringUtils.isBlank(applicationType)) {
			return "GENERAL";
		}
		return applicationType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	public static String toCsv(List<String> types) {
		if (Objects.isNull(types) || types.isEmpty()) {
			return "";
		}
		return String.join(",", types);
	}

	public static List<String> parseCsv(String csv) {
		if (StringUtils.isBlank(csv)) {
			return new ArrayList<>();
		}
		List<String> out = new ArrayList<>();
		for (String part : csv.split(",")) {
			String trimmed = StringUtils.trim(part);
			if (StringUtils.isNotBlank(trimmed)) {
				out.add(trimmed);
			}
		}
		return out;
	}

	/**
	 * Demo portal may pass {@code demoRequiredDocuments} (CSV, 1–3 items). Otherwise use product defaults.
	 */
	public static List<String> resolveRequiredDocuments(String applicationType, String demoRequiredDocuments) {
		if (StringUtils.isNotBlank(demoRequiredDocuments)) {
			return normalizeDemoChecklist(parseCsv(demoRequiredDocuments));
		}
		return typesForApplicationType(applicationType);
	}

	public static List<String> normalizeDemoChecklist(List<String> requested) {
		if (Objects.isNull(requested) || requested.isEmpty()) {
			throw new IllegalArgumentException("Document checklist must include at least one type");
		}
		if (requested.size() > DEMO_MAX_DOCUMENTS) {
			throw new IllegalArgumentException("Document checklist supports at most " + DEMO_MAX_DOCUMENTS + " types");
		}
		Set<String> allowed = new LinkedHashSet<>();
		for (String type : requested) {
			if (StringUtils.isBlank(type)) {
				continue;
			}
			String canonical = canonicalDemoType(type.trim());
			if (!DEMO_DOCUMENT_TYPES.contains(canonical)) {
				throw new IllegalArgumentException("Unsupported document type: " + type);
			}
			allowed.add(canonical);
		}
		if (allowed.isEmpty()) {
			throw new IllegalArgumentException("Document checklist must include at least one type");
		}
		if (allowed.size() > DEMO_MAX_DOCUMENTS) {
			throw new IllegalArgumentException("Document checklist supports at most " + DEMO_MAX_DOCUMENTS + " types");
		}
		return new ArrayList<>(allowed);
	}

	private static String canonicalDemoType(String type) {
		for (String allowed : DEMO_DOCUMENT_TYPES) {
			if (allowed.equalsIgnoreCase(type)) {
				return allowed;
			}
		}
		return type;
	}

	public static List<String> copyPending(List<String> pendingDocuments) {
		if (Objects.isNull(pendingDocuments) || pendingDocuments.isEmpty()) {
			return new ArrayList<>();
		}
		return new ArrayList<>(pendingDocuments);
	}

	public static List<String> copyVerified(List<String> verifiedDocuments) {
		if (Objects.isNull(verifiedDocuments) || verifiedDocuments.isEmpty()) {
			return new ArrayList<>();
		}
		return new ArrayList<>(verifiedDocuments);
	}

	public static String resolveDocumentType(String documentType, List<String> pendingDocuments) {
		if (StringUtils.isNotBlank(documentType)) {
			return documentType.trim();
		}
		if (Objects.nonNull(pendingDocuments) && !pendingDocuments.isEmpty()) {
			return pendingDocuments.getFirst();
		}
		return "";
	}

	private static List<String> mutableList(String... items) {
		List<String> list = new ArrayList<>();
		for (String item : items) {
			list.add(item);
		}
		return list;
	}

}
