package com.gateway.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransactionDTOTest {

    @Test
    void recordExposesConstructorArguments() {
        List<Double> features = java.util.Collections.nCopies(30, 0.1);

        TransactionDTO transaction = new TransactionDTO(
                "txn-123",
                "card-456",
                42.75,
                "2026-07-26T10:00:00Z",
                features
        );

        assertEquals("txn-123", transaction.transactionId());
        assertEquals("card-456", transaction.cardId());
        assertEquals(42.75, transaction.amount());
        assertEquals("2026-07-26T10:00:00Z", transaction.timestamp());
        assertEquals(features, transaction.features());
        assertNotNull(transaction.toString());
    }
}
