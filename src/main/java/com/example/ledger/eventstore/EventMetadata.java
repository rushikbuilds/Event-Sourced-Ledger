package com.example.ledger.eventstore;

import java.time.Instant;
import java.util.UUID;

public record EventMetadata(String correlationId, String causationId, String userId, Instant timestamp) {

  public static EventMetadata create() {
    return new EventMetadata(UUID.randomUUID().toString(), null, null, Instant.now());
  }
}
