package com.example.ledger.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class Dtos {

  public record Open(
      @NotBlank @Size(max = 200) String ownerName,
      @Size(min = 3, max = 3) String currency,
      @DecimalMin("0.0") BigDecimal initialDeposit) {

    public Open {
      if (currency == null) currency = "USD";
      if (initialDeposit == null) initialDeposit = BigDecimal.ZERO;
    }
  }

  public record Money(
      @NotNull @Positive BigDecimal amount,
      @Size(max = 500) String description) {
  }

  public record Transfer(
      @NotBlank String fromAccountId,
      @NotBlank String toAccountId,
      @NotNull @Positive BigDecimal amount,
      @Size(max = 500) String description) {
  }

  public record Reason(
      @NotBlank @Size(max = 500) String reason) {
  }

  public record OptionalReason(
      @Size(max = 500) String reason) {
  }
}
