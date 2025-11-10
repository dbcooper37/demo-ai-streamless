package com.demo.websocket.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Comprehensive Metrics Service
 * 
 * Tracks:
 * - WebSocket connections & messages
 * - Stream processing latency
 * - Cache hit/miss rates
 * - Error rates
 * - System resources
 */
@Service
@Slf4j
public class MetricsService {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, AtomicInteger> gaugeValues;

    public MetricsService(MeterRegistry registry) {
        this.registry = registry;
        this.gaugeValues = new ConcurrentHashMap<>();
        initializeMetrics();
    }

    private void initializeMetrics() {
        // Register custom gauges
        registerGauge("websocket.active_connections", () -> 
            gaugeValues.getOrDefault("active_connections", new AtomicInteger(0)).get());
        
        registerGauge("stream.active_sessions", () -> 
            gaugeValues.getOrDefault("active_sessions", new AtomicInteger(0)).get());
    }

    // ===== Counter Metrics =====

    public void incrementCounter(String name) {
        incrementCounter(name, Tags.empty());
    }

    public void incrementCounter(String name, Tags tags) {
        Counter.builder(name)
            .tags(tags)
            .register(registry)
            .increment();
    }

    public void incrementCounter(String name, String... tags) {
        Counter.builder(name)
            .tags(tags)
            .register(registry)
            .increment();
    }

    // ===== Timer Metrics =====

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void stopTimer(Timer.Sample sample, String name) {
        stopTimer(sample, name, Tags.empty());
    }

    public void stopTimer(Timer.Sample sample, String name, Tags tags) {
        sample.stop(Timer.builder(name)
            .tags(tags)
            .register(registry));
    }

    public void recordTimer(String name, Duration duration) {
        recordTimer(name, duration, Tags.empty());
    }

    public void recordTimer(String name, Duration duration, Tags tags) {
        Timer.builder(name)
            .tags(tags)
            .register(registry)
            .record(duration);
    }

    // ===== Distribution Summary =====

    public void recordDistribution(String name, long value) {
        recordDistribution(name, value, Tags.empty());
    }

    public void recordDistribution(String name, long value, Tags tags) {
        DistributionSummary.builder(name)
            .tags(tags)
            .register(registry)
            .record(value);
    }

    // ===== Gauge Metrics =====

    public void setGaugeValue(String name, int value) {
        gaugeValues.computeIfAbsent(name, k -> new AtomicInteger(0)).set(value);
    }

    public void incrementGauge(String name) {
        gaugeValues.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public void decrementGauge(String name) {
        gaugeValues.computeIfAbsent(name, k -> new AtomicInteger(0)).decrementAndGet();
    }

    private void registerGauge(String name, Supplier<Number> valueSupplier) {
        Gauge.builder(name, valueSupplier)
            .register(registry);
    }

    // ===== Business Metrics =====

    public void recordWebSocketConnection(String userId, boolean success) {
        incrementCounter("websocket.connections", 
            Tags.of("user", userId, "status", success ? "success" : "failure"));
        
        if (success) {
            incrementGauge("active_connections");
        }
    }

    public void recordWebSocketDisconnection(String userId) {
        incrementCounter("websocket.disconnections", Tags.of("user", userId));
        decrementGauge("active_connections");
    }

    public void recordMessageReceived(String messageType) {
        incrementCounter("websocket.messages.received", 
            Tags.of("type", messageType));
    }

    public void recordMessageSent(String messageType) {
        incrementCounter("websocket.messages.sent", 
            Tags.of("type", messageType));
    }

    public void recordStreamStarted(String sessionId) {
        incrementCounter("stream.started");
        incrementGauge("active_sessions");
    }

    public void recordStreamCompleted(String sessionId, Duration duration, int chunkCount) {
        incrementCounter("stream.completed");
        decrementGauge("active_sessions");
        
        recordTimer("stream.duration", duration);
        recordDistribution("stream.chunks", chunkCount);
    }

    public void recordStreamError(String sessionId, String errorType) {
        incrementCounter("stream.errors", Tags.of("type", errorType));
        decrementGauge("active_sessions");
    }

    public void recordCacheHit(String cacheLevel) {
        incrementCounter("cache.hits", Tags.of("level", cacheLevel));
    }

    public void recordCacheMiss(String cacheLevel) {
        incrementCounter("cache.misses", Tags.of("level", cacheLevel));
    }

    public void recordRecoveryAttempt(boolean success) {
        incrementCounter("recovery.attempts", 
            Tags.of("status", success ? "success" : "failure"));
    }

    public void recordAuthenticationAttempt(boolean success) {
        incrementCounter("authentication.attempts", 
            Tags.of("status", success ? "success" : "failure"));
    }

    public void recordError(String errorType, String component) {
        incrementCounter("errors", 
            Tags.of("type", errorType, "component", component));
    }

    // ===== Performance Metrics =====

    public void recordLatency(String operation, Duration latency) {
        recordTimer(operation + ".latency", latency);
    }

    public void recordThroughput(String operation, long count) {
        recordDistribution(operation + ".throughput", count);
    }
}
