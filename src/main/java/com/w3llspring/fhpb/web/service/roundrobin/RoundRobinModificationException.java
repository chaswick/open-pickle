package com.w3llspring.fhpb.web.service.roundrobin;

/**
 * Exception thrown when round-robin management operations fail due to invalid input or permission
 * violations. The message is intended to be surfaced to end users.
 */
public class RoundRobinModificationException extends RuntimeException {

  public RoundRobinModificationException(String message) {
    super(message);
  }

  public RoundRobinModificationException(String message, Throwable cause) {
    super(message, cause);
  }
}
