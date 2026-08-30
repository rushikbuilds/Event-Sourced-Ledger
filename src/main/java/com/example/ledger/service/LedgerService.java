package com.example.ledger.service;

import com.example.ledger.api.Dtos;
import com.example.ledger.domain.*;
import com.example.ledger.eventstore.EventStore;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
public class LedgerService {
  private final JdbcTemplate db;
  private final EventStore store;
  private final ProjectionService projections;
  private final ExchangeRateService exchangeRateService;

  public LedgerService(JdbcTemplate db, EventStore store, ProjectionService projections, ExchangeRateService exchangeRateService) {
    this.db = db;
    this.store = store;
    this.projections = projections;
    this.exchangeRateService = exchangeRateService;
  }

  @Transactional
  @CacheEvict(value = "accountsList", allEntries = true)
  public Map<String, Object> open(Dtos.Open r) {
    Account a = Account.open(r.ownerName(), r.currency(), r.initialDeposit());
    emit(a, "AccountOpened", Map.of("accountId", a.id(), "ownerName", a.ownerName(), "currency", a.currency(), "openedAt", a.openedAt().toString()));
    if (r.initialDeposit().signum() > 0) {
      emit(a, "MoneyDeposited", Map.of("accountId", a.id(), "amount", r.initialDeposit(), "description", "Initial deposit"));
    }
    project();
    return Map.of("accountId", a.id(), "balance", a.balance());
  }

  @Transactional
  @Caching(evict = {
      @CacheEvict(value = "accounts", key = "#id"),
      @CacheEvict(value = "accountsList", allEntries = true),
      @CacheEvict(value = "transactions", allEntries = true),
      @CacheEvict(value = "events", key = "#id")
  })
  public void money(String id, BigDecimal amount, String desc, boolean withdrawal) {
    Account a = load(id);
    if (withdrawal) a.withdraw(amount); else a.deposit(amount);
    emit(a, withdrawal ? "MoneyWithdrawn" : "MoneyDeposited", Map.of("accountId", id, "amount", amount, "description", desc == null ? (withdrawal ? "Withdrawal" : "Deposit") : desc));
    project();
  }

  @Transactional
  @Caching(evict = {
      @CacheEvict(value = "accounts", allEntries = true),
      @CacheEvict(value = "accountsList", allEntries = true),
      @CacheEvict(value = "transactions", allEntries = true),
      @CacheEvict(value = "events", allEntries = true)
  })
  public Map<String, Object> transfer(Dtos.Transfer r) {
    if (r.fromAccountId().equals(r.toAccountId())) {
      throw new DomainException("SAME_ACCOUNT_TRANSFER", "Cannot transfer money to the same account '" + r.fromAccountId() + "'", 422);
    }
    Account from = load(r.fromAccountId()), to = load(r.toAccountId());
    BigDecimal rate = exchangeRateService.getRate(from.currency(), to.currency());
    BigDecimal creditAmount = r.amount().multiply(rate).setScale(4, RoundingMode.HALF_UP);

    from.debit(r.amount());
    to.credit(creditAmount);

    String tid = UUID.randomUUID().toString(), d = r.description() == null ? "Transfer" : r.description();
    var sent = new HashMap<String, Object>();
    sent.put("type", "TransferSent");
    sent.putAll(Map.of(
        "accountId", from.id(),
        "toAccountId", to.id(),
        "amount", r.amount(),
        "currency", from.currency(),
        "targetAmount", creditAmount,
        "targetCurrency", to.currency(),
        "exchangeRate", rate,
        "transferId", tid,
        "description", d
    ));

    var received = new HashMap<String, Object>();
    received.put("type", "TransferReceived");
    received.putAll(Map.of(
        "accountId", to.id(),
        "fromAccountId", from.id(),
        "amount", creditAmount,
        "currency", to.currency(),
        "sourceAmount", r.amount(),
        "sourceCurrency", from.currency(),
        "exchangeRate", rate,
        "transferId", tid,
        "description", d
    ));

    store.appendAtomic(List.of(
        new EventStore.Append("account-" + from.id(), List.of(sent), store.version("account-" + from.id())),
        new EventStore.Append("account-" + to.id(), List.of(received), store.version("account-" + to.id()))
    ));
    project();

    return Map.of(
        "transferId", tid,
        "fromAccountId", from.id(),
        "toAccountId", to.id(),
        "amount", r.amount(),
        "fromCurrency", from.currency(),
        "creditedAmount", creditAmount,
        "toCurrency", to.currency(),
        "exchangeRate", rate
    );
  }

