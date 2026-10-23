package com.application.workflow.integration.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "general-workflow.integration.idempotency", name = "enabled", havingValue = "false")
public class NoOpIdempotencyService implements IdempotencyService {
    @Override
    public boolean tryAcquire(String idempotencyKey) {
        return true;
    }

    @Override
    public void release(String idempotencyKey) {

    }
}
