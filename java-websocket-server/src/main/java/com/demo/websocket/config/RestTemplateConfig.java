package com.demo.websocket.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate Configuration for AI Service Communication
 */
@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate bean with appropriate timeouts and configuration
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(() -> {
                    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
                    factory.setConnectTimeout(Duration.ofSeconds(5).toMillisPart());
                    factory.setConnectionRequestTimeout(Duration.ofSeconds(5).toMillisPart());
                    return factory;
                })
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(30))
                .build();
    }
}
