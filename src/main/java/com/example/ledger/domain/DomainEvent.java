package com.example.ledger.domain;

import java.time.Instant;
import java.util.Map;

public record DomainEvent(String type, Map<String, Object> data, Instant occurredAt) {
  public DomainEvent {
    data = Map.copyOf(data);
  }
}
