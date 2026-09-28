package com.gateway.service;

import com.gateway.dto.TransactionDTO;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class KafkaProducerService {

    private static final String TOPIC = "transacoes-solicitadas";
    private final KafkaTemplate<String, TransactionDTO> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, TransactionDTO> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, TransactionDTO>> sendTransaction(TransactionDTO transaction) {
        // Usamos o cardId como chave para garantir que transações do mesmo cartão caiam na mesma partição
        String cardId = transaction.cardId();
        if (cardId == null || cardId.isBlank()) {
            cardId = "unknown-card";
        }

        return kafkaTemplate.send(TOPIC, cardId, transaction);
    }
}