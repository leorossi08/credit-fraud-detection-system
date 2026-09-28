package com.gateway.dto;

import com.gateway.model.TransactionRecord;
import com.gateway.model.TransactionStatus;

import java.time.Instant;

public record TransactionStatusResponse(
        String transactionId,
        String cardId,
        double amount,
        String timestamp,
        TransactionStatus status,
        Boolean isFraud,
        Double fraudProbability,
        String message,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransactionStatusResponse from(TransactionRecord record) {
        return new TransactionStatusResponse(
                record.getTransactionId(),
                record.getCardId(),
                record.getAmount(),
                record.getTransactionTimestamp(),
                record.getStatus(),
                record.getIsFraud(),
                record.getFraudProbability(),
                record.getStatusMessage(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}