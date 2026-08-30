package com.example.ledger.api;

import com.example.ledger.eventstore.EventStore;
import com.example.ledger.service.LedgerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class LedgerController {

  private final LedgerService service;
  private final EventStore store;

  public LedgerController(LedgerService service, EventStore store) {
    this.service = service;
    this.store = store;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("status", "healthy", "timestamp", Instant.now(), "version", "1.0.0", "database", "postgresql");
  }

  @PostMapping("/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> open(@Valid @RequestBody Dtos.Open r) {
    var result = service.open(r);
    return merge(Map.of("message", "Account opened successfully"), result);
  }

  @GetMapping("/accounts")
  public Map<String, Object> list(
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    return service.list(Math.min(Math.max(limit, 1), 200), Math.max(offset, 0));
  }

  @GetMapping("/accounts/{id}")
  public Map<String, Object> account(@PathVariable String id) {
    return service.account(id);
  }

  @GetMapping("/accounts/{id}/events")
  public Map<String, Object> events(@PathVariable String id) {
    return Map.of("streamId", "account-" + id, "events", service.events(id));
  }

  @GetMapping("/accounts/{id}/transactions")
  public Map<String, Object> transactions(
      @PathVariable String id,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    var tx = service.transactions(id, Math.min(Math.max(limit, 1), 200), Math.max(offset, 0));
    return Map.of("transactions", tx, "total", tx.size(), "limit", limit, "offset", offset);
  }

  @PostMapping("/accounts/{id}/deposit")
  public Map<String, Object> deposit(@PathVariable String id, @Valid @RequestBody Dtos.Money r) {
    service.money(id, r.amount(), r.description(), false);
    var a = service.account(id);
    return Map.of("message", "Deposited " + r.amount() + " " + a.get("currency"), "balance", a.get("balance"));
  }

  @PostMapping("/accounts/{id}/withdraw")
  public Map<String, Object> withdraw(@PathVariable String id, @Valid @RequestBody Dtos.Money r) {
    service.money(id, r.amount(), r.description(), true);
    var a = service.account(id);
    return Map.of("message", "Withdrew " + r.amount() + " " + a.get("currency"), "balance", a.get("balance"));
  }

  @PostMapping("/transfers")
  public Map<String, Object> transfer(@Valid @RequestBody Dtos.Transfer r) {
    return merge(Map.of("message", "Transferred " + r.amount()), service.transfer(r));
  }

  @PostMapping("/accounts/{id}/freeze")
  public Map<String, Object> freeze(@PathVariable String id, @Valid @RequestBody Dtos.Reason r) {
    service.manage(id, "freeze");
    return Map.of("message", "Account frozen", "accountId", id);
  }

  @PostMapping("/accounts/{id}/unfreeze")
  public Map<String, Object> unfreeze(@PathVariable String id) {
    service.manage(id, "unfreeze");
    return Map.of("message", "Account unfrozen", "accountId", id);
  }

  @PostMapping("/accounts/{id}/close")
  public Map<String, Object> close(@PathVariable String id, @RequestBody(required = false) Dtos.OptionalReason r) {
    service.manage(id, "close");
    return Map.of("message", "Account closed", "accountId", id);
  }

  @GetMapping("/admin/stats")
  public Map<String, Object> stats() {
    return Map.of(
        "totalEvents", store.count(),
        "projections", List.of(
            Map.of("name", "account-balance", "lastProcessedPosition", store.count()),
            Map.of("name", "transaction-history", "lastProcessedPosition", store.count())),
        "timestamp", Instant.now(),
        "database", "postgresql");
  }

  @PostMapping("/admin/rebuild-projections")
  public Map<String, Object> rebuild() {
    service.rebuild();
    return Map.of("message", "Projections rebuilt successfully", "eventsProcessed", store.count());
  }

  private Map<String, Object> merge(Map<String, Object> a, Map<String, Object> b) {
    var m = new HashMap<>(a);
    m.putAll(b);
    return m;
  }
}
