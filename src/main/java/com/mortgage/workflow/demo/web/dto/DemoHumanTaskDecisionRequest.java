package com.mortgage.workflow.demo.web.dto;

import java.util.Map;

/**
 * Generic completion payload for any human task in the mortgage workflow. {@code variables} holds
 * the decision variable(s) the task's outgoing gateway expects, e.g. {@code underwritingDecision},
 * {@code complianceDecision}, {@code documentValid}, {@code documentExpiryDecision}, ...
 */
public record DemoHumanTaskDecisionRequest(Map<String, Object> variables, String comment) {
}
