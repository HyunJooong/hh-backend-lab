package com.choo.hhbackendlab.redis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    void redisConnectionTest() {
        // 값 저장
        redisTemplate.opsForValue().set("test-key", "test-value");

        // 값 조회
        String value = redisTemplate.opsForValue().get("test-key");

        assertThat(value).isEqualTo("test-value");
        System.out.println("Redis 연결 성공!");
    }
}
