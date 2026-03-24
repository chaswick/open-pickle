package com.w3llspring.fhpb.web.controller.advice;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class SecurityExceptionHandler {
  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<Void> translateSecurityExceptions(SecurityException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
  }
}
