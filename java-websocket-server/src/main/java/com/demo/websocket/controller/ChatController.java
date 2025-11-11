package com.demo.websocket.controller;

import com.demo.websocket.service.AiServiceLoadBalancer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Map;

/**
 * Chat Controller - Proxy for AI Service with Load Balancing
 * All API calls from frontend should go through this controller
 * Requests are load-balanced across multiple AI service nodes
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final AiServiceLoadBalancer aiServiceLoadBalancer;

    public ChatController(AiServiceLoadBalancer aiServiceLoadBalancer) {
        this.aiServiceLoadBalancer = aiServiceLoadBalancer;
    }

    /**
     * Send chat message
     * POST /api/chat
     * Load-balanced across AI service nodes
     */
    @PostMapping("/chat")
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> request) {
        try {
            log.info("Proxying chat request to AI service (load-balanced): session_id={}", 
                    request.get("session_id"));
            
            // Forward request to AI service via load balancer
            ResponseEntity<Map> response = aiServiceLoadBalancer.post("/chat", request);
            
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
     * Load-balanced across AI service nodes
     */
    @PostMapping("/cancel")
    public ResponseEntity<?> cancelMessage(@RequestBody Map<String, Object> request) {
        try {
            log.info("Proxying cancel request to AI service (load-balanced): session_id={}, message_id={}", 
                    request.get("session_id"), 
                    request.get("message_id"));
            
            // Forward request to AI service via load balancer
            ResponseEntity<Map> response = aiServiceLoadBalancer.post("/cancel", request);
            
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
     * Load-balanced across AI service nodes
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<?> getHistory(@PathVariable String sessionId) {
        try {
            log.info("Proxying history request to AI service (load-balanced): session_id={}", sessionId);
            
            ResponseEntity<Map> response = aiServiceLoadBalancer.get("/history/" + sessionId);
            
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
     * Load-balanced across AI service nodes
     */
    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<?> clearHistory(@PathVariable String sessionId) {
        try {
            log.info("Proxying clear history request to AI service (load-balanced): session_id={}", sessionId);
            
            aiServiceLoadBalancer.delete("/history/" + sessionId);
            
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
     * Health check for all AI service nodes
     * GET /api/ai-health
     */
    @GetMapping("/ai-health")
    public ResponseEntity<?> checkAiServiceHealth() {
        try {
            Map<String, Object> healthStatus = aiServiceLoadBalancer.checkHealth();
            
            String overallStatus = (String) healthStatus.get("overall_status");
            if ("available".equals(overallStatus)) {
                return ResponseEntity.ok(healthStatus);
            } else {
                return ResponseEntity
                        .status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(healthStatus);
            }
                    
        } catch (Exception e) {
            log.error("AI service health check failed", e);
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "Health check failed",
                            "detail", e.getMessage()
                    ));
        }
    }
}
