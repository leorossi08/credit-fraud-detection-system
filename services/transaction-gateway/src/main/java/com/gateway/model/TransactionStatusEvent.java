package com.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionStatusEvent(
        String transactionId,
        String status,
        Boolean isFraud,
        Double fraudProbability,
        String message,
        Instant occurredAt
) {
}