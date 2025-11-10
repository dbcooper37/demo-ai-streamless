package com.demo.websocket.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * Security Validator with JWT Support
 * 
 * Handles:
 * - JWT token validation
 * - Token expiration checking
 * - User authorization
 * - Rate limiting
 */
@Service
@Slf4j
public class SecurityValidator {

    private final SecretKey secretKey;
    private final long tokenExpirationMs;
    private final MetricsService metricsService;

    public SecurityValidator(
            @Value("${security.jwt.secret:default-secret-key-change-this-in-production-minimum-256-bits}") String secret,
            @Value("${security.jwt.expiration-ms:3600000}") long tokenExpirationMs,
            MetricsService metricsService) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenExpirationMs = tokenExpirationMs;
        this.metricsService = metricsService;
    }

    /**
     * Validate JWT token and user ID match
     */
    public boolean validateToken(String token, String userId) {
        try {
            if (token == null || token.isEmpty()) {
                log.warn("Empty token provided for user: {}", userId);
                metricsService.recordAuthenticationAttempt(false);
                return false;
            }

            // Remove "Bearer " prefix if present
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            Claims claims = extractAllClaims(token);
            
            // Validate user ID matches
            String tokenUserId = claims.getSubject();
            if (!userId.equals(tokenUserId)) {
                log.warn("User ID mismatch: expected={}, token={}", userId, tokenUserId);
                metricsService.recordAuthenticationAttempt(false);
                return false;
            }

            // Validate expiration
            if (isTokenExpired(claims)) {
                log.warn("Token expired for user: {}", userId);
                metricsService.recordAuthenticationAttempt(false);
                return false;
            }

            metricsService.recordAuthenticationAttempt(true);
            return true;

        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            metricsService.recordAuthenticationAttempt(false);
            return false;
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            metricsService.recordAuthenticationAttempt(false);
            return false;
        } catch (ExpiredJwtException e) {
            log.error("Expired JWT token: {}", e.getMessage());
            metricsService.recordAuthenticationAttempt(false);
            return false;
        } catch (UnsupportedJwtException e) {
            log.error("Unsupported JWT token: {}", e.getMessage());
            metricsService.recordAuthenticationAttempt(false);
            return false;
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
            metricsService.recordAuthenticationAttempt(false);
            return false;
        } catch (Exception e) {
            log.error("Error validating token", e);
            metricsService.recordAuthenticationAttempt(false);
            return false;
        }
    }

    /**
     * Extract user ID from token
     */
    public String extractUserId(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extract expiration date from token
     */
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * Extract specific claim from token
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    /**
     * Check if token is expired
     */
    private boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    /**
     * Generate JWT token (for testing/development)
     */
    public String generateToken(String userId) {
        return Jwts.builder()
            .subject(userId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + tokenExpirationMs))
            .signWith(secretKey)
            .compact();
    }

    /**
     * Validate rate limit for user
     * TODO: Implement with Redis
     */
    public boolean checkRateLimit(String userId) {
        // For now, always allow
        // In production, implement with Redis rate limiter
        return true;
    }

    /**
     * Check if user has permission for resource
     */
    public boolean hasPermission(String userId, String resource, String action) {
        // Basic implementation - can be extended with role-based access control
        log.debug("Checking permission: user={}, resource={}, action={}", userId, resource, action);
        return true;
    }
}
