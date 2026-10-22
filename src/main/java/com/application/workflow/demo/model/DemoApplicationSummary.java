package com.application.workflow.demo.model;

import java.time.Instant;

public record DemoApplicationSummary(
		String applicationId,
		long processInstanceKey,
		String applicationType,
		DemoFlowProfile flowProfile,
		String startedBy,
		Instant startedAt) {
}
