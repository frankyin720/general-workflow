package com.application.workflow.demo.web.dto;

import com.application.workflow.demo.auth.DemoRole;

public record DemoLoginResponse(
		String token,
		String username,
		String displayName,
		DemoRole role) {
}
