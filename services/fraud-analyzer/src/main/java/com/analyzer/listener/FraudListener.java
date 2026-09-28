package com.analyzer.listener;

import com.analyzer.dto.TransactionStatusEvent;
import java.util.Map;
import java.time.Duration;
import java.util.Objects;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class FraudListener {

    private static final String RESULTS_TOPIC = "transacoes-analisadas";
    private static final Logger logger = LoggerFactory.getLogger(FraudListener.class);
    private final WebClient mlClient;
    private final KafkaTemplate<String, TransactionStatusEvent> resultTemplate;

    public FraudListener(
            WebClient.Builder webClientBuilder,
            KafkaTemplate<String, TransactionStatusEvent> resultTemplate,
            @Value("${fraud.ml-service-url}") String mlServiceUrl) {
        this.mlClient = webClientBuilder.baseUrl(Objects.requireNonNull(mlServiceUrl)).build();
        this.resultTemplate = resultTemplate;
    }

    @KafkaListener(topics = "transacoes-solicitadas", groupId = "fraud-analyzer-group")
    public void consume(Map<String, Object> transaction) {
        String transactionId = String.valueOf(transaction.get("transactionId"));
        publishStatus(transactionId, "PROCESSING", null, null, null);

        Map<String, Object> response = mlClient.post()
                .uri("/predict")
                .bodyValue(transaction)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block(Duration.ofSeconds(10));

        if (response == null || !(response.get("isFraud") instanceof Boolean fraudFlag)) {
            throw new IllegalStateException("ML service returned an invalid prediction for " + transactionId);
        }
        if (!(response.get("fraudProbability") instanceof Number probabilityValue)) {
            throw new IllegalStateException("ML service returned an invalid probability for " + transactionId);
        }

        double fraudProbability = probabilityValue.doubleValue();
        if (!Double.isFinite(fraudProbability) || fraudProbability < 0.0 || fraudProbability > 1.0) {
            throw new IllegalStateException("ML service returned an out-of-range probability for " + transactionId);
        }

        publishStatus(transactionId, "COMPLETED", fraudFlag, fraudProbability, null);

        if (fraudFlag) {
            logger.warn("Fraud detected for transaction {} with probability {}", transactionId,
                    fraudProbability);
        } else {
            logger.info("Transaction {} classified as legitimate with probability {}", transactionId,
                    fraudProbability);
        }
    }

    private void publishStatus(
            String transactionId,
            String status,
            Boolean isFraud,
            Double fraudProbability,
            String message) {
        TransactionStatusEvent event = new TransactionStatusEvent(
                transactionId, status, isFraud, fraudProbability, message, Instant.now());
        try {
                resultTemplate.send(RESULTS_TOPIC, Objects.requireNonNull(transactionId), event)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing transaction status.", error);
        } catch (Exception error) {
            throw new IllegalStateException("Could not publish transaction status to Kafka.", error);
        }
    }
}