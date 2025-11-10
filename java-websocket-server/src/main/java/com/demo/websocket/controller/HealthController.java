package com.demo.websocket.controller;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final RedisTemplate<String, String> redisTemplate;

    public HealthController(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");

        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            response.put("redis", "connected");
        } catch (Exception e) {
            response.put("redis", "disconnected");
        }

        return response;
    }
}
