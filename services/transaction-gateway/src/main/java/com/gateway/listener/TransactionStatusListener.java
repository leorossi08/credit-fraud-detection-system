package com.gateway.listener;

import com.gateway.model.TransactionStatusEvent;
import com.gateway.service.TransactionLifecycleService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TransactionStatusListener {

    private final TransactionLifecycleService lifecycleService;

    public TransactionStatusListener(TransactionLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @KafkaListener(topics = "transacoes-analisadas", groupId = "transaction-status-group")
    public void onStatus(TransactionStatusEvent event) {
        lifecycleService.applyStatusEvent(event);
    }

    @KafkaListener(topics = "transacoes-solicitadas.DLT", groupId = "transaction-dlt-status-group")
    public void onDeadLetter(TransactionStatusEvent failedTransaction) {
        lifecycleService.markFailed(failedTransaction.transactionId(),
                "Analysis failed after retries; inspect the Kafka DLT.");
    }
}