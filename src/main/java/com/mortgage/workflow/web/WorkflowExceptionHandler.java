package com.mortgage.workflow.web;

import io.camunda.client.api.command.ProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class WorkflowExceptionHandler {

	@ExceptionHandler(ResponseStatusException.class)
	public ProblemDetail handleResponseStatus(ResponseStatusException exception) {
		ProblemDetail detail = ProblemDetail.forStatus(exception.getStatusCode());
		detail.setTitle(exception.getStatusCode().toString());
		if (exception.getReason() != null) {
			detail.setDetail(exception.getReason());
		}
		return detail;
	}

	@ExceptionHandler(ProblemException.class)
	public ProblemDetail handleCamundaProblem(ProblemException exception) {
		int code = exception.code();
		HttpStatus status;
		if (code >= 400 && code < 600) {
			status = HttpStatus.valueOf(code);
		} else {
			status = HttpStatus.BAD_GATEWAY;
		}
		ProblemDetail detail = ProblemDetail.forStatus(status);
		detail.setTitle("Camunda request failed");
		String message = exception.getMessage();
		if (code == 404) {
			message = message + " (Is application-workflow deployed? Start c8run and restart map.)";
		} else if (code == 503 || code == 502) {
			message = message + " (Is Camunda reachable at camunda.client.grpc-address / rest-address?)";
		}
		detail.setDetail(message);
		return detail;
	}

}
