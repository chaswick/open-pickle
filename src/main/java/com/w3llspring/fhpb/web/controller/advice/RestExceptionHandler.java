package com.w3llspring.fhpb.web.controller.advice;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * Global exception handler for REST controllers. Ensures validation errors and other exceptions are
 * properly formatted and include error messages for the frontend to display to users.
 */
@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);
  private static final DisconnectedClientHelper disconnectedClientHelper =
      new DisconnectedClientHelper(RestExceptionHandler.class.getName());

  /**
   * Missing static resources should be a normal 404, not a 500. iOS/Safari commonly probes for
   * apple-touch-icon-precomposed.png.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<Map<String, Object>> handleNoResourceFoundException(
      NoResourceFoundException ex) {
    Map<String, Object> errorResponse = new HashMap<>();

    String correlationId = java.util.UUID.randomUUID().toString().substring(0, 8);
    String message = "Not found";

    errorResponse.put("error", message);
    errorResponse.put("message", message);
    errorResponse.put("status", HttpStatus.NOT_FOUND.value());
    errorResponse.put("exception", ex.getClass().getSimpleName());
    errorResponse.put("correlationId", correlationId);
    errorResponse.put("timestamp", java.time.Instant.now().toString());

    log.debug("[{}] NoResourceFoundException: {}", correlationId, ex.getMessage());

    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .header("X-Correlation-Id", correlationId)
        .body(errorResponse);
  }

  /**
   * Handles ResponseStatusException thrown by REST controllers. Returns a JSON response with the
   * error message for user-friendly display.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> handleResponseStatusException(
      ResponseStatusException ex) {
    String message = ex.getReason();
    if (message == null || message.isBlank()) {
      message = "An error occurred. Please try again.";
    }
    String correlationId = java.util.UUID.randomUUID().toString().substring(0, 8);
    log.warn("[{}] ResponseStatusException: {} - {}", correlationId, ex.getStatusCode(), message);
    return buildErrorResponse(
        ex.getStatusCode().value(), ex.getClass().getSimpleName(), message, correlationId);
  }

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    HttpMessageNotReadableException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<Map<String, Object>> handleBadRequest(Exception ex) {
    String message = ex.getMessage();
    if (message == null || message.isBlank()) {
      message = "Invalid request.";
    }
    log.debug("Bad request: {}", message, ex);
    return buildErrorResponse(
        HttpStatus.BAD_REQUEST.value(), ex.getClass().getSimpleName(), message);
  }

  /**
   * Handles any unexpected exceptions in REST controllers. Returns a generic error message without
   * exposing internal details.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<?> handleGenericException(Exception ex) {
    if (disconnectedClientHelper.checkAndLogClientDisconnectedException(ex)) {
      return ResponseEntity.noContent().build();
    }
    String correlationId = java.util.UUID.randomUUID().toString().substring(0, 8);
    log.error("[{}] Unexpected exception: {}", correlationId, ex.getMessage(), ex);
    return buildErrorResponse(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        ex.getClass().getSimpleName(),
        "An unexpected error occurred. Please try again.",
        correlationId);
  }

  private ResponseEntity<Map<String, Object>> buildErrorResponse(
      int status, String exception, String message) {
    return buildErrorResponse(
        status, exception, message, java.util.UUID.randomUUID().toString().substring(0, 8));
  }

  private ResponseEntity<Map<String, Object>> buildErrorResponse(
      int status, String exception, String message, String correlationId) {
    Map<String, Object> errorResponse = new HashMap<>();
    errorResponse.put("error", message);
    errorResponse.put("message", message);
    errorResponse.put("status", status);
    errorResponse.put("exception", exception);
    errorResponse.put("correlationId", correlationId);
    errorResponse.put("timestamp", java.time.Instant.now().toString());

    return ResponseEntity.status(status)
        .header("X-Correlation-Id", correlationId)
        .body(errorResponse);
  }
}
