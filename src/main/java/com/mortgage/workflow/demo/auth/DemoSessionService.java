package com.mortgage.workflow.demo.auth;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class DemoSessionService {

	private final DemoUserRegistry userRegistry;
	private final Map<String, DemoSession> sessions = new ConcurrentHashMap<>();

	public DemoSessionService(DemoUserRegistry userRegistry) {
		this.userRegistry = userRegistry;
	}

	public Optional<DemoSession> login(String username, String password) {
		return userRegistry.authenticate(username, password).map(user -> {
			DemoSession session = new DemoSession(UUID.randomUUID().toString(), user.username(), Instant.now());
			sessions.put(session.token(), session);
			return session;
		});
	}

	public void logout(String token) {
		if (token != null) {
			sessions.remove(token);
		}
	}

	public DemoSession openConductorSession() {
		DemoSession session = new DemoSession(UUID.randomUUID().toString(), "demo.conductor", Instant.now());
		sessions.put(session.token(), session);
		return session;
	}

	public Optional<DemoUser> resolveUser(String token) {
		if (token == null || token.isBlank()) {
			return Optional.empty();
		}
		DemoSession session = sessions.get(token);
		if (session == null) {
			return Optional.empty();
		}
		return userRegistry.find(session.username());
	}
}
