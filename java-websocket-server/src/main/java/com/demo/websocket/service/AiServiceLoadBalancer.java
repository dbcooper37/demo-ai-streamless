package com.demo.websocket.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Load Balancer for AI Service Nodes
 * Round-robin load balancing across multiple AI service instances
 */
@Slf4j
@Service
public class AiServiceLoadBalancer {

    private final RestTemplate restTemplate;
    private final List<String> aiServiceUrls;
    private final AtomicInteger currentIndex;

    public AiServiceLoadBalancer(RestTemplate restTemplate,
                                  @Value("${ai.service.urls:http://python-ai-1:8000,http://python-ai-2:8000,http://python-ai-3:8000}") 
                                  String aiServiceUrlsConfig) {
        this.restTemplate = restTemplate;
        this.aiServiceUrls = parseUrls(aiServiceUrlsConfig);
        this.currentIndex = new AtomicInteger(0);
        
        log.info("AiServiceLoadBalancer initialized with {} nodes: {}", 
                aiServiceUrls.size(), aiServiceUrls);
    }

    /**
     * Parse comma-separated URLs
     */
    private List<String> parseUrls(String urlsConfig) {
        List<String> urls = new ArrayList<>();
        for (String url : urlsConfig.split(",")) {
            String trimmed = url.trim();
            if (!trimmed.isEmpty()) {
                urls.add(trimmed);
            }
        }
        return urls;
    }

    /**
     * Get next AI service URL using round-robin
     */
    private String getNextUrl() {
        if (aiServiceUrls.isEmpty()) {
            throw new IllegalStateException("No AI service URLs configured");
        }
        
        int index = Math.abs(currentIndex.getAndIncrement() % aiServiceUrls.size());
        return aiServiceUrls.get(index);
    }

    /**
     * Execute request with retry logic
     */
    public <T> ResponseEntity<T> execute(String path, 
                                          HttpMethod method, 
                                          Object body, 
                                          Class<T> responseType) {
        int maxRetries = aiServiceUrls.size();
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            String baseUrl = getNextUrl();
            String fullUrl = baseUrl + path;
            
            try {
                log.debug("Attempting request to AI service: url={}, method={}, attempt={}/{}", 
                        fullUrl, method, attempt + 1, maxRetries);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<?> entity = body != null ? new HttpEntity<>(body, headers) : new HttpEntity<>(headers);
                
                ResponseEntity<T> response = restTemplate.exchange(
                        fullUrl,
                        method,
                        entity,
                        responseType
                );

                log.info("AI service request successful: url={}, status={}", 
                        fullUrl, response.getStatusCode());
                
                return response;

            } catch (Exception e) {
                lastException = e;
                log.warn("AI service request failed: url={}, attempt={}/{}, error={}", 
                        fullUrl, attempt + 1, maxRetries, e.getMessage());
                
                // Try next node
                if (attempt < maxRetries - 1) {
                    log.info("Retrying with next AI service node...");
                }
            }
        }

        // All retries failed
        log.error("All AI service nodes failed after {} attempts", maxRetries, lastException);
        throw new RuntimeException("All AI service nodes are unavailable", lastException);
    }

    /**
     * POST request to AI service
     */
    public ResponseEntity<Map> post(String path, Object body) {
        return execute(path, HttpMethod.POST, body, Map.class);
    }

    /**
     * GET request to AI service
     */
    public ResponseEntity<Map> get(String path) {
        return execute(path, HttpMethod.GET, null, Map.class);
    }

    /**
     * DELETE request to AI service
     */
    public ResponseEntity<Void> delete(String path) {
        return execute(path, HttpMethod.DELETE, null, Void.class);
    }

    /**
     * Health check for all AI service nodes
     */
    public Map<String, Object> checkHealth() {
        Map<String, Object> healthStatus = new java.util.HashMap<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        int healthyCount = 0;

        for (String url : aiServiceUrls) {
            Map<String, Object> nodeStatus = new java.util.HashMap<>();
            nodeStatus.put("url", url);
            
            try {
                ResponseEntity<Map> response = restTemplate.getForEntity(url + "/health", Map.class);
                nodeStatus.put("status", "healthy");
                nodeStatus.put("http_status", response.getStatusCode().value());
                nodeStatus.put("response", response.getBody());
                healthyCount++;
            } catch (Exception e) {
                nodeStatus.put("status", "unhealthy");
                nodeStatus.put("error", e.getMessage());
            }
            
            nodes.add(nodeStatus);
        }

        healthStatus.put("total_nodes", aiServiceUrls.size());
        healthStatus.put("healthy_nodes", healthyCount);
        healthStatus.put("nodes", nodes);
        healthStatus.put("overall_status", healthyCount > 0 ? "available" : "unavailable");

        return healthStatus;
    }
}
