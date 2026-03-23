package com.codecomp.codecomp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE = "judge-queue";
    public static final String DLQ = "judge-dlq";

    @Bean
    public Queue queue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return new Queue(DLQ, true);
    }
}