package com.example.ledger.projections;

import com.example.ledger.eventstore.EventStore;

public interface Projection {
  String name();
  void handle(EventStore.Stored event);
  void reset();
}
