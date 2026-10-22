package com.application.workflow.demo.web;

import java.util.Objects;
import com.application.workflow.demo.auth.DemoSessionService;
import com.application.workflow.demo.auth.DemoUser;
import com.application.workflow.demo.auth.DemoUserRegistry;
import com.application.workflow.demo.config.DemoProperties;
import com.application.workflow.demo.integration.DemoIntegrationEvent;
import com.application.workflow.demo.integration.DemoIntegrationLog;
import com.application.workflow.demo.model.DemoApplicationSummary;
import com.application.workflow.demo.model.DemoFlowProfile;
import com.application.workflow.demo.service.DemoPortalService;
import com.application.workflow.demo.web.dto.DemoApplicationStatusView;
import com.application.workflow.demo.web.dto.DemoEdocUploadRequest;
import com.application.workflow.demo.web.dto.DemoStartApplicationRequest;
import com.application.workflow.demo.web.dto.DemoUnderwritingDecisionRequest;
import com.application.workflow.web.dto.UserTaskView;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(prefix = "general-workflow.demo", name = "enabled", havingValue = "true")
public class DemoPortalController {

	private final DemoSessionService sessionService;
	private final DemoUserRegistry userRegistry;
	private final DemoProperties demoProperties;
	private final DemoPortalService demoPortalService;
	private final DemoIntegrationLog integrationLog;

	public DemoPortalController(
			DemoSessionService sessionService,
			DemoUserRegistry userRegistry,
			DemoProperties demoProperties,
			DemoPortalService demoPortalService,
			DemoIntegrationLog integrationLog) {
		this.sessionService = sessionService;
		this.userRegistry = userRegistry;
		this.demoProperties = demoProperties;
		this.demoPortalService = demoPortalService;
		this.integrationLog = integrationLog;
	}

	@PostMapping("/applications")
	public DemoApplicationSummary startApplication(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER, required = false) String token,
			@RequestBody(required = false) DemoStartApplicationRequest request) {
		return demoPortalService.startApplication(
				currentUser(token),
				Objects.nonNull(request)
						? request
						: new DemoStartApplicationRequest("", DemoFlowProfile.HAPPY_AUTO, "", new ArrayList<>()));
	}

	@GetMapping("/applications")
	public List<DemoApplicationSummary> listApplications(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER) String token) {
		return demoPortalService.listApplications(currentUser(token));
	}

	@GetMapping("/applications/{applicationId}/status")
	public DemoApplicationStatusView status(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER, required = false) String token,
			@PathVariable String applicationId) {
		return demoPortalService.getStatus(currentUser(token), applicationId);
	}

	@PostMapping("/applications/{applicationId}/edoc-upload")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void edocUpload(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER, required = false) String token,
			@PathVariable String applicationId,
			@RequestBody(required = false) DemoEdocUploadRequest request) {
		demoPortalService.simulateEdocUpload(
				currentUser(token),
				applicationId,
				Objects.nonNull(request) ? request : new DemoEdocUploadRequest("", "", ""));
	}

	@PostMapping("/applications/{applicationId}/esign-complete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void esignComplete(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER, required = false) String token,
			@PathVariable String applicationId) {
		demoPortalService.simulateEsignComplete(currentUser(token), applicationId);
	}

	@GetMapping("/underwriting/tasks")
	public List<UserTaskView> underwritingTasks(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER) String token) {
		return demoPortalService.listUnderwritingTasks(currentUser(token));
	}

	@PostMapping("/underwriting/tasks/{userTaskKey}/complete")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void completeUnderwriting(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER, required = false) String token,
			@PathVariable long userTaskKey,
			@RequestBody DemoUnderwritingDecisionRequest request) {
		demoPortalService.completeUnderwritingTask(currentUser(token), userTaskKey, request);
	}

	@GetMapping("/integration-events")
	public List<DemoIntegrationEvent> integrationEvents(
			@RequestHeader(name = DemoAuthController.SESSION_HEADER, required = false) String token,
			@RequestParam(defaultValue = "50") int limit) {
		currentUser(token);
		return integrationLog.recent(Math.min(limit, 200));
	}

	private DemoUser currentUser(String token) {
		if (StringUtils.isNotBlank(token)) {
			var resolved = sessionService.resolveUser(token);
			if (resolved.isPresent()) {
				return resolved.get();
			}
		}
		if (demoProperties.conductorMode()) {
			return userRegistry.find("demo.conductor")
					.orElseThrow(() -> new ResponseStatusException(
							HttpStatus.UNAUTHORIZED, "Demo conductor user is not configured"));
		}
		throw new ResponseStatusException(
				HttpStatus.UNAUTHORIZED,
				"Missing or expired X-Demo-Session header — reload the page to sign in");
	}
}