  @Transactional
  @Caching(evict = {
      @CacheEvict(value = "accounts", key = "#id"),
      @CacheEvict(value = "accountsList", allEntries = true),
      @CacheEvict(value = "events", key = "#id")
  })
  public void manage(String id, String action) {
    Account a = load(id);
    if (action.equals("freeze")) a.freeze(); else if (action.equals("unfreeze")) a.unfreeze(); else a.close();
    Map<String, Object> e = new HashMap<>();
    e.put("accountId", id);
    e.put("type", action.equals("freeze") ? "AccountFrozen" : action.equals("unfreeze") ? "AccountUnfrozen" : "AccountClosed");
    if (action.equals("freeze")) e.put("reason", "request");
    emit(a, (String) e.remove("type"), e);
    project();
  }

  @Cacheable(value = "accounts", key = "#id")
  public Map<String, Object> account(String id) {
    return db.query("select account_id,owner_name,balance,currency,status,opened_at,updated_at from account_balances where account_id=?", rs -> {
      if (!rs.next()) throw notFound(id);
      return Map.of(
          "accountId", rs.getString(1),
          "ownerName", rs.getString(2),
          "balance", rs.getBigDecimal(3),
          "currency", rs.getString(4),
          "status", rs.getString(5),
          "openedAt", rs.getTimestamp(6).toInstant(),
          "updatedAt", rs.getTimestamp(7).toInstant()
      );
    }, id);
  }

  @Cacheable(value = "accountsList", key = "#limit + '_' + #offset")
  public Map<String, Object> list(int limit, int offset) {
    var rows = db.queryForList("select account_id,owner_name,balance,currency,status,opened_at,updated_at from account_balances order by opened_at desc limit ? offset ?", limit, offset);
    return Map.of("accounts", rows, "total", db.queryForObject("select count(*) from account_balances", Long.class), "limit", limit, "offset", offset);
  }

  @Cacheable(value = "transactions", key = "#id + '_' + #limit + '_' + #offset")
  public List<Map<String, Object>> transactions(String id, int limit, int offset) {
    account(id);
    return db.queryForList("select id,account_id,type,amount,balance_after,description,counterparty_account_id,transfer_id,created_at from transaction_history where account_id=? order by created_at desc limit ? offset ?", id, limit, offset);
  }

  @Cacheable(value = "events", key = "#id")
  public List<EventStore.Stored> events(String id) {
    var e = store.stream("account-" + id);
    if (e.isEmpty()) throw notFound(id);
    return e;
  }

  private Account load(String id) {
    var e = events(id);
    Map<String, Object> o = e.get(0).data();
    Account a = Account.rehydrate(id, (String) o.get("ownerName"), (String) o.get("currency"), BigDecimal.ZERO, Account.Status.active, Instant.parse((String) o.get("openedAt")));
    for (var x : e) apply(a, x.data());
    return a;
  }

  private void apply(Account a, Map<String, Object> e) {
    String t = (String) e.get("type");
    if (t.equals("MoneyDeposited") || t.equals("TransferReceived")) {
      a.credit(new BigDecimal(e.get("amount").toString()));
    } else if (t.equals("MoneyWithdrawn") || t.equals("TransferSent")) {
      a.debit(new BigDecimal(e.get("amount").toString()));
    } else if (t.equals("AccountFrozen")) {
      a.freeze();
    } else if (t.equals("AccountUnfrozen")) {
      a.unfreeze();
    }
  }

  private void emit(Account a, String type, Map<String, Object> data) {
    var e = new HashMap<>(data);
    e.put("type", type);
    store.append("account-" + a.id(), List.of(e), store.version("account-" + a.id()));
  }

  private void project() { projections.rebuild(); }

  @Caching(evict = {
      @CacheEvict(value = "accounts", allEntries = true),
      @CacheEvict(value = "accountsList", allEntries = true),
      @CacheEvict(value = "transactions", allEntries = true),
      @CacheEvict(value = "events", allEntries = true)
  })
  public void rebuild() { projections.rebuild(); }

  private DomainException notFound(String id) {
    return new DomainException("ACCOUNT_NOT_FOUND", "Account '" + id + "' does not exist", 404);
  }
}
