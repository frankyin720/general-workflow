package com.mortgage.workflow.demo.web.dto;

import com.mortgage.workflow.demo.auth.DemoRole;

public record DemoPersonaView(String username, String displayName, DemoRole role, String hint) {
}
