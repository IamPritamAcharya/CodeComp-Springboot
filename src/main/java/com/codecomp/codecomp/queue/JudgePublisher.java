package com.codecomp.codecomp.queue;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import com.codecomp.codecomp.config.RabbitMQConfig;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JudgePublisher {
    
    private final RabbitTemplate rabbitTemplate;

    public void send(Long submissionId) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.QUEUE, submissionId);
    }

}
