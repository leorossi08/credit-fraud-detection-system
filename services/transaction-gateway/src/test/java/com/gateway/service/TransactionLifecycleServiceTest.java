package com.gateway.service;

import com.gateway.dto.TransactionDTO;
import com.gateway.model.TransactionRecord;
import com.gateway.model.TransactionStatus;
import com.gateway.model.TransactionStatusEvent;
import com.gateway.repository.TransactionRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("null")
class TransactionLifecycleServiceTest {

    @Test
    void submitPersistsQueuedBeforePublishing() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        KafkaProducerService producer = mock(KafkaProducerService.class);
        TransactionLifecycleService service = new TransactionLifecycleService(repository, producer);
        TransactionDTO transaction = transaction();
        when(repository.existsById(transaction.transactionId())).thenReturn(false);
        when(repository.save(any(TransactionRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(producer.sendTransaction(transaction)).thenReturn(CompletableFuture.completedFuture(null));

        var response = service.submit(transaction).join();

        assertEquals("QUEUED", response.status());
        assertEquals(transaction.transactionId(), response.transactionId());
        verify(repository).save(any(TransactionRecord.class));
        verify(producer).sendTransaction(transaction);
    }

    @Test
    void submitRejectsDuplicateTransactionId() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        KafkaProducerService producer = mock(KafkaProducerService.class);
        TransactionLifecycleService service = new TransactionLifecycleService(repository, producer);
        TransactionDTO transaction = transaction();
        when(repository.existsById(transaction.transactionId())).thenReturn(true);

        assertThrows(RuntimeException.class, () -> service.submit(transaction));
        verify(repository, never()).save(any(TransactionRecord.class));
        verify(producer, never()).sendTransaction(any());
    }

    @Test
    void applyStatusEventPersistsFraudResult() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        TransactionLifecycleService service = new TransactionLifecycleService(repository, mock(KafkaProducerService.class));
        TransactionRecord record = TransactionRecord.queued(transaction());
        when(repository.findById("txn-test")).thenReturn(Optional.of(record));
        when(repository.save(record)).thenReturn(record);

        service.applyStatusEvent(new TransactionStatusEvent(
                "txn-test", "COMPLETED", true, 0.97, null, Instant.now()));

        assertEquals(TransactionStatus.COMPLETED, record.getStatus());
        assertEquals(true, record.getIsFraud());
        assertEquals(0.97, record.getFraudProbability());
        verify(repository).save(record);
    }

    @Test
    void terminalStatusDoesNotRegressOnDelayedProcessingEvent() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        TransactionLifecycleService service = new TransactionLifecycleService(repository, mock(KafkaProducerService.class));
        TransactionRecord record = TransactionRecord.queued(transaction());
        record.complete(true, 0.97);
        when(repository.findById("txn-test")).thenReturn(Optional.of(record));

        service.applyStatusEvent(new TransactionStatusEvent(
                "txn-test", "PROCESSING", null, null, null, Instant.now()));

