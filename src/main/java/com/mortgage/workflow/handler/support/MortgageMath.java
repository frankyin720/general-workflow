package com.mortgage.workflow.handler.support;

/** Canadian mortgage arithmetic used by the GDS/TDS and LTV workers. */
public final class MortgageMath {

	private MortgageMath() {
	}

	/** Monthly payment with semi-annual compounding (Canadian convention). */
	public static double monthlyPayment(double principal, double annualRatePercent, int amortizationYears) {
		if (principal <= 0) {
			return 0;
		}
		double semiAnnual = annualRatePercent / 100.0 / 2.0;
		double monthlyRate = Math.pow(1 + semiAnnual, 2.0 / 12.0) - 1;
		int months = amortizationYears * 12;
		if (monthlyRate == 0) {
			return principal / months;
		}
		return principal * monthlyRate / (1 - Math.pow(1 + monthlyRate, -months));
	}

	public static double ratioPercent(double monthlyObligations, double annualIncome) {
		if (annualIncome <= 0) {
			return 999;
		}
		return round1(monthlyObligations * 12 / annualIncome * 100);
	}

	public static double round1(double value) {
		return Math.round(value * 10.0) / 10.0;
	}

}
