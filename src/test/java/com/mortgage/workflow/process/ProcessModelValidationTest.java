package com.mortgage.workflow.process;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.annotation.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BoundaryEvent;
import io.camunda.zeebe.model.bpmn.instance.BusinessRuleTask;
import io.camunda.zeebe.model.bpmn.instance.EndEvent;
import io.camunda.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.LinkEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.ParallelGateway;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeCalledDecision;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/**
 * Static consistency checks between the BPMN, the DMN / form resources and the Java job workers.
 * Runs without a Zeebe engine.
 */
class ProcessModelValidationTest {

	private static final Path PROCESS_DIR = Path.of("src/main/resources/process");

	private static BpmnModelInstance model;

	@BeforeAll
	static void loadModel() throws IOException {
		try (InputStream in = Files.newInputStream(PROCESS_DIR.resolve("application-workflow.bpmn"))) {
			model = Bpmn.readModelFromStream(in);
		}
	}

	@Test
	void bpmnIsStructurallyValid() {
		Bpmn.validateModel(model);
		assertThat(model.getModelElementsByType(io.camunda.zeebe.model.bpmn.instance.Process.class))
				.extracting(io.camunda.zeebe.model.bpmn.instance.Process::getId)
				.containsExactly("mortgage_workflow");
	}

	@Test
	void everyServiceTaskTypeHasAJobWorker() {
		Set<String> workerTypes = jobWorkerTypes();
		List<String> missing = new ArrayList<>();
		for (ServiceTask task : model.getModelElementsByType(ServiceTask.class)) {
			ZeebeTaskDefinition def = task.getSingleExtensionElement(ZeebeTaskDefinition.class);
			assertThat(def).as("service task %s has a zeebe:taskDefinition", task.getId()).isNotNull();
			if (!workerTypes.contains(def.getType())) {
				missing.add(task.getId() + " -> " + def.getType());
			}
		}
		assertThat(missing).as("service task types without @JobWorker").isEmpty();
	}

	@Test
	void everyJobWorkerIsUsedByTheProcess() {
		Set<String> used = new HashSet<>();
		for (ServiceTask task : model.getModelElementsByType(ServiceTask.class)) {
			used.add(task.getSingleExtensionElement(ZeebeTaskDefinition.class).getType());
		}
		assertThat(jobWorkerTypes()).as("orphan @JobWorker types").isSubsetOf(used);
	}

	@Test
	void everyCalledDecisionExistsInADmnFile() throws IOException {
		Set<String> decisionIds = new HashSet<>();
		Pattern pattern = Pattern.compile("<decision\\s+id=\"([^\"]+)\"");
		try (Stream<Path> files = Files.list(PROCESS_DIR)) {
			for (Path dmn : files.filter(p -> p.toString().endsWith(".dmn")).toList()) {
				Matcher matcher = pattern.matcher(Files.readString(dmn));
				while (matcher.find()) {
					decisionIds.add(matcher.group(1));
				}
			}
		}
		for (BusinessRuleTask task : model.getModelElementsByType(BusinessRuleTask.class)) {
			ZeebeCalledDecision called = task.getSingleExtensionElement(ZeebeCalledDecision.class);
			assertThat(called).as("business rule task %s has zeebe:calledDecision", task.getId()).isNotNull();
			assertThat(decisionIds).as("decision for %s", task.getId()).contains(called.getDecisionId());
			assertThat(called.getResultVariable()).as("result variable for %s", task.getId()).isNotBlank();
		}
		assertThat(decisionIds).containsExactlyInAnyOrder(
				"pre_screening_eligibility", "debt_service_underwriting", "funding_readiness");
	}

	@Test
	void everyUserTaskHasAFormAndCandidateGroup() throws IOException {
		Set<String> formIds = new HashSet<>();
		ObjectMapper mapper = new ObjectMapper();
		try (Stream<Path> files = Files.list(PROCESS_DIR)) {
			for (Path form : files.filter(p -> p.toString().endsWith(".form")).toList()) {
				JsonNode json = mapper.readTree(form.toFile());
				formIds.add(json.get("id").asText());
			}
		}
		for (UserTask task : model.getModelElementsByType(UserTask.class)) {
			ZeebeFormDefinition form = task.getSingleExtensionElement(ZeebeFormDefinition.class);
			assertThat(form).as("user task %s has a form", task.getId()).isNotNull();
			assertThat(formIds).as("form for %s", task.getId()).contains(form.getFormId());
			var assignment = task.getSingleExtensionElement(
					io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAssignmentDefinition.class);
			assertThat(assignment).as("user task %s has candidate group", task.getId()).isNotNull();
			assertThat(assignment.getCandidateGroups()).isNotBlank();
		}
	}

	@Test
	void everyMessageHasACorrelationKey() {
		var messageEvents = model.getModelElementsByType(MessageEventDefinition.class);
		assertThat(messageEvents).isNotEmpty();
		for (MessageEventDefinition def : messageEvents) {
			var message = def.getMessage();
			assertThat(message).as("message event %s references a message", def.getId()).isNotNull();
			ZeebeSubscription subscription = message.getSingleExtensionElement(ZeebeSubscription.class);
			assertThat(subscription).as("message %s has zeebe:subscription", message.getName()).isNotNull();
			assertThat(subscription.getCorrelationKey()).isEqualTo("=applicationId");
		}
	}

