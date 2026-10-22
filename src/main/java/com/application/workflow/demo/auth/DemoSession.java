package com.application.workflow.demo.auth;

import java.time.Instant;

public record DemoSession(String token, String username, Instant createdAt) {
}
