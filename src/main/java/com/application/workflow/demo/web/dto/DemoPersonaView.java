package com.application.workflow.demo.web.dto;

import com.application.workflow.demo.auth.DemoRole;

public record DemoPersonaView(String username, String displayName, DemoRole role, String hint) {
}
