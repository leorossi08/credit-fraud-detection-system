package com.gateway.dto;

import com.gateway.model.TransactionRecord;
import com.gateway.model.TransactionStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransactionStatusResponseTest {

    @Test
    void mapsPersistedTransactionFieldsToResponse() {
        TransactionRecord record = TransactionRecord.queued(new TransactionDTO(
                "txn-response", "card-response", 14.25, "2026-09-26T12:00:00Z",
                Collections.nCopies(30, 0.0)));
        record.complete(true, 0.88);

        TransactionStatusResponse response = TransactionStatusResponse.from(record);

        assertEquals("txn-response", response.transactionId());
        assertEquals("card-response", response.cardId());
        assertEquals(14.25, response.amount());
        assertEquals("2026-09-26T12:00:00Z", response.timestamp());
        assertEquals(TransactionStatus.COMPLETED, response.status());
        assertEquals(true, response.isFraud());
        assertEquals(0.88, response.fraudProbability());
        assertNull(response.message());
        assertNull(response.createdAt());
        assertNull(response.updatedAt());
    }
}