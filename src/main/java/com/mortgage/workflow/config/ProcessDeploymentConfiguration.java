package com.mortgage.workflow.config;

import io.camunda.client.annotation.Deployment;
import org.springframework.context.annotation.Configuration;

@Configuration
@Deployment(resources = {
		"classpath*:/process/**/*.bpmn",
		"classpath*:/process/**/*.dmn",
		"classpath*:/process/**/*.form"
})
public class ProcessDeploymentConfiguration {
}
