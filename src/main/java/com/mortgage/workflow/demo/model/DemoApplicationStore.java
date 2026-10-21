package com.mortgage.workflow.demo.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class DemoApplicationStore {

	private final Map<String, DemoApplicationSummary> byApplicationId = new ConcurrentHashMap<>();

	public void save(DemoApplicationSummary summary) {
		byApplicationId.put(summary.applicationId(), summary);
	}

	public Optional<DemoApplicationSummary> find(String applicationId) {
		return Optional.ofNullable(byApplicationId.get(applicationId));
	}

	public List<DemoApplicationSummary> listForUser(String username, boolean viewAll) {
		List<DemoApplicationSummary> results = new ArrayList<>();
		for (DemoApplicationSummary summary : byApplicationId.values()) {
			if (viewAll || username.equals(summary.startedBy())) {
				results.add(summary);
			}
		}
		results.sort((a, b) -> b.startedAt().compareTo(a.startedAt()));
		return results;
	}

	public DemoApplicationSummary createPlaceholder(String applicationId, String startedBy) {
		return new DemoApplicationSummary(
				applicationId,
				0L,
				"Mortgage",
				DemoFlowProfile.HAPPY_AUTO,
				startedBy,
				Instant.now());
	}
}
