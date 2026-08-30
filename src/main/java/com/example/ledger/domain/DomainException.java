package com.example.ledger.domain;

public class DomainException extends RuntimeException {

  private final String code;
  private final int status;

  public DomainException(String code, String message, int status) {
    super(message);
    this.code = code;
    this.status = status;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
