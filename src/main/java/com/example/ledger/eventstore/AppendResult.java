package com.example.ledger.eventstore;

public record AppendResult(int streamVersion, long globalPosition) {}
