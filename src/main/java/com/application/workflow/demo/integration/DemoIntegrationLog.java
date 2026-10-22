package com.application.workflow.demo.integration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Component;

@Component
public class DemoIntegrationLog {

	private static final int MAX = 500;

	private final Deque<DemoIntegrationEvent> events = new ConcurrentLinkedDeque<>();

	public void record(String applicationId, String source, String action, String detail) {
		events.addFirst(new DemoIntegrationEvent(Instant.now(), applicationId, source, action, detail));
		while (events.size() > MAX) {
			events.removeLast();
		}
	}

	public List<DemoIntegrationEvent> recent(int limit) {
		List<DemoIntegrationEvent> copy = new ArrayList<>();
		for (DemoIntegrationEvent event : events) {
			copy.add(event);
			if (copy.size() >= limit) {
				break;
			}
		}
		return Collections.unmodifiableList(copy);
	}
}
