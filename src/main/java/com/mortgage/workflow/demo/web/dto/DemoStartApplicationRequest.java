package com.mortgage.workflow.demo.web.dto;

import com.mortgage.workflow.demo.model.DemoFlowProfile;
import java.util.Map;

public record DemoStartApplicationRequest(
		String applicationType,
		DemoFlowProfile flowProfile,
		String customerName,
		Map<String, Object> financials) {

	public DemoStartApplicationRequest(String applicationType, DemoFlowProfile flowProfile, String customerName) {
		this(applicationType, flowProfile, customerName, null);
	}
}
