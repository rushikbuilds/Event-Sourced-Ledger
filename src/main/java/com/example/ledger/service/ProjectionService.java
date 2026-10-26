package com.example.ledger.service;

import com.example.ledger.eventstore.EventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ProjectionService {

  private static final String PROJECTION_NAME = "main";
  private final JdbcTemplate db;
  private final EventStore store;

  public ProjectionService(JdbcTemplate db, EventStore store) {
    this.db = db;
    this.store = store;
  }

  @PostConstruct
  public void catchUp() {
    processNewEvents();
  }

  @Transactional
  public void processNewEvents() {
    Long lastPos = db.queryForObject(
        "select coalesce(max(last_processed_position), 0) from projection_checkpoints where projection_name = ?",
        Long.class,
        PROJECTION_NAME
    );
    long currentPos = lastPos == null ? 0 : lastPos;

    List<EventStore.Stored> newEvents = store.all(currentPos);
    if (newEvents.isEmpty()) {
      return;
    }

    for (var event : newEvents) {
      applyEvent(event);
      currentPos = event.globalPosition();
    }

    db.update(
        "insert into projection_checkpoints(projection_name, last_processed_position, updated_at) " +
        "values(?, ?, now()) on conflict(projection_name) do update set last_processed_position = excluded.last_processed_position, updated_at = now()",
        PROJECTION_NAME, currentPos
    );
  }

  @Transactional
  public void rebuild() {
    db.update("delete from transaction_history");
    db.update("delete from account_balances");
    db.update("delete from projection_checkpoints where projection_name = ?", PROJECTION_NAME);

    long currentPos = 0;
    for (var event : store.all(0)) {
      applyEvent(event);
      currentPos = event.globalPosition();
    }

    if (currentPos > 0) {
      db.update(
          "insert into projection_checkpoints(projection_name, last_processed_position, updated_at) " +
          "values(?, ?, now()) on conflict(projection_name) do update set last_processed_position = excluded.last_processed_position, updated_at = now()",
          PROJECTION_NAME, currentPos
      );
    }
  }

  private void applyEvent(EventStore.Stored event) {
    var d = event.data();
    String t = event.type();
    String id = (String) d.get("accountId");
    var ts = event.timestamp() != null
        ? Timestamp.from(event.timestamp())
        : Timestamp.from(Instant.now());

    if (t.equals("AccountOpened")) {
      db.update(
          "insert into account_balances(account_id, owner_name, balance, currency, status, opened_at, updated_at) " +
          "values(?, ?, 0, ?, 'active', ?, ?) on conflict(account_id) do nothing",
          id, d.get("ownerName"), d.get("currency"), ts, ts);

    } else if (t.equals("MoneyDeposited") || t.equals("TransferReceived")
        || t.equals("MoneyWithdrawn") || t.equals("TransferSent")) {
      BigDecimal amount = new BigDecimal(d.get("amount").toString());
      boolean isCredit = t.equals("MoneyDeposited") || t.equals("TransferReceived");

      BigDecimal newBalance;
      if (isCredit) {
        newBalance = db.queryForObject(
            "update account_balances set balance = balance + ?, updated_at = ? where account_id = ? returning balance",
            BigDecimal.class, amount, ts, id);
      } else {
        newBalance = db.queryForObject(
            "update account_balances set balance = balance - ?, updated_at = ? where account_id = ? returning balance",
            BigDecimal.class, amount, ts, id);
      }

      String type = t.equals("MoneyDeposited") ? "deposit"
          : t.equals("MoneyWithdrawn") ? "withdrawal"
          : t.equals("TransferSent") ? "transfer_sent"
          : "transfer_received";

      String counterparty = t.equals("TransferSent") ? (String) d.get("toAccountId")
          : t.equals("TransferReceived") ? (String) d.get("fromAccountId")
          : null;

      db.update(
          "insert into transaction_history(account_id, type, amount, balance_after, description, counterparty_account_id, transfer_id, created_at) " +
          "values(?, ?, ?, ?, ?, ?, ?, ?)",
          id, type, amount, newBalance, d.get("description"), counterparty, d.get("transferId"), ts);

    } else if (t.startsWith("Account")) {
      String status = t.equals("AccountFrozen") ? "frozen"
          : t.equals("AccountClosed") ? "closed"
          : "active";
      db.update("update account_balances set status = ?, updated_at = ? where account_id = ?", status, ts, id);
    }
  }
}
