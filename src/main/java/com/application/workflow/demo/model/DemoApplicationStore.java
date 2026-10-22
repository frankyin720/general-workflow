package com.application.workflow.demo.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "general-workflow.demo", name = "enabled", havingValue = "true")
public class DemoApplicationStore {

	private static final Logger log = LoggerFactory.getLogger(DemoApplicationStore.class);

	private final Map<String, DemoApplicationSummary> byApplicationId = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper;
	private final Path storeFile;

	public DemoApplicationStore() {
		this.objectMapper = demoStoreObjectMapper();
		this.storeFile = Path.of(System.getProperty("user.home"), ".general-workflow", "demo-applications.json");
		purgeOrphanTempFiles();
		loadFromDisk();
	}

	private static ObjectMapper demoStoreObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
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
				"General",
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
		synchronized (this) {
			Path tempFile = null;
			try {
				Files.createDirectories(storeFile.getParent());
				Map<String, DemoApplicationSummary> snapshot = new LinkedHashMap<>(byApplicationId);
				tempFile = Files.createTempFile(storeFile.getParent(), "demo-applications-", ".json.tmp");
				objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), snapshot);
				moveStoreFileIntoPlace(tempFile);
				tempFile = null;
			} catch (IOException e) {
				log.warn("Could not persist demo applications to {}: {}", storeFile, e.getMessage());
			} finally {
				if (tempFile != null) {
					try {
						Files.deleteIfExists(tempFile);
					} catch (IOException e) {
						log.warn("Could not delete temp demo store file {}: {}", tempFile, e.getMessage());
					}
				}
			}
		}
	}

	private void moveStoreFileIntoPlace(Path tempFile) throws IOException {
		try {
			Files.move(tempFile, storeFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ex) {
			Files.move(tempFile, storeFile, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.deleteIfExists(tempFile);
	}

	private void purgeOrphanTempFiles() {
		Path dir = storeFile.getParent();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (Stream<Path> entries = Files.list(dir)) {
			entries.filter(DemoApplicationStore::isOrphanStoreTempFile).forEach(path -> {
				try {
					Files.deleteIfExists(path);
					log.info("Removed orphan demo store temp file {}", path.getFileName());
				} catch (IOException e) {
					log.warn("Could not remove orphan temp file {}: {}", path, e.getMessage());
				}
			});
		} catch (IOException e) {
			log.warn("Could not scan {} for temp files: {}", dir, e.getMessage());
		}
	}

	private static boolean isOrphanStoreTempFile(Path path) {
		String name = path.getFileName().toString();
		return (name.startsWith("demo-applications-") && name.endsWith(".json.tmp"))
				|| (name.startsWith("t-") && name.endsWith(".tmp"));
	}
}
