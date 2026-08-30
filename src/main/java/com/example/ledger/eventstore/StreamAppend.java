package com.example.ledger.eventstore;

import java.util.List;
import java.util.Map;

public record StreamAppend(String streamId, List<Map<String, Object>> events, int expectedVersion) {}
