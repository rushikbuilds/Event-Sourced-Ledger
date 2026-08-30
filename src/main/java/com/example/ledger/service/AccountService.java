package com.example.ledger.service;

import com.example.ledger.api.Dtos;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class AccountService {

  private final LedgerService ledger;

  public AccountService(LedgerService ledger) {
    this.ledger = ledger;
  }

  public Map<String, Object> open(Dtos.Open request) {
    return ledger.open(request);
  }

  public void deposit(String id, BigDecimal amount, String description) {
    ledger.money(id, amount, description, false);
  }

  public void withdraw(String id, BigDecimal amount, String description) {
    ledger.money(id, amount, description, true);
  }

  public Map<String, Object> get(String id) {
    return ledger.account(id);
  }
}
