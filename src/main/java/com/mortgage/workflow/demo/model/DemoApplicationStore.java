package com.mortgage.workflow.demo.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application-workflow.demo", name = "enabled", havingValue = "true")
public class DemoApplicationStore {

	private static final Logger log = LoggerFactory.getLogger(DemoApplicationStore.class);

	private final Map<String, DemoApplicationSummary> byApplicationId = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper;
	private final Path storeFile;

	public DemoApplicationStore() {
		this.objectMapper = new ObjectMapper().findAndRegisterModules();
		this.storeFile = Path.of(System.getProperty("user.home"), ".application-workflow", "demo-applications.json");
		loadFromDisk();
	}

	public void save(DemoApplicationSummary summary) {
		byApplicationId.put(summary.applicationId(), summary);
		persistToDisk();
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

	private void loadFromDisk() {
		if (!Files.isRegularFile(storeFile)) {
			return;
		}
		try {
			byte[] bytes = Files.readAllBytes(storeFile);
			if (bytes.length == 0) {
				return;
			}
			Map<String, DemoApplicationSummary> loaded = objectMapper.readValue(bytes, new TypeReference<>() {});
			byApplicationId.putAll(loaded);
			log.info("Loaded {} demo application(s) from {}", byApplicationId.size(), storeFile);
		} catch (IOException e) {
			log.warn("Could not load demo applications from {}: {}", storeFile, e.getMessage());
		}
	}

	private void persistToDisk() {
		try {
			Files.createDirectories(storeFile.getParent());
			Map<String, DemoApplicationSummary> snapshot = new LinkedHashMap<>(byApplicationId);
			objectMapper.writerWithDefaultPrettyPrinter().writeValue(storeFile.toFile(), snapshot);
		} catch (IOException e) {
			log.warn("Could not persist demo applications to {}: {}", storeFile, e.getMessage());
		}
	}
}
