package com.application.workflow.demo.web.dto;

import com.application.workflow.demo.model.DemoFlowProfile;
import java.util.List;

public record DemoStartApplicationRequest(
		String applicationType,
		DemoFlowProfile flowProfile,
		String customerName,
		List<String> requiredDocuments) {
}
