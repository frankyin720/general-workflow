package com.application.workflow.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.application.workflow.worker.support.ProcessProgress;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessProgressTest {

	@Test
	void appendDedupesWithListContains() {
		var first = ProcessProgress.append(new ArrayList<>(), ProcessProgress.STEP_CREATE_RECORD);
		@SuppressWarnings("unchecked")
		List<String> steps = (List<String>) first.get("completedSteps");
		var second = ProcessProgress.append(steps, ProcessProgress.STEP_CREATE_RECORD);
		@SuppressWarnings("unchecked")
		List<String> afterDuplicate = (List<String>) second.get("completedSteps");
		assertThat(afterDuplicate).containsExactly(ProcessProgress.STEP_CREATE_RECORD);

		var third = ProcessProgress.append(afterDuplicate, ProcessProgress.STEP_EDOCS_VERIFIED);
		@SuppressWarnings("unchecked")
		List<String> merged = (List<String>) third.get("completedSteps");
		assertThat(merged).containsExactly(
				ProcessProgress.STEP_CREATE_RECORD, ProcessProgress.STEP_EDOCS_VERIFIED);
	}

	@Test
	void includesReadsLegacyCsv() {
		assertThat(ProcessProgress.includes("CREATE_RECORD,EDOCS_VERIFIED", ProcessProgress.STEP_EDOCS_VERIFIED))
				.isTrue();
		List<String> fundingSteps = new ArrayList<>();
		fundingSteps.add(ProcessProgress.STEP_FUNDING_INITIATED);
		assertThat(ProcessProgress.includes(fundingSteps, ProcessProgress.STEP_FUNDING_INITIATED))
				.isTrue();
	}

}
