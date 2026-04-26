package com.dusk.module.workflow.event;

import com.dusk.common.core.auth.authentication.LoginUserIdContextHolder;
import com.dusk.module.workflow.transaction.TransactionalMessageService;
import com.dusk.workflow.dto.WorkflowEventDto;
import com.dusk.workflow.enums.WorkflowEventType;
import com.dusk.workflow.trace.WorkflowTraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.IllegalTransactionStateException;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流事件发布器
 * <p>
 * 采用 <b>双通道发布</b> 策略：
 * <ol>
 *   <li><b>Spring ApplicationEvent</b> —— 进程内异步通知，由 {@link WorkflowEventConsumer} 接收并分发给本地 {@link com.dusk.workflow.service.IWorkflowListener}</li>
 *   <li><b>事务消息表 + RabbitMQ</b> —— 跨服务通知，消息先保存到本地事务表（与业务操作同事务），事务提交后由定时任务发送到 MQ</li>
 * </ol>
 * </p>
 * <p>
 * <b>v2.0 改进：</b>使用本地消息表模式替代直接发送 MQ，解决以下问题：
 * <ul>
 *   <li>事务内发送 MQ 失败导致业务回滚</li>
 *   <li>事务提交后 MQ 发送失败导致消息丢失</li>
 *   <li>MQ 发送成功但事务回滚导致消息重复</li>
 * </ul>
 * 通过本地消息表 + 定时补偿任务，保证消息最终一致性。
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
 * @see TransactionalMessageService
 */
@Slf4j
@Component
public class WorkflowEventPublisher {

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private TransactionalMessageService transactionalMessageService;

    /**
     * 发布工作流事件
     * <p>
     * 此方法应在事务上下文中调用，消息将被保存到本地事务表，
     * 与业务操作在同一事务中，保证原子性。
     * </p>
     *
     * @param eventType           事件类型
     * @param processInstanceId   流程实例ID
     * @param processDefinitionKey 流程定义Key
     * @param businessKey         业务Key
     * @param taskId              任务ID
     * @param taskName            任务名称
     * @param taskDefinitionKey   任务定义Key
     * @param assignee            任务处理人
     * @param comment             审批意见
     * @param variables           流程变量
     * @param businessData        业务扩展数据
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
        // 设置 TraceId，支持分布式追踪
        event.setTraceId(WorkflowTraceContext.getTraceIdOrNull());

        // 1. 发布Spring事件（进程内异步通知）
        applicationEventPublisher.publishEvent(new WorkflowSpringEvent(this, event));

        // 2. 保存到事务消息表（与业务操作同事务）
        // 事务提交后，由 MessageCompensationTask 定时发送到 MQ
        saveToTransactionalMessage(event);
    }

    /**
     * 简化版发布
     */
    public void publish(WorkflowEventType eventType, String processInstanceId, String processDefinitionKey,
                        String businessKey) {
        publish(eventType, processInstanceId, processDefinitionKey, businessKey,
                null, null, null, null, null, null, null);
    }

    /**
     * 保存消息到事务表
     * <p>
     * 在当前事务上下文中保存消息，如果没有事务上下文，
     * 则记录警告日志并尝试直接保存（不推荐）。
     * </p>
     */
    private void saveToTransactionalMessage(WorkflowEventDto event) {
        try {
            transactionalMessageService.saveMessage(event);
            log.debug("工作流事件已保存到事务表: type={}, processInstanceId={}",
                    event.getEventType(), event.getProcessInstanceId());
        } catch (IllegalTransactionStateException e) {
            // 没有事务上下文，通常是编程错误
            log.warn("保存事务消息时没有事务上下文，消息可能丢失: type={}, processInstanceId={}",
                    event.getEventType(), event.getProcessInstanceId(), e);
        } catch (Exception e) {
            // 其他异常，记录错误但不抛出（不影响主流程）
            log.error("保存事务消息失败: type={}, processInstanceId={}",
                    event.getEventType(), event.getProcessInstanceId(), e);
        }
    }
}

