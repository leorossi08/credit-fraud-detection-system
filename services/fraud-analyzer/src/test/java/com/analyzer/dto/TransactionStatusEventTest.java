package com.analyzer.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionStatusEventTest {

    @Test
    void recordExposesAllEventValues() {
        Instant occurredAt = Instant.parse("2026-09-26T12:00:00Z");
        TransactionStatusEvent event = new TransactionStatusEvent(
                "txn-test", "COMPLETED", true, 0.91, null, occurredAt);

        assertEquals("txn-test", event.transactionId());
        assertEquals("COMPLETED", event.status());
        assertEquals(true, event.isFraud());
        assertEquals(0.91, event.fraudProbability());
        assertEquals(null, event.message());
        assertEquals(occurredAt, event.occurredAt());
        assertEquals(event, new TransactionStatusEvent(
                "txn-test", "COMPLETED", true, 0.91, null, occurredAt));
    }
}