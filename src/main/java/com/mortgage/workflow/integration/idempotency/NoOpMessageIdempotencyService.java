package com.mortgage.workflow.integration.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "mortgage-workflow.integration.idempotency", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpMessageIdempotencyService implements MessageIdempotencyService {

	@Override
	public boolean tryAcquire(String idempotencyKey) {
		return true;
	}

	@Override
	public void release(String idempotencyKey) {
		// no-op
	}

}
