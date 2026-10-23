package com.application.workflow;

import com.application.workflow.integration.camunda.CamundaMessageProvider;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApplicationWorkflowApplicationTests {

	@MockitoBean
	private CamundaClient camundaClient;

	@MockitoBean
	private CamundaMessageProvider camundaMessageProvider;

	@Test
	void contextLoads() {
	}

}
