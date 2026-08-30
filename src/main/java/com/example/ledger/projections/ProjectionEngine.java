package com.example.ledger.projections;

import com.example.ledger.eventstore.EventStore;
import com.example.ledger.service.ProjectionService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProjectionEngine {

  private final EventStore eventStore;
  private final ProjectionService projectionService;

  public ProjectionEngine(EventStore eventStore, ProjectionService projectionService) {
    this.eventStore = eventStore;
    this.projectionService = projectionService;
  }

  public void processNewEvents() {
    projectionService.rebuild();
  }

  public long rebuildAll() {
    projectionService.rebuild();
    return eventStore.count();
  }

  public List<String> status() {
    return List.of("account-balance", "transaction-history");
  }
}
