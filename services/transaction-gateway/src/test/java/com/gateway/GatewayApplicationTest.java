package com.gateway;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class GatewayApplicationTest {

    private final GatewayApplication application = new GatewayApplication();

    @Test
    void transactionsTopicUsesExpectedConfiguration() {
        assertTopic(application.transactionsTopic(), "transacoes-solicitadas");
    }

    @Test
    void resultsTopicUsesExpectedConfiguration() {
        assertTopic(application.transactionResultsTopic(), "transacoes-analisadas");
    }

    @Test
    void deadLetterTopicUsesExpectedConfiguration() {
        assertTopic(application.failedTransactionsTopic(), "transacoes-solicitadas.DLT");
    }

    @Test
    void mainDelegatesToSpringApplication() {
        String[] arguments = {"--test"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            GatewayApplication.main(arguments);

            springApplication.verify(() -> SpringApplication.run(GatewayApplication.class, arguments));
        }
    }

    private void assertTopic(NewTopic topic, String expectedName) {
        assertEquals(expectedName, topic.name());
        assertEquals(3, topic.numPartitions());
        assertEquals((short) 1, topic.replicationFactor());
    }
}