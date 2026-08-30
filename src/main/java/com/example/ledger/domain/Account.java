package com.example.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class Account {

  public enum Status { active, frozen, closed }

  private final String id;
  private final String ownerName;
  private final String currency;
  private BigDecimal balance;
  private Status status;
  private final Instant openedAt;

  private Account(String id, String ownerName, String currency, BigDecimal balance, Status status, Instant openedAt) {
    this.id = id;
    this.ownerName = ownerName;
    this.currency = currency;
    this.balance = balance;
    this.status = status;
    this.openedAt = openedAt;
  }

  public static Account open(String ownerName, String currency, BigDecimal initialDeposit) {
    return new Account(UUID.randomUUID().toString(), ownerName, currency, initialDeposit, Status.active, Instant.now());
  }

  public static Account rehydrate(String id, String ownerName, String currency, BigDecimal balance, Status status, Instant openedAt) {
    return new Account(id, ownerName, currency, balance, status, openedAt);
  }

  public void deposit(BigDecimal amount) {
    active();
    positive(amount);
    balance = balance.add(amount);
  }

  public void withdraw(BigDecimal amount) {
    active();
    positive(amount);
    if (amount.compareTo(balance) > 0) {
      throw new DomainException("INSUFFICIENT_FUNDS",
          "Insufficient funds in account '" + id + "': balance is " + balance + ", attempted " + amount, 422);
    }
    balance = balance.subtract(amount);
  }

  public void debit(BigDecimal amount) {
    withdraw(amount);
  }

  public void credit(BigDecimal amount) {
    active();
    positive(amount);
    balance = balance.add(amount);
  }

  public void freeze() {
    if (status == Status.closed) {
      throw new DomainException("ACCOUNT_CLOSED", "Account '" + id + "' is closed and cannot be modified", 409);
    }
    if (status == Status.frozen) {
      throw new DomainException("ACCOUNT_ALREADY_FROZEN", "Account '" + id + "' is already frozen", 409);
    }
    status = Status.frozen;
  }

  public void unfreeze() {
    if (status != Status.frozen) {
      throw new DomainException("ACCOUNT_NOT_FROZEN", "Account '" + id + "' is not frozen", 409);
    }
    status = Status.active;
  }

  public void close() {
    if (status == Status.closed) {
      throw new DomainException("ACCOUNT_CLOSED", "Account '" + id + "' is closed and cannot be modified", 409);
    }
    if (balance.signum() != 0) {
      throw new DomainException("ACCOUNT_HAS_BALANCE", "Account '" + id + "' has a remaining balance of " + balance, 409);
    }
    status = Status.closed;
  }

  private void active() {
    if (status != Status.active) {
      throw new DomainException("ACCOUNT_NOT_ACTIVE",
          "Account '" + id + "' is " + status + " - only active accounts can transact", 409);
    }
  }

  private static void positive(BigDecimal a) {
    if (a.signum() <= 0) {
      throw new DomainException("INVALID_AMOUNT", "Amount must be a positive number, got " + a, 422);
    }
  }

  public String id() {
    return id;
  }

  public String ownerName() {
    return ownerName;
  }

  public String currency() {
    return currency;
  }

  public BigDecimal balance() {
    return balance;
  }

  public Status status() {
    return status;
  }

  public Instant openedAt() {
    return openedAt;
  }
}
