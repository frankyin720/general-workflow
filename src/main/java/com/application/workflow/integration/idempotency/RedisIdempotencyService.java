package com.application.workflow.integration.idempotency;

import com.application.workflow.integration.config.IntegrationConfig;
import org.apache.commons.lang3.BooleanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@ConditionalOnProperty(prefix = "general-workflow.integration.idempotency", name = "enabled", havingValue = "true")
public class RedisIdempotencyService implements IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyService.class);
    private static final String PROCESSED = "PROCESSED";

    private final StringRedisTemplate redisTemplate;
    private final String prefix;
    private final Duration ttl;

    public RedisIdempotencyService(StringRedisTemplate redisTemplate, IntegrationConfig config) {
        this.redisTemplate = redisTemplate;
        this.prefix = config.idempotency().keyPrefix();
        this.ttl = config.idempotency().ttl();
    }

    @Override
    public boolean tryAcquire(String idempotencyKey) {
        String redisKey = this.prefix + idempotencyKey;
        Boolean acquired = this.redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSED, ttl);
        if (BooleanUtils.isFalse(acquired)) {
            log.warn("Duplicated Kafka message detected. Key: {}", idempotencyKey);
        }
        return acquired;
    }

    @Override
    public void release(String idempotencyKey) {
        String redisKey = prefix + idempotencyKey;
        this.redisTemplate.delete(redisKey);
    }
}
