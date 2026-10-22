package com.application.workflow.demo.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoApplicationStoreTest {

	@Test
	void savePersistsJsonIncludingInstant(@TempDir Path tempHome) throws Exception {
		System.setProperty("user.home", tempHome.toString());
		try {
			DemoApplicationStore store = new DemoApplicationStore();
			store.save(store.createPlaceholder("APP-TEST-1", "advisor.smith"));
			Path file = tempHome.resolve(".general-workflow").resolve("demo-applications.json");
			assertThat(file).isRegularFile();
			String json = Files.readString(file);
			assertThat(json).contains("APP-TEST-1");
			assertThat(json).contains("startedAt");
			assertThat(json).contains("T"); // ISO-8601 instant
		} finally {
			System.clearProperty("user.home");
		}
	}
}
