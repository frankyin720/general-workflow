package com.mortgage.workflow.demo.web;

import com.mortgage.workflow.demo.auth.DemoSession;
import com.mortgage.workflow.demo.auth.DemoSessionService;
import com.mortgage.workflow.demo.auth.DemoUser;
import com.mortgage.workflow.demo.auth.DemoUserRegistry;
import com.mortgage.workflow.demo.config.DemoProperties;
import com.mortgage.workflow.demo.web.dto.DemoLoginRequest;
import com.mortgage.workflow.demo.web.dto.DemoLoginResponse;
import com.mortgage.workflow.demo.web.dto.DemoPersonaView;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/demo/auth")
@ConditionalOnProperty(prefix = "application-workflow.demo", name = "enabled", havingValue = "true")
public class DemoAuthController {

	public static final String SESSION_HEADER = "X-Demo-Session";

	private final DemoUserRegistry userRegistry;
	private final DemoSessionService sessionService;
	private final DemoProperties demoProperties;

	public DemoAuthController(
			DemoUserRegistry userRegistry,
			DemoSessionService sessionService,
			DemoProperties demoProperties) {
		this.userRegistry = userRegistry;
		this.sessionService = sessionService;
		this.demoProperties = demoProperties;
	}

	@PostMapping("/conductor")
	public DemoLoginResponse conductorLogin() {
		if (!demoProperties.conductorMode()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conductor mode is disabled");
		}
		DemoSession session = sessionService.openConductorSession();
		return toResponse(session);
	}

	@GetMapping("/personas")
	public List<DemoPersonaView> personas() {
		return userRegistry.all().stream()
				.map(user -> new DemoPersonaView(
						user.username(),
						user.displayName(),
						user.role(),
						"Password: demo"))
				.toList();
	}

	@PostMapping("/login")
	public DemoLoginResponse login(@RequestBody DemoLoginRequest request) {
		return sessionService.login(request.username(), request.password())
				.map(this::toResponse)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestHeader(name = SESSION_HEADER, required = false) String token) {
		sessionService.logout(token);
	}

	@GetMapping("/me")
	public DemoLoginResponse me(@RequestHeader(name = SESSION_HEADER, required = false) String token) {
		DemoUser user = sessionService.resolveUser(token)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in"));
		return new DemoLoginResponse(token, user.username(), user.displayName(), user.role());
	}

	private DemoLoginResponse toResponse(DemoSession session) {
		DemoUser user = userRegistry.find(session.username()).orElseThrow();
		return new DemoLoginResponse(session.token(), user.username(), user.displayName(), user.role());
	}
}
