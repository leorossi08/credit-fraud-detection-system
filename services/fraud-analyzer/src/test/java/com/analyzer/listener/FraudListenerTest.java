package com.analyzer.listener;

import com.analyzer.dto.TransactionStatusEvent;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@SuppressWarnings("null")
class FraudListenerTest {

    private static final String RESULTS_TOPIC = "transacoes-analisadas";
    private static final String TRANSACTION_ID = "txn-test";

    @Test
    void publishesProcessingAndFraudResultForValidPrediction() {
        KafkaTemplate<String, TransactionStatusEvent> resultTemplate = resultTemplate();
        FraudListener listener = listener(resultTemplate,
                "{\"isFraud\":true,\"fraudProbability\":0.94}");

        listener.consume(Map.of("transactionId", TRANSACTION_ID));

        ArgumentCaptor<TransactionStatusEvent> events = ArgumentCaptor.forClass(TransactionStatusEvent.class);
        verify(resultTemplate, times(2)).send(eq(RESULTS_TOPIC), eq(TRANSACTION_ID), events.capture());
        List<TransactionStatusEvent> published = events.getAllValues();
        assertEquals("PROCESSING", published.get(0).status());
        assertEquals("COMPLETED", published.get(1).status());
        assertEquals(true, published.get(1).isFraud());
        assertEquals(0.94, published.get(1).fraudProbability());
        assertNotNull(published.get(0).occurredAt());
        assertNotNull(published.get(1).occurredAt());
    }

        @Test
        void publishesLegitimatePrediction() {
                KafkaTemplate<String, TransactionStatusEvent> resultTemplate = resultTemplate();
                FraudListener listener = listener(resultTemplate,
                                "{\"isFraud\":false,\"fraudProbability\":0.02}");

                listener.consume(Map.of("transactionId", TRANSACTION_ID));

                ArgumentCaptor<TransactionStatusEvent> event = ArgumentCaptor.forClass(TransactionStatusEvent.class);
                verify(resultTemplate, times(2)).send(eq(RESULTS_TOPIC), eq(TRANSACTION_ID), event.capture());
                assertEquals(false, event.getAllValues().get(1).isFraud());
                assertEquals(0.02, event.getAllValues().get(1).fraudProbability());
        }

    @Test
    void rejectsPredictionWithoutFraudFlag() {
        KafkaTemplate<String, TransactionStatusEvent> resultTemplate = resultTemplate();
        FraudListener listener = listener(resultTemplate, "{\"fraudProbability\":0.2}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> listener.consume(Map.of("transactionId", TRANSACTION_ID)));

        assertEquals("ML service returned an invalid prediction for " + TRANSACTION_ID, error.getMessage());
        verify(resultTemplate).send(eq(RESULTS_TOPIC), eq(TRANSACTION_ID), any(TransactionStatusEvent.class));
    }

        @Test
        void rejectsPredictionWithoutProbability() {
                KafkaTemplate<String, TransactionStatusEvent> resultTemplate = resultTemplate();
                FraudListener listener = listener(resultTemplate, "{\"isFraud\":true}");

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> listener.consume(Map.of("transactionId", TRANSACTION_ID)));

                assertEquals("ML service returned an invalid probability for " + TRANSACTION_ID, error.getMessage());
        }

    @Test
    void rejectsProbabilityOutsideUnitInterval() {
        KafkaTemplate<String, TransactionStatusEvent> resultTemplate = resultTemplate();
        FraudListener listener = listener(resultTemplate,
                "{\"isFraud\":false,\"fraudProbability\":1.1}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> listener.consume(Map.of("transactionId", TRANSACTION_ID)));

        assertEquals("ML service returned an out-of-range probability for " + TRANSACTION_ID, error.getMessage());
        verify(resultTemplate).send(eq(RESULTS_TOPIC), eq(TRANSACTION_ID), any(TransactionStatusEvent.class));
    }

        @Test
        void rejectsNegativeProbability() {
                KafkaTemplate<String, TransactionStatusEvent> resultTemplate = resultTemplate();
                FraudListener listener = listener(resultTemplate,
                                "{\"isFraud\":false,\"fraudProbability\":-0.1}");

                IllegalStateException error = assertThrows(IllegalStateException.class,
                                () -> listener.consume(Map.of("transactionId", TRANSACTION_ID)));

                assertEquals("ML service returned an out-of-range probability for " + TRANSACTION_ID, error.getMessage());
        }

    @SuppressWarnings("unchecked")
    private KafkaTemplate<String, TransactionStatusEvent> resultTemplate() {
        KafkaTemplate<String, TransactionStatusEvent> resultTemplate =
                (KafkaTemplate<String, TransactionStatusEvent>) mock(KafkaTemplate.class);
        when(resultTemplate.send(anyString(), anyString(), any(TransactionStatusEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        return resultTemplate;
    }

    private FraudListener listener(
            KafkaTemplate<String, TransactionStatusEvent> resultTemplate,
            String responseBody) {
        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(responseBody)
                        .build()));
        return new FraudListener(webClientBuilder, resultTemplate, "http://ml-service.test");
    }
}