package com.demo.websocket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * RestTemplate Configuration
 * Configures RestTemplate with proper message converters to handle various content types
 */
@Configuration
public class RestTemplateConfig {

    /**
     * Create a RestTemplate bean with custom message converters
     * This fixes the "no suitable HttpMessageConverter" error by allowing
     * JSON parsing even when content-type is text/plain or other non-standard types
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add a custom JSON converter that accepts multiple content types
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        
        // Configure converter to accept text/plain, application/json, and other content types as JSON
        List<MediaType> supportedMediaTypes = Arrays.asList(
            MediaType.APPLICATION_JSON,
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_HTML,
            new MediaType("application", "*+json"),
            new MediaType("text", "*")
        );
        jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
        
        // Add the converter to the RestTemplate
        // Place it at the beginning so it takes precedence
        restTemplate.getMessageConverters().add(0, jsonConverter);
        
        return restTemplate;
    }
}
