package com.mortgage.workflow.web;

import com.mortgage.workflow.service.WorkflowTaskService;
import com.mortgage.workflow.web.dto.CompleteUserTaskRequest;
import com.mortgage.workflow.web.dto.MessageCorrelationResponse;
import com.mortgage.workflow.web.dto.PublishMessageRequest;
import com.mortgage.workflow.web.dto.StartProcessRequest;
import com.mortgage.workflow.web.dto.StartProcessResponse;
import com.mortgage.workflow.web.dto.UserTaskView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {

	private final WorkflowTaskService workflowTaskService;

	public WorkflowController(WorkflowTaskService workflowTaskService) {
		this.workflowTaskService = workflowTaskService;
	}

	@PostMapping("/processes/mortgage_workflow/instances")
	public StartProcessResponse startMortgageWorkflow(@RequestBody(required = false) StartProcessRequest request) {
		StartProcessRequest payload = request != null ? request : new StartProcessRequest(null, null, null, null);
		return workflowTaskService.startMortgageWorkflow(payload);
	}

	@GetMapping("/user-tasks")
	public List<UserTaskView> listOpenUserTasks(@RequestParam(required = false) String assignee) {
		return workflowTaskService.listOpenUserTasks(assignee);
	}

	@PostMapping("/user-tasks/{userTaskKey}/completion")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void completeUserTask(
			@PathVariable long userTaskKey,
			@RequestBody(required = false) CompleteUserTaskRequest request) {
		CompleteUserTaskRequest payload = request != null ? request : new CompleteUserTaskRequest(null, null);
		workflowTaskService.completeUserTask(userTaskKey, payload);
	}

	@PostMapping("/messages/edoc-uploaded")
	public MessageCorrelationResponse correlateEdocUploaded(@RequestBody PublishMessageRequest request) {
		return workflowTaskService.correlateMessage(
				WorkflowTaskService.MESSAGE_EDOC_UPLOADED, request.applicationId());
	}

	@PostMapping("/messages/esign-completed")
	public MessageCorrelationResponse correlateEsignCompleted(@RequestBody PublishMessageRequest request) {
		return workflowTaskService.correlateMessage(
				WorkflowTaskService.MESSAGE_ESIGN_COMPLETED, request.applicationId());
	}

}
