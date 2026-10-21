package com.mortgage.workflow;

import com.mortgage.workflow.integration.camunda.CamundaMessageBridge;
import io.camunda.client.CamundaClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class ApplicationWorkflowApplicationTests {

	@MockitoBean
	private CamundaClient camundaClient;

	@MockitoBean
	private CamundaMessageBridge camundaMessageBridge;

	@Test
	void contextLoads() {
	}

}
