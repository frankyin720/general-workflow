package com.mortgage.workflow.handler;

import com.mortgage.workflow.handler.support.IntegrationErrorCodes;
import com.mortgage.workflow.handler.support.IntegrationJobFailures;
import com.mortgage.workflow.handler.support.IntegrationScenario;
import com.mortgage.workflow.handler.support.ProcessProgress;
import com.mortgage.workflow.handler.support.Variables;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;
import io.camunda.client.exception.CamundaError;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Phase 4 — commitment letter and legal instruction workers. */
@Component
public class CommitmentHandlers {

	private static final Logger log = LoggerFactory.getLogger(CommitmentHandlers.class);

	@JobWorker(type = "generateMortgageCommitmentLetter")
	public Map<String, Object> generateCommitmentLetter(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) Integer commitmentVersion,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String completedSteps) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Document generation service unavailable",
					Map.of("integrationPoint", "generateMortgageCommitmentLetter"));
		}
		// Only the first attempt fails in the simulated scenario; operations can retry successfully.
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.COMMITMENT_GENERATION_FAILED)
				&& (commitmentVersion == null || commitmentVersion == 0)) {
			throw CamundaError.bpmnError(
					IntegrationErrorCodes.DOCUMENT_GENERATION_FAILED,
					"Commitment letter template rendering failed",
					Map.of("commitmentVersion", 1, "documentGenerationError", "Template placeholder missing"));
		}
		int version = Variables.or(commitmentVersion, 0) + 1;
		Instant expiry = Instant.now().plus(14, ChronoUnit.DAYS);
		log.info("Commitment letter generated applicationId={} version={}", applicationId, version);
		Map<String, Object> variables = new HashMap<>();
		variables.put("commitmentLetterId", "CL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		variables.put("commitmentVersion", version);
		variables.put("commitmentExpiresAt", expiry.toString());
		variables.put("applicationStatus", "COMMITMENT_ISSUED");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_COMMITMENT_LETTER));
		return variables;
	}

	@JobWorker(type = "sendCommitmentLetterForEsign")
	public Map<String, Object> sendCommitmentLetterForEsign(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String commitmentLetterId,
			@Variable(optional = true) String completedSteps) {
		String envelopeId = "ENV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		log.info("Commitment letter sent for eSign applicationId={} letter={} envelope={}",
				applicationId, commitmentLetterId, envelopeId);
		Map<String, Object> variables = new HashMap<>();
		variables.put("eSignEnvelopeId", envelopeId);
		variables.put("eSignStatus", "SENT");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_COMMITMENT_SENT));
		return variables;
	}

	@JobWorker(type = "recordClientDeclined")
	public Map<String, Object> recordClientDeclined(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String clientDeclineReason) {
		log.info("Client declined commitment applicationId={} reason={}", applicationId, clientDeclineReason);
		Map<String, Object> variables = new HashMap<>();
		variables.put("applicationStatus", "CLIENT_DECLINED");
		variables.put("eSignStatus", "DECLINED");
		variables.put("clientDeclineReason", Variables.or(clientDeclineReason, "Client declined the offer"));
		return variables;
	}

	@JobWorker(type = "sendSolicitorInstructions")
	public Map<String, Object> sendSolicitorInstructions(
			@Variable(optional = true) String applicationId,
			@Variable(optional = true) String integrationScenario,
			@Variable(optional = true) String completedSteps) {
		if (IntegrationScenario.is(integrationScenario, IntegrationScenario.TECHNICAL_FAILURE)) {
			throw IntegrationJobFailures.technicalFailure(
					"Legal instruction portal unavailable", Map.of("integrationPoint", "sendSolicitorInstructions"));
		}
		log.info("Solicitor / notary instructions sent applicationId={}", applicationId);
		Map<String, Object> variables = new HashMap<>();
		variables.put("solicitorInstructionsSent", true);
		variables.put("solicitorInstructionId", "SOL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
		variables.put("applicationStatus", "INSTRUCTED");
		variables.putAll(ProcessProgress.append(completedSteps, ProcessProgress.STEP_SOLICITOR_INSTRUCTIONS));
		return variables;
	}

}
