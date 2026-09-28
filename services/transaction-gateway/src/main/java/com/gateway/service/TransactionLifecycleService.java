package com.gateway.service;

import com.gateway.dto.TransactionDTO;
import com.gateway.dto.TransactionStatusResponse;
import com.gateway.dto.TransactionSubmissionResponse;
import com.gateway.model.TransactionRecord;
import com.gateway.repository.TransactionRecordRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
public class TransactionLifecycleService {

    private final TransactionRecordRepository repository;
    private final KafkaProducerService producerService;

    public TransactionLifecycleService(
            TransactionRecordRepository repository,
            KafkaProducerService producerService) {
        this.repository = repository;
        this.producerService = producerService;
    }

    public CompletableFuture<TransactionSubmissionResponse> submit(TransactionDTO transaction) {
        String transactionId = Objects.requireNonNull(transaction.transactionId());
        if (repository.existsById(transactionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "transactionId already exists.");
        }

        repository.save(Objects.requireNonNull(TransactionRecord.queued(transaction)));
        return producerService.sendTransaction(transaction)
                .thenApply(result -> new TransactionSubmissionResponse(transactionId, "QUEUED"))
                .exceptionally(error -> {
                    markFailed(transactionId, "Could not publish transaction to Kafka.");
                    throw new CompletionException(new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Kafka could not accept the transaction.",
                            error));
                });
    }

    public List<TransactionStatusResponse> recent(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100.");
        }
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit))
                .stream()
                .map(TransactionStatusResponse::from)
                .toList();
    }

    public TransactionStatusResponse find(String transactionId) {
        return repository.findById(Objects.requireNonNull(transactionId))
                .map(TransactionStatusResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found."));
    }

    public void applyStatusEvent(com.gateway.model.TransactionStatusEvent event) {
        repository.findById(Objects.requireNonNull(event.transactionId())).ifPresent(record -> {
            if (isTerminal(record)) {
                return;
            }
            switch (event.status()) {
                case "PROCESSING" -> record.markProcessing();
                case "COMPLETED" -> record.complete(Boolean.TRUE.equals(event.isFraud()),
                        event.fraudProbability() == null ? 0.0 : event.fraudProbability());
                case "FAILED" -> record.fail(event.message());
                default -> throw new IllegalArgumentException("Unknown transaction status: " + event.status());
            }
            repository.save(record);
        });
    }

    public void markFailed(String transactionId, String message) {
        repository.findById(Objects.requireNonNull(transactionId)).ifPresent(record -> {
            if (isTerminal(record)) {
                return;
            }
            record.fail(message);
            repository.save(record);
        });
    }

    private boolean isTerminal(TransactionRecord record) {
        return record.getStatus() == com.gateway.model.TransactionStatus.COMPLETED
                || record.getStatus() == com.gateway.model.TransactionStatus.FAILED;
    }
}