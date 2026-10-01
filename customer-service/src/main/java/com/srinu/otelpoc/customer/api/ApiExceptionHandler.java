package com.srinu.otelpoc.customer.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                error -> error.getField(),
                error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                (first, ignored) -> first));
        return ResponseEntity.badRequest()
            .body(error("VALIDATION_ERROR", "Request validation failed", request, fields));
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> notFound(NoSuchElementException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error("CUSTOMER_NOT_FOUND", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(error("INVALID_REQUEST", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error("INTERNAL_ERROR", "Unexpected processing error", request, Map.of()));
    }

    private ApiError error(String code, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), code, message, request.getRequestURI(), MDC.get("correlationId"), fieldErrors);
    }

    record ApiError(Instant timestamp, String code, String message, String path,
                    String correlationId, Map<String, String> fieldErrors) {}
}
