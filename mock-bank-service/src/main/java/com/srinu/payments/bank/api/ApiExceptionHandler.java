package com.srinu.payments.bank.api;

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
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia(org.springframework.web.HttpMediaTypeNotSupportedException ex,HttpServletRequest request){
        return ResponseEntity.status(415).body(error("UNSUPPORTED_MEDIA_TYPE","Use application/json for this request",request,Map.of()));
    }
    @ExceptionHandler(org.springframework.web.bind.ServletRequestBindingException.class)
    ResponseEntity<ApiError> missingParameter(org.springframework.web.bind.ServletRequestBindingException ex,HttpServletRequest request){
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR","A required request parameter or header is missing",request,Map.of()));
    }
    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex,HttpServletRequest request){
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR","Request parameter has an invalid format",request,Map.of()));
    }
    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(org.springframework.http.converter.HttpMessageNotReadableException ex,HttpServletRequest request){
        return ResponseEntity.badRequest().body(error("VALIDATION_ERROR","Request body must contain valid JSON and field types",request,Map.of()));
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMethod(org.springframework.web.HttpRequestMethodNotSupportedException ex,HttpServletRequest request){
        return ResponseEntity.status(405).body(error("METHOD_NOT_ALLOWED","This HTTP method is not supported for this operation",request,Map.of()));
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

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<ApiError> notFound(NoSuchElementException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error("BANK_RESOURCE_NOT_FOUND", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> badRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(error("INVALID_REQUEST", ex.getMessage(), request, Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class).error(
            "event=BANK_API_FAILURE correlationId={} errorType={} causeType={}",MDC.get("correlationId"),
            ex.getClass().getName(),ex.getCause()==null?"none":ex.getCause().getClass().getName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error("BANK_PROCESSING_ERROR", "Unexpected bank processing error", request, Map.of()));
    }

    private ApiError error(String code, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), code, message, request.getRequestURI(), MDC.get("correlationId"), fieldErrors);
    }

    record ApiError(Instant timestamp, String code, String message, String path,
                    String correlationId, Map<String, String> fieldErrors) {}
}
