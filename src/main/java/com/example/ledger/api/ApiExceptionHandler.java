package com.example.ledger.api;

import com.example.ledger.domain.DomainException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {

  @ExceptionHandler(DomainException.class)
  ResponseEntity<Map<String, Object>> domain(DomainException e) {
    return ResponseEntity.status(e.status()).body(
        Map.of("error", e.code(), "message", e.getMessage(), "statusCode", e.status()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException e) {
    var details = e.getBindingResult().getFieldErrors().stream()
        .map(x -> Map.of("field", x.getField(), "message", x.getDefaultMessage()))
        .toList();
    return ResponseEntity.unprocessableEntity().body(
        Map.of("error", "VALIDATION_ERROR", "message", "Invalid request data", "details", details));
  }
}
