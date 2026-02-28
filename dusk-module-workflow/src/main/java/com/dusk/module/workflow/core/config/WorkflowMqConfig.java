package com.dusk.module.workflow.core.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流MQ配置
 * <p>
 * 声明工作流事件的 RabbitMQ Exchange、Queue 和 Binding。
 * 使用 Topic Exchange，routing key 格式为 {@code workflow.event.{event_type}}，
 * 例如 {@code workflow.event.task_completed}。
 * </p>
 * <p>
 * MQ 是否启用取决于 {@code spring.rabbitmq.is-enabled} 配置，
 * 未启用时事件仅通过 Spring ApplicationEvent 在进程内传播。
 * </p>
 *
 * @author kefuming
 * @see com.dusk.module.workflow.event.WorkflowEventPublisher
 * @see com.dusk.module.workflow.event.WorkflowEventConsumer
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

