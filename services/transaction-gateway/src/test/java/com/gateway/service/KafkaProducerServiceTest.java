package com.gateway.service;

import com.gateway.dto.TransactionDTO;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class KafkaProducerServiceTest {

    @SuppressWarnings("unchecked")
    @Test
    void sendTransactionUsesCardIdAsKeyAndSendsTheTransaction() {
        KafkaTemplate<String, TransactionDTO> kafkaTemplate =
                (KafkaTemplate<String, TransactionDTO>) mock(KafkaTemplate.class);
        KafkaProducerService service = new KafkaProducerService(kafkaTemplate);

        TransactionDTO transaction = new TransactionDTO(
                "txn-123",
                "card-456",
                42.75,
                "2026-07-26T10:00:00Z",
                List.of(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0,
                    1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.0,
                    2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 3.0)
        );

        service.sendTransaction(transaction);

        verify(kafkaTemplate).send("transacoes-solicitadas", "card-456", transaction);
    }
}
