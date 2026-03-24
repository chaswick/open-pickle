package com.w3llspring.fhpb.web.service.meetups;

public class DuplicateMeetupException extends RuntimeException {

  public DuplicateMeetupException(String message) {
    super(message);
  }
}
