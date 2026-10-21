package com.mortgage.workflow.demo.web.dto;

import com.mortgage.workflow.demo.auth.DemoRole;

public record DemoLoginResponse(
		String token,
		String username,
		String displayName,
		DemoRole role) {
}
