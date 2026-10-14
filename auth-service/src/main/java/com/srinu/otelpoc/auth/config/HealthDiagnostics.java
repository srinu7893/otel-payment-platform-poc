package com.srinu.otelpoc.auth.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** Log only health status/component names; never serialize contributor details or exceptions. */
@Component
public class HealthDiagnostics extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(HealthDiagnostics.class);
    private final ObjectMapper mapper;
    public HealthDiagnostics(ObjectMapper mapper) { this.mapper = mapper; }
    @EventListener
    public void availability(AvailabilityChangeEvent<?> event) {
        log.info("event=APPLICATION_AVAILABILITY state={}", event.getState());
    }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !(path.equals("/actuator/health") || path.startsWith("/actuator/health/"));
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cached = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(request, cached);
            try {
                JsonNode health = mapper.readTree(cached.getContentAsByteArray());
                if (health != null) logStatuses("root", health);
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                log.warn("event=HEALTH_RESPONSE_UNREADABLE status={}", cached.getStatus());
            }
        } finally { cached.copyBodyToResponse(); }
    }
    private void logStatuses(String component, JsonNode health) {
        String status = health.path("status").asText();
        if (status.equals("DOWN") || status.equals("OUT_OF_SERVICE") || status.equals("UNKNOWN")) {
            log.warn("event=HEALTH_COMPONENT_UNHEALTHY component={} status={}", component, status);
        }
        health.path("components").fields().forEachRemaining(entry -> logStatuses(entry.getKey(), entry.getValue()));
    }
}
