package com.example.ledger.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;

@Repository
public class EventStore {

  public record Stored(
      long globalPosition,
      String streamId,
      int version,
      String type,
      Map<String, Object> data,
      Map<String, Object> metadata,
      Instant timestamp) {
  }

  public record Append(String stream, List<Map<String, Object>> events, int expected) {
  }

  private final JdbcTemplate db;
  private final ObjectMapper json;

  public EventStore(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  public int version(String stream) {
    Integer v = db.queryForObject("select coalesce(max(version),0) from events where stream_id=?", Integer.class, stream);
    return v == null ? 0 : v;
  }

  public void append(String stream, List<Map<String, Object>> events, int expected) {
    int current = version(stream);
    if (current != expected) {
      throw new com.example.ledger.domain.DomainException(
          "CONCURRENCY_CONFLICT", "Concurrency conflict on stream '" + stream + "'", 409);
    }
    int v = expected;
    for (var event : events) {
      v++;
      insert(stream, v, event);
    }
  }

  public void appendAtomic(List<Append> appends) {
    for (var a : appends) {
      append(a.stream(), a.events(), a.expected());
    }
  }

  private void insert(String stream, int version, Map<String, Object> event) {
    try {
      db.update(
          "insert into events(stream_id,version,type,data,metadata) values(?,?,?,?::jsonb,?::jsonb)",
          stream, version, event.get("type"),
          json.writeValueAsString(event),
          json.writeValueAsString(Map.of(
              "correlationId", UUID.randomUUID().toString(),
              "timestamp", Instant.now().toString())));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  public List<Stored> stream(String stream) {
    return db.query(
        "select global_position,stream_id,version,type,data,metadata,timestamp from events where stream_id=? order by version",
        (r, n) -> new Stored(
            r.getLong(1), r.getString(2), r.getInt(3), r.getString(4),
            read(r.getString(5)), read(r.getString(6)), r.getTimestamp(7).toInstant()),
        stream);
  }

  public List<Stored> all(long from) {
    return db.query(
        "select global_position,stream_id,version,type,data,metadata,timestamp from events where global_position>? order by global_position",
        (r, n) -> new Stored(
            r.getLong(1), r.getString(2), r.getInt(3), r.getString(4),
            read(r.getString(5)), read(r.getString(6)), r.getTimestamp(7).toInstant()),
        from);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> read(String value) {
    try {
      return json.readValue(value, Map.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public long count() {
    Long n = db.queryForObject("select count(*) from events", Long.class);
    return n == null ? 0 : n;
  }
}
