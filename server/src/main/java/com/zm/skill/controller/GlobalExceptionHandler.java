package com.zm.skill.controller;

import com.zm.skill.controller.dto.ApiResponse;
import com.zm.skill.controller.dto.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * QA-007: Global exception handler ensuring all error responses
 * include an errorCode in the ApiResponse format.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, message));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.error(ErrorCode.VALIDATION_FAILED, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        return ResponseEntity.internalServerError()
            .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, ex.getMessage()));
    }
}
