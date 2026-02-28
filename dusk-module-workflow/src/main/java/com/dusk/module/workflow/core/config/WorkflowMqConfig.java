package com.dusk.module.workflow.core.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流MQ配置
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Configuration
public class WorkflowMqConfig {

    public static final String WORKFLOW_EVENT_EXCHANGE = "workflow.event.exchange";
    public static final String WORKFLOW_EVENT_QUEUE = "workflow.event.queue";
    public static final String WORKFLOW_EVENT_ROUTING_KEY = "workflow.event.#";

    @Bean
    public TopicExchange workflowEventExchange() {
        return new TopicExchange(WORKFLOW_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue workflowEventQueue() {
        return QueueBuilder.durable(WORKFLOW_EVENT_QUEUE).build();
    }

    @Bean
    public Binding workflowEventBinding(Queue workflowEventQueue, TopicExchange workflowEventExchange) {
        return BindingBuilder.bind(workflowEventQueue).to(workflowEventExchange).with(WORKFLOW_EVENT_ROUTING_KEY);
    }
}

