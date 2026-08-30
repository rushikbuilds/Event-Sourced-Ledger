package com.example.ledger.projections;

import com.example.ledger.eventstore.EventStore;
import org.springframework.jdbc.core.JdbcTemplate;

public final class AccountBalanceProjection implements Projection {

  private final JdbcTemplate jdbc;

  public AccountBalanceProjection(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public String name() {
    return "account-balance";
  }

  @Override
  public void handle(EventStore.Stored event) {
  }

  @Override
  public void reset() {
    jdbc.update("delete from account_balances");
  }
}
