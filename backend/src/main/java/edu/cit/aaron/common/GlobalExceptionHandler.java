package edu.cit.aaron.common;

import edu.cit.aaron.inventory.ProductNotFoundException;
import edu.cit.aaron.shop.OrderConflictException;
import edu.cit.aaron.shop.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "INVALID");
        body.put("reason", ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse("Invalid request"));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ProductNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "REJECTED");
        body.put("reason", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "NOT_FOUND");
        body.put("reason", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(OrderConflictException.class)
    public ResponseEntity<Map<String, Object>> handleOrderConflict(OrderConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "CONFLICT");
        body.put("reason", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}