	@Test
	void exclusiveGatewaysWithSeveralOutgoingFlowsHaveConditionsAndADefault() {
		for (ExclusiveGateway gateway : model.getModelElementsByType(ExclusiveGateway.class)) {
			var outgoing = gateway.getOutgoing();
			if (outgoing.size() <= 1) {
				continue;
			}
			SequenceFlow defaultFlow = gateway.getDefault();
			assertThat(defaultFlow).as("gateway %s has a default flow", gateway.getId()).isNotNull();
			for (SequenceFlow flow : outgoing) {
				if (!flow.equals(defaultFlow)) {
					assertThat(flow.getConditionExpression())
							.as("flow %s out of %s has a condition", flow.getId(), gateway.getId())
							.isNotNull();
				}
			}
		}
	}

	/**
	 * A parallel gateway waits for a token on <em>every</em> incoming flow. If two of its incoming
	 * flows come from decisions (exclusive gateways with several outgoing flows) only one token can
	 * ever arrive and the process instance hangs forever. Such alternative paths must be merged by an
	 * exclusive gateway first and only then fanned out by the parallel gateway.
	 */
	@Test
	void parallelGatewaysNeverJoinMutuallyExclusivePaths() {
		List<String> problems = new ArrayList<>();
		for (ParallelGateway gateway : model.getModelElementsByType(ParallelGateway.class)) {
			if (gateway.getIncoming().size() <= 1) {
				continue;
			}
			List<String> decisionSources = gateway.getIncoming().stream()
					.map(SequenceFlow::getSource)
					.filter(source -> source instanceof ExclusiveGateway && source.getOutgoing().size() > 1)
					.map(FlowNode::getId)
					.toList();
			if (!decisionSources.isEmpty()) {
				problems.add(gateway.getId() + " joins flows coming from exclusive decision(s) " + decisionSources
						+ " - the instance would wait forever; merge them with an exclusive gateway first");
			}
		}
		assertThat(problems).as("parallel gateways used as merge of alternative paths").isEmpty();
	}

	@Test
	void noLinkEvents() {
		// Zeebe rejects deployments when link throw/catch pairs are used for path consolidation.
		assertThat(model.getModelElementsByType(LinkEventDefinition.class)).isEmpty();
	}

	@Test
	void everyFlowNodeIsConnected() {
		List<String> problems = new ArrayList<>();
		for (FlowNode node : model.getModelElementsByType(FlowNode.class)) {
			if (node instanceof SubProcess sub && sub.triggeredByEvent()) {
				continue; // event sub-processes are triggered, not wired
			}
			boolean needsIncoming = !(node instanceof StartEvent) && !(node instanceof BoundaryEvent);
			boolean needsOutgoing = !(node instanceof EndEvent);
			if (needsIncoming && node.getIncoming().isEmpty()) {
				problems.add(node.getId() + " has no incoming flow");
			}
			if (needsOutgoing && node.getOutgoing().isEmpty()) {
				problems.add(node.getId() + " has no outgoing flow");
			}
		}
		assertThat(problems).isEmpty();
	}

	@Test
	void everyTimeoutAndErrorHasAHandler() {
		// Every boundary event must lead somewhere (manual handling / escalation / cancel).
		for (BoundaryEvent boundary : model.getModelElementsByType(BoundaryEvent.class)) {
			assertThat(boundary.getOutgoing()).as("boundary event %s has a follow-up", boundary.getId()).isNotEmpty();
		}
		// Every external wait (message catch) sits in an event-based gateway or a sub-process with an expiry timer.
		for (IntermediateCatchEvent catchEvent : model.getModelElementsByType(IntermediateCatchEvent.class)) {
			boolean isMessage = catchEvent.getEventDefinitions().stream().anyMatch(MessageEventDefinition.class::isInstance);
			if (!isMessage) {
				continue;
			}
			boolean inEventBasedGateway = catchEvent.getIncoming().stream()
					.anyMatch(flow -> flow.getSource() instanceof io.camunda.zeebe.model.bpmn.instance.EventBasedGateway);
			boolean inGuardedSubProcess = catchEvent.getParentElement() instanceof SubProcess sub
					&& model.getModelElementsByType(BoundaryEvent.class).stream()
							.anyMatch(b -> b.getAttachedTo() != null && b.getAttachedTo().getId().equals(sub.getId()));
			assertThat(inEventBasedGateway || inGuardedSubProcess)
					.as("message wait %s is protected by a timeout", catchEvent.getId())
					.isTrue();
		}
	}

	@Test
	void bpmnHasDiagramInformation() throws IOException {
		String xml = Files.readString(PROCESS_DIR.resolve("application-workflow.bpmn"));
		assertThat(xml).contains("<bpmndi:BPMNDiagram");
		long shapes = Pattern.compile("<bpmndi:BPMNShape ").matcher(xml).results().count();
		long nodes = model.getModelElementsByType(FlowNode.class).size();
		assertThat(shapes).as("every flow node has a shape").isGreaterThanOrEqualTo(nodes);
	}

	private static Set<String> jobWorkerTypes() {
		var scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));
		Set<String> types = new HashSet<>();
		for (var candidate : scanner.findCandidateComponents("com.mortgage.workflow.handler")) {
			try {
				Class<?> clazz = Class.forName(candidate.getBeanClassName());
				for (Method method : clazz.getDeclaredMethods()) {
					JobWorker worker = method.getAnnotation(JobWorker.class);
					if (worker != null) {
						types.add(worker.type());
					}
				}
			} catch (ClassNotFoundException e) {
				throw new IllegalStateException(e);
			}
		}
		return types;
	}

}