        assertEquals(TransactionStatus.COMPLETED, record.getStatus());
        verify(repository, never()).save(record);
    }

    @Test
    void recentRejectsUnboundedLimits() {
        TransactionLifecycleService service = new TransactionLifecycleService(
                mock(TransactionRecordRepository.class), mock(KafkaProducerService.class));

        assertThrows(RuntimeException.class, () -> service.recent(101));
    }

        @Test
        void recentReturnsMappedRecordsForRequestedLimit() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        TransactionRecord record = TransactionRecord.queued(transaction());
        when(repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 25))).thenReturn(List.of(record));
        TransactionLifecycleService service = new TransactionLifecycleService(repository,
            mock(KafkaProducerService.class));

        List<com.gateway.dto.TransactionStatusResponse> responses = service.recent(25);

        assertEquals(1, responses.size());
        assertEquals("txn-test", responses.get(0).transactionId());
        assertEquals(TransactionStatus.QUEUED, responses.get(0).status());
        verify(repository).findAllByOrderByCreatedAtDesc(PageRequest.of(0, 25));
        }

        @Test
        void findReturnsMappedRecord() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        when(repository.findById("txn-test")).thenReturn(Optional.of(TransactionRecord.queued(transaction())));
        TransactionLifecycleService service = new TransactionLifecycleService(repository,
            mock(KafkaProducerService.class));

        var response = service.find("txn-test");

        assertEquals("txn-test", response.transactionId());
        assertEquals(TransactionStatus.QUEUED, response.status());
        }

        @Test
        void findThrowsNotFoundForUnknownTransaction() {
        TransactionLifecycleService service = new TransactionLifecycleService(
            mock(TransactionRecordRepository.class), mock(KafkaProducerService.class));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
            () -> service.find("missing"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        }

        @Test
        void applyStatusEventsUpdatesProcessingAndFailureStates() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        TransactionRecord record = TransactionRecord.queued(transaction());
        when(repository.findById("txn-test")).thenReturn(Optional.of(record));
        TransactionLifecycleService service = new TransactionLifecycleService(repository,
            mock(KafkaProducerService.class));

        service.applyStatusEvent(new TransactionStatusEvent(
            "txn-test", "PROCESSING", null, null, null, Instant.now()));
        assertEquals(TransactionStatus.PROCESSING, record.getStatus());

        service.applyStatusEvent(new TransactionStatusEvent(
            "txn-test", "FAILED", null, null, "analysis failed", Instant.now()));

        assertEquals(TransactionStatus.FAILED, record.getStatus());
        assertEquals("analysis failed", record.getStatusMessage());
        verify(repository, times(2)).save(record);
        }

        @Test
        void completionDefaultsMissingProbabilityToZero() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        TransactionRecord record = TransactionRecord.queued(transaction());
        when(repository.findById("txn-test")).thenReturn(Optional.of(record));
        TransactionLifecycleService service = new TransactionLifecycleService(repository,
            mock(KafkaProducerService.class));

        service.applyStatusEvent(new TransactionStatusEvent(
            "txn-test", "COMPLETED", false, null, null, Instant.now()));

        assertEquals(TransactionStatus.COMPLETED, record.getStatus());
        assertEquals(false, record.getIsFraud());
        assertEquals(0.0, record.getFraudProbability());
        verify(repository).save(record);
        }

        @Test
        void unknownStatusIsRejectedWithoutSaving() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        when(repository.findById("txn-test")).thenReturn(Optional.of(TransactionRecord.queued(transaction())));
        TransactionLifecycleService service = new TransactionLifecycleService(repository,
            mock(KafkaProducerService.class));

        assertThrows(IllegalArgumentException.class, () -> service.applyStatusEvent(new TransactionStatusEvent(
            "txn-test", "UNKNOWN", null, null, null, Instant.now())));

        verify(repository, never()).save(any(TransactionRecord.class));
        }

        @Test
        void failedPublicationMarksPersistedRecordAndReturnsUnavailable() {
        TransactionRecordRepository repository = mock(TransactionRecordRepository.class);
        KafkaProducerService producer = mock(KafkaProducerService.class);
        TransactionDTO transaction = transaction();
        AtomicReference<TransactionRecord> persisted = new AtomicReference<>();
        when(repository.existsById(transaction.transactionId())).thenReturn(false);
        when(repository.save(any(TransactionRecord.class))).thenAnswer(invocation -> {
            TransactionRecord record = invocation.getArgument(0);
            persisted.set(record);
            return record;
        });
        when(repository.findById(transaction.transactionId()))
            .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(producer.sendTransaction(transaction))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        TransactionLifecycleService service = new TransactionLifecycleService(repository, producer);

        CompletionException error = assertThrows(CompletionException.class,
            () -> service.submit(transaction).join());

        ResponseStatusException responseError = assertInstanceOf(ResponseStatusException.class, error.getCause());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, responseError.getStatusCode());
        assertEquals(TransactionStatus.FAILED, persisted.get().getStatus());
        assertEquals("Could not publish transaction to Kafka.", persisted.get().getStatusMessage());
        verify(repository, times(2)).save(any(TransactionRecord.class));
        }

    private TransactionDTO transaction() {
        return new TransactionDTO(
                "txn-test",
                "card-test",
                10.0,
                "2026-09-26T12:00:00Z",
                java.util.Collections.nCopies(30, 0.0));
    }
}