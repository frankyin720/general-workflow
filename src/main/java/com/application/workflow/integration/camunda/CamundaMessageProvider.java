package com.application.workflow.integration.camunda;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class CamundaMessageProvider {
    private static final Logger log =LoggerFactory.getLogger(CamundaMessageProvider.class);
    private static final Duration TIME_TO_LIVE = Duration.ofHours(1);

    private final CamundaClient camundaClient;

    public CamundaMessageProvider(CamundaClient camundaClient) {
        this.camundaClient = camundaClient;
    }

    public CorrelationResult correlateOrBuffer(String messageName, String correlationKey, Map<String, Object> variables) {
        var command = this.camundaClient.newCorrelateMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey);
        if (MapUtils.isNotEmpty(variables)) {
            command.variables(variables);
        }

        try {
            var response = command.execute();
            long processInstanceKey = response.getProcessInstanceKey();
            log.info("Corrlated message: messageName = {}, correlationKey = {}, processInstanceKey = {}", messageName, correlationKey, processInstanceKey);
            return new CorrelationResult(MessageCorrelationOutcome.CORRELATED, processInstanceKey);
        } catch (ProblemException e) {
            if (e.code() == 404) {
                log.warn("No active subscriber for message: messageName = {}, correlationKey = {} — buffering", messageName, correlationKey);
                publishWithBuffer(messageName, correlationKey, variables, TIME_TO_LIVE);
                return new CorrelationResult(MessageCorrelationOutcome.BUFFERED, 0L);
            }
            throw e;
        }

    }

    public void publishWithBuffer(String messageName, String correlationKey, Map<String, Object> variables, Duration timeToLive) {

        var command = this.camundaClient.newPublishMessageCommand()
                .messageName(messageName)
                .correlationKey(correlationKey)
                .timeToLive(timeToLive);
        if (MapUtils.isNotEmpty(variables)) {
            command.variables(variables);
        }
        command.execute();
    }
}

