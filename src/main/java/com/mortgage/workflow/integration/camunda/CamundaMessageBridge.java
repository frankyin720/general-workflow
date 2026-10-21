package com.mortgage.workflow.integration.camunda;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Correlates external events to mortgage-workflow message catch events; buffers when no subscription is open yet.
 */
@Service
public class CamundaMessageBridge {

	private static final Logger log = LoggerFactory.getLogger(CamundaMessageBridge.class);
	private static final Duration DEFAULT_BUFFER_TTL = Duration.ofHours(1);

	private final CamundaClient camundaClient;

	public CamundaMessageBridge(CamundaClient camundaClient) {
		this.camundaClient = camundaClient;
	}

	public void correlateOrBuffer(String messageName, String correlationKey, Map<String, Object> variables) {
		if (correlationKey == null || correlationKey.isBlank()) {
			throw new IllegalArgumentException("correlationKey (applicationId) is required");
		}
		try {
			var command = camundaClient.newCorrelateMessageCommand()
					.messageName(messageName)
					.correlationKey(correlationKey);
			if (variables != null && !variables.isEmpty()) {
				command = command.variables(variables);
			}
			var response = command.execute();
			log.info(
					"Correlated message messageName={} correlationKey={} processInstanceKey={}",
					messageName,
					correlationKey,
					response.getProcessInstanceKey());
		} catch (ProblemException exception) {
			if (exception.code() == 404) {
				log.info(
						"No active subscription for messageName={} correlationKey={}, publishing with TTL buffer",
						messageName,
						correlationKey);
				publishWithBuffer(messageName, correlationKey, variables, DEFAULT_BUFFER_TTL);
				return;
			}
			throw exception;
		}
	}

	private void publishWithBuffer(
			String messageName,
			String correlationKey,
			Map<String, Object> variables,
			Duration timeToLive) {
		var command = camundaClient.newPublishMessageCommand()
				.messageName(messageName)
				.correlationKey(correlationKey)
				.timeToLive(timeToLive);
		if (variables != null && !variables.isEmpty()) {
			command = command.variables(variables);
		}
		command.execute();
	}

}
