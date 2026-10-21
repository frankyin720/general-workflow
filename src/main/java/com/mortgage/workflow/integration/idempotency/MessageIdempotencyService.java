package com.mortgage.workflow.integration.idempotency;

public interface MessageIdempotencyService {

	/**
	 * @return true if this key was acquired for first-time processing; false if duplicate
	 */
	boolean tryAcquire(String idempotencyKey);

	void release(String idempotencyKey);

}
