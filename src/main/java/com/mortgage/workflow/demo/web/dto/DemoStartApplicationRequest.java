package com.mortgage.workflow.demo.web.dto;

import com.mortgage.workflow.demo.model.DemoFlowProfile;

public record DemoStartApplicationRequest(
		String applicationType,
		DemoFlowProfile flowProfile,
		String customerName) {
}
