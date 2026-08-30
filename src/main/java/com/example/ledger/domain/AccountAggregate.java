package com.example.ledger.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AccountAggregate {

  private final Account account;
  private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

  private AccountAggregate(Account account) {
    this.account = account;
  }

  public static AccountAggregate open(String ownerName, String currency, BigDecimal initialDeposit) {
    AccountAggregate aggregate = new AccountAggregate(Account.open(ownerName, currency, initialDeposit));
    aggregate.raise("AccountOpened", Map.of("accountId", aggregate.id(), "ownerName", ownerName,
        "currency", currency, "openedAt", aggregate.openedAt().toString()));
    if (initialDeposit.signum() > 0) {
      aggregate.raise("MoneyDeposited", Map.of("accountId", aggregate.id(), "amount", initialDeposit,
          "description", "Initial deposit"));
    }
    return aggregate;
  }

  public static AccountAggregate from(Account account) {
    return new AccountAggregate(account);
  }

  public void deposit(BigDecimal amount, String description) {
    account.deposit(amount);
    raise("MoneyDeposited", money(amount, description));
  }

  public void withdraw(BigDecimal amount, String description) {
    account.withdraw(amount);
    raise("MoneyWithdrawn", money(amount, description));
  }

  public void freeze(String reason) {
    account.freeze();
    raise("AccountFrozen", Map.of("accountId", id(), "reason", reason));
  }

  public void unfreeze() {
    account.unfreeze();
    raise("AccountUnfrozen", Map.of("accountId", id()));
  }

  public void close(String reason) {
    account.close();
    raise("AccountClosed", Map.of("accountId", id(), "reason", reason, "closedAt", Instant.now().toString()));
  }

  private Map<String, Object> money(BigDecimal amount, String description) {
    return Map.of("accountId", id(), "amount", amount, "description", description);
  }

  private void raise(String type, Map<String, Object> data) {
    uncommittedEvents.add(new DomainEvent(type, data, Instant.now()));
  }

  public List<DomainEvent> uncommittedEvents() {
    return List.copyOf(uncommittedEvents);
  }

  public String id() {
    return account.id();
  }

  public BigDecimal balance() {
    return account.balance();
  }

  public String openedAt() {
    return account.openedAt().toString();
  }
}
