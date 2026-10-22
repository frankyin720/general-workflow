package com.application.workflow.demo.auth;

import java.util.Objects;
import java.util.ArrayList;
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
		List<DemoUser> users = new ArrayList<>();
		users.add(new DemoUser("advisor.smith", "demo", "Alex Smith", DemoRole.ADVISOR, ""));
		users.add(new DemoUser("customer.lee", "demo", "Jamie Lee", DemoRole.CUSTOMER, ""));
		users.add(new DemoUser(
				"underwriter.jones",
				"demo",
				"Taylor Jones",
				DemoRole.UNDERWRITER,
				DemoRole.UNDERWRITER_CAMUNDA_GROUP));
		users.add(new DemoUser("ecm.bot", "demo", "ECM Integration Bot", DemoRole.ECM_SYSTEM, ""));
		users.add(new DemoUser("ops.admin", "demo", "Ops Admin", DemoRole.OPERATIONS, ""));
		users.add(new DemoUser(
				"demo.conductor",
				"demo",
				"Demo Conductor",
				DemoRole.CONDUCTOR,
				DemoRole.UNDERWRITER_CAMUNDA_GROUP));
		this.byUsername = users.stream().collect(Collectors.toMap(DemoUser::username, Function.identity()));
	}

	public Optional<DemoUser> authenticate(String username, String password) {
		DemoUser user = byUsername.get(username);
		if (Objects.isNull(user) || !user.password().equals(password)) {
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
