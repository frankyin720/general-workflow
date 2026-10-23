package com.application.workflow.integration.idempotency;

import java.util.List;

public interface IdempotencyService {

    boolean tryAcquire(String idempotencyKey);

    void release(String idempotencyKey);
}
