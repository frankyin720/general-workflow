package com.mortgage.workflow.demo.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DemoUserRegistry {

	private final Map<String, DemoUser> byUsername;

	public DemoUserRegistry() {
		List<DemoUser> users = List.of(
				new DemoUser("advisor.smith", "demo", "Alex Smith", DemoRole.ADVISOR, "Advisor"),
				new DemoUser("customer.lee", "demo", "Jamie Lee", DemoRole.CUSTOMER, null),
				new DemoUser("underwriter.jones", "demo", "Taylor Jones", DemoRole.UNDERWRITER,
						DemoRole.UNDERWRITER_CAMUNDA_GROUP),
				new DemoUser("compliance.khan", "demo", "Priya Khan", DemoRole.COMPLIANCE, "Compliance"),
				new DemoUser("ecm.bot", "demo", "ECM Integration Bot", DemoRole.ECM_SYSTEM, null),
				new DemoUser("ops.admin", "demo", "Ops Admin", DemoRole.OPERATIONS, "Operations"),
				new DemoUser("demo.conductor", "demo", "Demo Conductor", DemoRole.CONDUCTOR,
						DemoRole.UNDERWRITER_CAMUNDA_GROUP));
		this.byUsername = users.stream().collect(Collectors.toMap(DemoUser::username, Function.identity()));
	}

	public Optional<DemoUser> authenticate(String username, String password) {
		DemoUser user = byUsername.get(username);
		if (user == null || !user.password().equals(password)) {
			return Optional.empty();
		}
		return Optional.of(user);
	}

	public Optional<DemoUser> find(String username) {
		return Optional.ofNullable(byUsername.get(username));
	}

	public Collection<DemoUser> all() {
		return byUsername.values();
	}
}
