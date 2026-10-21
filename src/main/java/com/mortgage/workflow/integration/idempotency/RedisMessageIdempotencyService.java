package com.mortgage.workflow.integration.idempotency;

import com.mortgage.workflow.integration.config.IntegrationProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "application-workflow.integration.idempotency", name = "enabled", havingValue = "true")
public class RedisMessageIdempotencyService implements MessageIdempotencyService {

	private static final Logger log = LoggerFactory.getLogger(RedisMessageIdempotencyService.class);
	private static final String PROCESSED_VALUE = "PROCESSED";

	private final StringRedisTemplate redisTemplate;
	private final Duration ttl;
	private final String keyPrefix;

	public RedisMessageIdempotencyService(
			StringRedisTemplate redisTemplate,
			IntegrationProperties integrationProperties) {
		this.redisTemplate = redisTemplate;
		this.ttl = integrationProperties.idempotency().ttl();
		this.keyPrefix = integrationProperties.idempotency().keyPrefix();
	}

	@Override
	public boolean tryAcquire(String idempotencyKey) {
		String redisKey = keyPrefix + idempotencyKey;
		Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSED_VALUE, ttl);
		if (Boolean.FALSE.equals(acquired)) {
			log.warn("Duplicate Kafka message detected, idempotencyKey={}", idempotencyKey);
			return false;
		}
		return true;
	}

	@Override
	public void release(String idempotencyKey) {
		redisTemplate.delete(keyPrefix + idempotencyKey);
	}

}
