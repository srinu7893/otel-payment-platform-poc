package com.srinu.otelpoc.auth.api;

import com.srinu.otelpoc.auth.service.AuthenticationService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AuthenticationService.InvalidCredentialsException.class)
    ResponseEntity<ApiError> invalidCredentials(AuthenticationService.InvalidCredentialsException ex,
                                                HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(error("AUTHENTICATION_FAILED", "Invalid username or password", request, Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                fieldError -> fieldError.getField(),
                fieldError -> fieldError.getDefaultMessage() == null ? "invalid" : fieldError.getDefaultMessage(),
                (first, ignored) -> first));
        return ResponseEntity.badRequest()
            .body(error("VALIDATION_ERROR", "Request validation failed", request, fields));
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
