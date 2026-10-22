package com.application.workflow.demo.model;

public enum DemoFlowProfile {
	HAPPY_AUTO(720, "Auto-approve path (credit score 720)"),
	MANUAL_UNDERWRITING(640, "Manual underwriting (credit score 640)");

	private final int demoCreditScore;
	private final String description;

	DemoFlowProfile(int demoCreditScore, String description) {
		this.demoCreditScore = demoCreditScore;
		this.description = description;
	}

	public int demoCreditScore() {
		return demoCreditScore;
	}

	public String description() {
		return description;
	}
}
