package com.analyzer;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@SpringBootApplication
public class FraudAnalyzerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudAnalyzerApplication.class, args);
    }

    @Bean
    NewTopic deadLetterTopic() {
        return TopicBuilder.name("transacoes-solicitadas.DLT").partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic transactionResultsTopic() {
        return TopicBuilder.name("transacoes-analisadas").partitions(3).replicas(1).build();
    }
}