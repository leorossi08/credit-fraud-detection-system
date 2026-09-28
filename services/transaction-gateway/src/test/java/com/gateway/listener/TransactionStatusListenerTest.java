package com.gateway.listener;

import com.gateway.model.TransactionStatusEvent;
import com.gateway.service.TransactionLifecycleService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransactionStatusListenerTest {

    private final TransactionLifecycleService lifecycleService = mock(TransactionLifecycleService.class);
    private final TransactionStatusListener listener = new TransactionStatusListener(lifecycleService);

    @Test
    void forwardsAnalysisStatusEventToLifecycleService() {
        TransactionStatusEvent event = new TransactionStatusEvent(
                "txn-listener", "COMPLETED", false, 0.02, null, Instant.parse("2026-09-26T12:00:00Z"));

        listener.onStatus(event);

        verify(lifecycleService).applyStatusEvent(event);
    }

    @Test
    void marksDeadLetterTransactionAsFailed() {
        TransactionStatusEvent event = new TransactionStatusEvent(
                "txn-dead-letter", "FAILED", null, null, "analysis error",
                Instant.parse("2026-09-26T12:00:00Z"));

        listener.onDeadLetter(event);

        verify(lifecycleService).markFailed("txn-dead-letter",
                "Analysis failed after retries; inspect the Kafka DLT.");
    }
}