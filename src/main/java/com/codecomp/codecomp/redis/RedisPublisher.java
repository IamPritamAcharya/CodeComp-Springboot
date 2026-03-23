package com.codecomp.codecomp.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class RedisPublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void publish(String channel, Object message) {
        try {
            String json = objectMapper.writeValueAsString(message);

            System.out.println("Publishing JSON: " + json);

            redisTemplate.convertAndSend(channel, json);

        } catch (Exception e) {
            throw new RuntimeException("Redis publish failed", e);
        }
    }
}