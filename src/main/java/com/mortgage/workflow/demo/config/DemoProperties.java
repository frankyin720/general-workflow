package com.mortgage.workflow.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mortgage-workflow.demo")
public record DemoProperties(boolean enabled, boolean conductorMode) {
}
