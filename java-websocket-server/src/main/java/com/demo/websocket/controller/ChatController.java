package com.demo.websocket.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Map;

/**
 * Chat Controller - Proxy for AI Service
 * All API calls from frontend should go through this controller
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final RestTemplate restTemplate;
    
    @Value("${ai.service.url:http://python-ai-service:8000}")
    private String aiServiceUrl;

    public ChatController() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Send chat message
     * POST /api/chat
     */
    @PostMapping("/chat")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> request) {
        try {
            log.info("Proxying chat request to AI service: session_id={}", 
                    request.get("session_id"));
            
            // Forward request to Python AI service
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            String url = aiServiceUrl + "/chat";
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, 
                    entity, 
                    Map.class
            );
            
            log.info("Chat request successful: status={}, message_id={}", 
                    response.getStatusCode(), 
                    response.getBody() != null ? response.getBody().get("message_id") : "N/A");
            
            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(response.getBody());
                    
        } catch (HttpClientErrorException e) {
            log.error("Client error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(Map.of(
                            "error", "Bad request",
                            "detail", e.getResponseBodyAsString()
                    ));
                    
        } catch (HttpServerErrorException e) {
            log.error("Server error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error", "AI service error",
                            "detail", e.getMessage()
                    ));
                    
        } catch (Exception e) {
            log.error("Error proxying chat request", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Internal server error",
                            "detail", e.getMessage()
                    ));
        }
    }

    /**
     * Cancel streaming message
     * POST /api/cancel
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelMessage(@RequestBody Map<String, Object> request) {
        try {
            log.info("Proxying cancel request to AI service: session_id={}, message_id={}", 
                    request.get("session_id"), 
                    request.get("message_id"));
            
            // Forward request to Python AI service
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
            
            String url = aiServiceUrl + "/cancel";
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, 
                    entity, 
                    Map.class
            );
            
            log.info("Cancel request successful: status={}", response.getStatusCode());
            
            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(response.getBody());
                    
        } catch (HttpClientErrorException e) {
            log.error("Client error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(Map.of(
                            "error", "Bad request",
                            "detail", e.getResponseBodyAsString()
                    ));
                    
        } catch (HttpServerErrorException e) {
            log.error("Server error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error", "AI service error",
                            "detail", e.getMessage()
                    ));
                    
        } catch (Exception e) {
            log.error("Error proxying cancel request", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Internal server error",
                            "detail", e.getMessage()
                    ));
        }
    }

    /**
     * Get chat history
     * GET /api/history/{sessionId}
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<?> getHistory(@PathVariable String sessionId) {
        try {
            log.info("Proxying history request to AI service: session_id={}", sessionId);
            
            String url = aiServiceUrl + "/history/" + sessionId;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            log.info("History request successful: status={}, count={}", 
                    response.getStatusCode(),
                    response.getBody() != null ? response.getBody().get("count") : "N/A");
            
            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(response.getBody());
                    
        } catch (HttpClientErrorException e) {
            log.error("Client error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(Map.of(
                            "error", "Bad request",
                            "detail", e.getResponseBodyAsString()
                    ));
                    
        } catch (HttpServerErrorException e) {
            log.error("Server error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error", "AI service error",
                            "detail", e.getMessage()
                    ));
                    
        } catch (Exception e) {
            log.error("Error proxying history request", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Internal server error",
                            "detail", e.getMessage()
                    ));
        }
    }

    /**
     * Clear chat history
     * DELETE /api/history/{sessionId}
     */
    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<?> clearHistory(@PathVariable String sessionId) {
        try {
            log.info("Proxying clear history request to AI service: session_id={}", sessionId);
            
            String url = aiServiceUrl + "/history/" + sessionId;
            restTemplate.delete(url);
            
            log.info("Clear history request successful");
            
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "History cleared for session " + sessionId
            ));
                    
        } catch (HttpClientErrorException e) {
            log.error("Client error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(Map.of(
                            "error", "Bad request",
                            "detail", e.getResponseBodyAsString()
                    ));
                    
        } catch (HttpServerErrorException e) {
            log.error("Server error from AI service: status={}, body={}", 
                    e.getStatusCode(), e.getResponseBodyAsString());
            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error", "AI service error",
                            "detail", e.getMessage()
                    ));
                    
        } catch (Exception e) {
            log.error("Error proxying clear history request", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "error", "Internal server error",
                            "detail", e.getMessage()
                    ));
        }
    }

    /**
     * Health check for AI service connectivity
     * GET /api/ai-health
     */
    @GetMapping("/ai-health")
    public ResponseEntity<?> checkAiServiceHealth() {
        try {
            String url = aiServiceUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            return ResponseEntity.ok(Map.of(
                    "ai_service", "reachable",
                    "status", response.getBody() != null ? response.getBody().get("status") : "unknown",
                    "url", aiServiceUrl
            ));
                    
        } catch (Exception e) {
            log.error("AI service health check failed", e);
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "ai_service", "unreachable",
                            "error", e.getMessage(),
                            "url", aiServiceUrl
                    ));
        }
    }
}
