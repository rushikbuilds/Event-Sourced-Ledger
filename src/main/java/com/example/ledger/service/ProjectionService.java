package com.example.ledger.service;

import com.example.ledger.eventstore.EventStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.*;

@Service
public class ProjectionService {

  private final JdbcTemplate db;
  private final EventStore store;

  public ProjectionService(JdbcTemplate db, EventStore store) {
    this.db = db;
    this.store = store;
  }

  @PostConstruct
  public void catchUp() {
    rebuild();
  }

  @Transactional
  public void rebuild() {
    db.update("delete from transaction_history");
    db.update("delete from account_balances");
    Map<String, BigDecimal> balances = new HashMap<>();

    for (var event : store.all(0)) {
      var d = event.data();
      String t = event.type();
      String id = (String) d.get("accountId");
      var ts = event.timestamp() != null
          ? Timestamp.from(event.timestamp())
          : Timestamp.from(java.time.Instant.now());

      if (t.equals("AccountOpened")) {
        balances.put(id, BigDecimal.ZERO);
        db.update(
            "insert into account_balances(account_id,owner_name,balance,currency,status,opened_at,updated_at) values(?,?,0,?,'active',?,?)",
            id, d.get("ownerName"), d.get("currency"), ts, ts);

      } else if (t.equals("MoneyDeposited") || t.equals("TransferReceived")
          || t.equals("MoneyWithdrawn") || t.equals("TransferSent")) {
        BigDecimal amount = new BigDecimal(d.get("amount").toString());
        BigDecimal balance = (t.equals("MoneyDeposited") || t.equals("TransferReceived"))
            ? balances.get(id).add(amount)
            : balances.get(id).subtract(amount);
        balances.put(id, balance);
        db.update("update account_balances set balance=?,updated_at=? where account_id=?", balance, ts, id);

        String type = t.equals("MoneyDeposited") ? "deposit"
            : t.equals("MoneyWithdrawn") ? "withdrawal"
            : t.equals("TransferSent") ? "transfer_sent"
            : "transfer_received";
        db.update(
            "insert into transaction_history(account_id,type,amount,balance_after,description,counterparty_account_id,transfer_id,created_at) values(?,?,?,?,?,?,?,?)",
            id, type, amount, balance, d.get("description"),
            d.get(t.equals("TransferSent") ? "toAccountId" : "fromAccountId"),
            d.get("transferId"), ts);

      } else if (t.startsWith("Account")) {
        String status = t.equals("AccountFrozen") ? "frozen"
            : t.equals("AccountClosed") ? "closed"
            : "active";
        db.update("update account_balances set status=?,updated_at=? where account_id=?", status, ts, id);
      }
    }
  }
}
