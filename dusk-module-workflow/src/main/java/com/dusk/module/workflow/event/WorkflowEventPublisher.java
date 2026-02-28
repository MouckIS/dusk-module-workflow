package com.dusk.module.workflow.event;

import com.dusk.common.core.auth.authentication.LoginUserIdContextHolder;
import com.dusk.module.workflow.core.config.WorkflowMqConfig;
import com.dusk.workflow.dto.WorkflowEventDto;
import com.dusk.workflow.enums.WorkflowEventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流事件发布器
 * <p>
 * 采用 <b>双通道发布</b> 策略：
 * <ol>
 *   <li><b>Spring ApplicationEvent</b> —— 进程内异步通知，由 {@link WorkflowEventConsumer} 接收并分发给本地 {@link com.dusk.workflow.service.IWorkflowListener}</li>
 *   <li><b>RabbitMQ</b> —— 跨服务通知，消息发送到 {@code workflow.event.exchange}，routing key 格式：{@code workflow.event.{event_type}}</li>
 * </ol>
 * </p>
 * <p>
 * 当 RabbitMQ 未启用时（{@code spring.rabbitmq.is-enabled=false}），仅发布 Spring 事件，MQ 消息自动跳过。
 * MQ 发布失败时只记录错误日志，不影响主流程执行。
 * </p>
 * <p>
 * 事件发布时机由 {@code WorkflowServiceImpl} 在以下节点调用：
 * <ul>
 *   <li>流程启动后 → PROCESS_STARTED + TASK_CREATED</li>
 *   <li>任务完成后 → TASK_COMPLETED + (PROCESS_COMPLETED 或 TASK_CREATED)</li>
 *   <li>流程撤回后 → PROCESS_RECALLED</li>
 *   <li>节点跳转后 → TASK_JUMPED</li>
 *   <li>抄送发送后 → TASK_CC</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 * @see WorkflowEventConsumer
 * @see WorkflowSpringEvent
 * @see com.dusk.module.workflow.core.config.WorkflowMqConfig
 */
@Slf4j
@Component
public class WorkflowEventPublisher {

    @Autowired(required = false)
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 发布工作流事件
     */
    public void publish(WorkflowEventType eventType, String processInstanceId, String processDefinitionKey,
                        String businessKey, String taskId, String taskName, String taskDefinitionKey,
                        String assignee, String comment, Map<String, Object> variables,
                        Map<String, Object> businessData) {
        WorkflowEventDto event = new WorkflowEventDto();
        event.setEventType(eventType);
        event.setProcessInstanceId(processInstanceId);
        event.setProcessDefinitionKey(processDefinitionKey);
        event.setBusinessKey(businessKey);
        event.setTaskId(taskId);
        event.setTaskName(taskName);
        event.setTaskDefinitionKey(taskDefinitionKey);
        event.setAssignee(assignee);
        event.setComment(comment);
        event.setVariables(variables);
        event.setBusinessData(businessData);
        event.setTimestamp(LocalDateTime.now());
        event.setOperator(LoginUserIdContextHolder.getUserId() != null
                ? LoginUserIdContextHolder.getUserId().toString() : null);

        // 发布Spring事件（进程内）
        applicationEventPublisher.publishEvent(new WorkflowSpringEvent(this, event));

        // 发布MQ消息（跨服务）
        publishToMq(event);
    }

    /**
     * 简化版发布
     */
    public void publish(WorkflowEventType eventType, String processInstanceId, String processDefinitionKey,
                        String businessKey) {
        publish(eventType, processInstanceId, processDefinitionKey, businessKey,
                null, null, null, null, null, null, null);
    }

    private void publishToMq(WorkflowEventDto event) {
        if (rabbitTemplate == null) {
            log.debug("RabbitTemplate未配置，跳过MQ消息发布");
            return;
        }
        try {
            String routingKey = "workflow.event." + event.getEventType().name().toLowerCase();
            rabbitTemplate.convertAndSend(WorkflowMqConfig.WORKFLOW_EVENT_EXCHANGE, routingKey,
                    objectMapper.writeValueAsString(event));
            log.debug("工作流事件已发布到MQ: type={}, processInstanceId={}", event.getEventType(), event.getProcessInstanceId());
        } catch (Exception e) {
            log.error("发布工作流MQ事件失败", e);
        }
    }
}

