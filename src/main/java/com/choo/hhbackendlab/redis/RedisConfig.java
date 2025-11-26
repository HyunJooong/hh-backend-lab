package com.choo.hhbackendlab.redis;

import io.lettuce.core.RedisClient;
import org.redisson.Redisson;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Redisson 설정
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisClient redisClient(
            @Value("${spring.data.redis.host") String host
            , @Value("${spring.data.redis.port}") int port
    ) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port);

        return (RedisClient) Redisson.create(config);
    }

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        return new LettuceConnectionFactory("localhost", 6379);
    }
}
