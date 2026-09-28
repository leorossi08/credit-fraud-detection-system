package com.gateway.model;

import com.gateway.dto.TransactionDTO;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "transactions")
public class TransactionRecord {

    @Id
    @Column(name = "transaction_id", nullable = false, updatable = false, length = 100)
    private String transactionId;

    @Column(name = "card_id", nullable = false, length = 100)
    private String cardId;

    @Column(nullable = false)
    private double amount;

    @Column(name = "transaction_timestamp", nullable = false, length = 80)
    private String transactionTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "is_fraud")
    private Boolean isFraud;

    @Column(name = "fraud_probability")
    private Double fraudProbability;

    @Column(name = "status_message", length = 500)
    private String statusMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TransactionRecord() {
    }

    public static TransactionRecord queued(TransactionDTO transaction) {
        TransactionRecord record = new TransactionRecord();
        record.transactionId = transaction.transactionId();
        record.cardId = transaction.cardId();
        record.amount = transaction.amount();
        record.transactionTimestamp = transaction.timestamp();
        record.status = TransactionStatus.QUEUED;
        return record;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markProcessing() {
        status = TransactionStatus.PROCESSING;
        statusMessage = null;
    }

    public void complete(boolean fraud, double probability) {
        status = TransactionStatus.COMPLETED;
        isFraud = fraud;
        fraudProbability = probability;
        statusMessage = null;
    }

    public void fail(String message) {
        status = TransactionStatus.FAILED;
        statusMessage = message;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getCardId() {
        return cardId;
    }

    public double getAmount() {
        return amount;
    }

    public String getTransactionTimestamp() {
        return transactionTimestamp;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public Boolean getIsFraud() {
        return isFraud;
    }

    public Double getFraudProbability() {
        return fraudProbability;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}