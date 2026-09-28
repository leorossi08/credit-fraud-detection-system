package com.gateway.model;

import com.gateway.dto.TransactionDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransactionRecordTest {

    @Test
    void queuedCopiesTransactionFieldsAndInitializesStatus() {
        TransactionDTO transaction = transaction();

        TransactionRecord record = TransactionRecord.queued(transaction);

        assertEquals(transaction.transactionId(), record.getTransactionId());
        assertEquals(transaction.cardId(), record.getCardId());
        assertEquals(transaction.amount(), record.getAmount());
        assertEquals(transaction.timestamp(), record.getTransactionTimestamp());
        assertEquals(TransactionStatus.QUEUED, record.getStatus());
        assertNull(record.getIsFraud());
        assertNull(record.getFraudProbability());
    }

    @Test
    void persistenceCallbacksSetAndRetainCreationTime() {
        TransactionRecord record = TransactionRecord.queued(transaction());

        record.onCreate();
        Instant createdAt = record.getCreatedAt();
        Instant initialUpdatedAt = record.getUpdatedAt();
        record.onUpdate();

        assertNotNull(createdAt);
        assertEquals(createdAt, initialUpdatedAt);
        assertEquals(createdAt, record.getCreatedAt());
        assertFalse(record.getUpdatedAt().isBefore(createdAt));
    }

    @Test
    void stateTransitionsUpdateStatusAndClearOrSetMessage() {
        TransactionRecord record = TransactionRecord.queued(transaction());

        record.fail("analysis failed");
        assertEquals(TransactionStatus.FAILED, record.getStatus());
        assertEquals("analysis failed", record.getStatusMessage());

        record.markProcessing();
        assertEquals(TransactionStatus.PROCESSING, record.getStatus());
        assertNull(record.getStatusMessage());

        record.complete(true, 0.91);
        assertEquals(TransactionStatus.COMPLETED, record.getStatus());
        assertEquals(true, record.getIsFraud());
        assertEquals(0.91, record.getFraudProbability());
        assertNull(record.getStatusMessage());
    }

    private TransactionDTO transaction() {
        return new TransactionDTO("txn-record", "card-record", 12.5,
                "2026-09-26T12:00:00Z", Collections.nCopies(30, 0.0));
    }
}