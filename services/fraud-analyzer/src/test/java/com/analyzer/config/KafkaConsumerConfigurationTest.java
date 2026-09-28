package com.analyzer.config;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigurationTest {

    @SuppressWarnings("unchecked")
    @Test
    void configuresDefaultErrorHandler() {
        KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

        CommonErrorHandler errorHandler = new KafkaConsumerConfiguration().kafkaErrorHandler(kafkaTemplate);

        assertInstanceOf(DefaultErrorHandler.class, errorHandler);
    }
}