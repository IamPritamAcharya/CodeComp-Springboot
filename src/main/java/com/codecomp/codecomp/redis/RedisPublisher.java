package com.codecomp.codecomp.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RedisPublisher {

    private final RedisTemplate<String, String> redisTemplate;

    public void publish(String channel, String message) {

        System.out.println("Publishing JSON: " + message);

        redisTemplate.convertAndSend(channel, message);
    }
}