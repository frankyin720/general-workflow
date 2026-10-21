package com.mortgage.workflow.demo.auth;

public record DemoUser(
		String username,
		String password,
		String displayName,
		DemoRole role,
		String camundaGroup) {
}
