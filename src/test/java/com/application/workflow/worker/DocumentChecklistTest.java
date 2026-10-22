package com.application.workflow.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.application.workflow.worker.support.DocumentChecklists;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChecklistTest {

	@Test
	void generalApplicationChecklistHasThreeTypes() {
		assertThat(DocumentChecklists.typesForApplicationType("General"))
				.containsExactly("ID", "Income", "PropertyAssessment");
	}

	@Test
	void demoChecklistCsvLimitsToThreeDistinctTypes() {
		List<String> twoTypes = new ArrayList<>();
		twoTypes.add("ID");
		twoTypes.add("Income");
		assertThat(DocumentChecklists.normalizeDemoChecklist(twoTypes))
				.containsExactly("ID", "Income");
		assertThat(DocumentChecklists.resolveRequiredDocuments("General", "ID,PropertyAssessment"))
				.containsExactly("ID", "PropertyAssessment");
	}

	@Test
	void recordDocumentVerifiedRemovesFromPending() {
		var worker = new RecordDocumentVerifiedWorker();
		List<String> initialPending = new ArrayList<>();
		initialPending.add("ID");
		initialPending.add("Income");
		initialPending.add("PropertyAssessment");
		var result = worker.handle("APP-1", "Income", "DOC-1", initialPending, new ArrayList<>());
		@SuppressWarnings("unchecked")
		List<String> pending = (List<String>) result.get("pendingDocuments");
		@SuppressWarnings("unchecked")
		List<String> verified = (List<String>) result.get("verifiedDocuments");
		assertThat(pending).containsExactly("ID", "PropertyAssessment");
		assertThat(verified).containsExactly("Income");
	}

}
