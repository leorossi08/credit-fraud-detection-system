package com.analyzer;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

class FraudAnalyzerApplicationTest {

    private final FraudAnalyzerApplication application = new FraudAnalyzerApplication();

    @Test
    void deadLetterTopicUsesExpectedConfiguration() {
        NewTopic topic = application.deadLetterTopic();

        assertEquals("transacoes-solicitadas.DLT", topic.name());
        assertEquals(3, topic.numPartitions());
        assertEquals((short) 1, topic.replicationFactor());
    }

    @Test
    void transactionResultsTopicUsesExpectedConfiguration() {
        NewTopic topic = application.transactionResultsTopic();

        assertEquals("transacoes-analisadas", topic.name());
        assertEquals(3, topic.numPartitions());
        assertEquals((short) 1, topic.replicationFactor());
    }

    @Test
    void mainDelegatesToSpringApplication() {
        String[] arguments = {"--test"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            FraudAnalyzerApplication.main(arguments);

            springApplication.verify(() -> SpringApplication.run(FraudAnalyzerApplication.class, arguments));
        }
    }
}