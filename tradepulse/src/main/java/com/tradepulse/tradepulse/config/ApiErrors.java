package com.tradepulse.tradepulse.config;

import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception ex) { return ResponseEntity.status(409).body(Map.of("message", "The resource already exists or conflicts with stored data")); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<?> invalid(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex instanceof MethodArgumentNotValidException ? "Check the required fields and values" : ex.getMessage()));
    }
    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<?> missing(Exception ex) { return ResponseEntity.status(404).body(Map.of("message", "Resource not found")); }
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<?> credentials(Exception ex) { return ResponseEntity.status(401).body(Map.of("message", "Invalid or expired credentials")); }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<?> forbidden(Exception ex) { return ResponseEntity.status(403).body(Map.of("message", "Access denied")); }
}
