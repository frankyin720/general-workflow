package com.mortgage.workflow.handler.support;

/** Small null-safe helpers for optional process variables. */
public final class Variables {

	private Variables() {
	}

	public static double or(Double value, double fallback) {
		return value != null ? value : fallback;
	}

	public static int or(Integer value, int fallback) {
		return value != null ? value : fallback;
	}

	public static boolean or(Boolean value, boolean fallback) {
		return value != null ? value : fallback;
	}

	public static String or(String value, String fallback) {
		return value != null && !value.isBlank() ? value : fallback;
	}

}
