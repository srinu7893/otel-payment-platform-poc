package com.srinu.payments.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {
        write(response, request, HttpServletResponse.SC_UNAUTHORIZED,
            "AUTHENTICATION_REQUIRED", "A valid bearer token is required");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        write(response, request, HttpServletResponse.SC_FORBIDDEN,
            "ACCESS_DENIED", "You do not have permission to access this resource");
    }

    private void write(HttpServletResponse response, HttpServletRequest request, int status,
                       String code, String message) throws IOException {
        String correlationId = MDC.get("correlationId");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = request.getHeader("X-Correlation-Id");
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
            "timestamp", Instant.now().toString(),
            "code", code,
            "message", message,
            "path", request.getRequestURI(),
            "correlationId", correlationId == null ? "" : correlationId,
            "fieldErrors", Map.of()));
    }
}
