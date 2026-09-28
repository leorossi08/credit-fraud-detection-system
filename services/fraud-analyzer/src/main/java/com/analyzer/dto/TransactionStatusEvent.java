package com.analyzer.dto;

import java.time.Instant;

public record TransactionStatusEvent(
        String transactionId,
        String status,
        Boolean isFraud,
        Double fraudProbability,
        String message,
        Instant occurredAt
) {
}