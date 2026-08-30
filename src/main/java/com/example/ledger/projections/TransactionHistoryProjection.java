package com.example.ledger.projections;

import com.example.ledger.eventstore.EventStore;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TransactionHistoryProjection implements Projection {

  private final JdbcTemplate jdbc;

  public TransactionHistoryProjection(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String name() {
    return "transaction-history";
  }

  @Override
  public void handle(EventStore.Stored event) {
  }

  @Override
  public void reset() {
    jdbc.update("delete from transaction_history");
  }
}
