package com.application.workflow.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "general-workflow.demo")
public record DemoProperties(boolean enabled, boolean conductorMode) {
}
