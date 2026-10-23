package com.application.workflow;

import com.application.workflow.integration.config.IntegrationConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(IntegrationConfig.class)
public class ApplicationWorkflowApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApplicationWorkflowApplication.class, args);
	}

}
