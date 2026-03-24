package com.w3llspring.fhpb.web.controller.advice;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class MvcExceptionHandler {

  @ExceptionHandler({
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class,
    IllegalArgumentException.class
  })
  public ResponseEntity<Void> handleBadRequest(Exception ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
  }
}
