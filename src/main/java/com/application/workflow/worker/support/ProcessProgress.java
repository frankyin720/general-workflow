package com.application.workflow.worker.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;

public final class ProcessProgress {

	public static final String STEP_CREATE_RECORD = "CREATE_RECORD";
	public static final String STEP_EDOCS_VERIFIED = "EDOCS_VERIFIED";
	public static final String STEP_APPROVAL_RECORDED = "APPROVAL_RECORDED";
	public static final String STEP_FUNDING_INITIATED = "FUNDING_INITIATED";

	private ProcessProgress() {
	}

	public static Map<String, Object> append(List<String> completedSteps, String step) {
		List<String> steps = new ArrayList<>(ListUtils.emptyIfNull(completedSteps).stream()
				.filter(Objects::nonNull)
				.toList());
		if (StringUtils.isNotBlank(step) && !steps.contains(step)) {
			steps.add(step);
		}
		return Collections.singletonMap("completedSteps", steps);
	}

	public static boolean includes(List<String> completedSteps, String step) {
		if (StringUtils.isBlank(step)) {
			return false;
		}
		return ListUtils.emptyIfNull(completedSteps).contains(step);
	}

	/**
	 * Demo / REST status may still see legacy CSV {@code completedSteps} from older instances.
	 */
	public static boolean includes(Object completedStepsRaw, String step) {
		return includes(toList(completedStepsRaw), step);
	}

	public static List<String> toList(Object completedStepsRaw) {
		if (Objects.isNull(completedStepsRaw)) {
			return new ArrayList<>();
		}
		if (completedStepsRaw instanceof List<?> list) {
			return new ArrayList<>(ListUtils.emptyIfNull(list).stream()
					.filter(Objects::nonNull)
					.map(Object::toString)
					.map(String::trim)
					.filter(text -> StringUtils.isNotBlank(text))
					.distinct()
					.toList());
		}
		String csv = completedStepsRaw.toString();
		if (StringUtils.isBlank(csv)) {
			return new ArrayList<>();
		}
		return new ArrayList<>(Arrays.stream(csv.split(","))
				.map(StringUtils::trim)
				.filter(text -> StringUtils.isNotBlank(text))
				.distinct()
				.toList());
	}

}
