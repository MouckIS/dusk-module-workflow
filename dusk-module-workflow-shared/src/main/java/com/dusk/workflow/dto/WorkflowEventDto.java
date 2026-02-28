package com.dusk.workflow.dto;

import com.dusk.workflow.enums.WorkflowEventType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流事件DTO，用于MQ消息传递和Spring事件通知
 * <p>
 * 携带了事件发生时的完整上下文信息，包括流程实例、任务、操作人、变量等。
 * 该对象会被序列化为JSON通过RabbitMQ传输，同时也作为Spring内部事件的载体。
 * </p>
 * <p>
 * 使用示例（在 IWorkflowListener 中接收）：
 * <pre>
 * public void onWorkflowEvent(WorkflowEventDto event) {
 *     if (event.getEventType() == WorkflowEventType.TASK_COMPLETED) {
 *         String businessKey = event.getBusinessKey();
 *         // 处理业务逻辑...
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author kefuming
 * @see com.dusk.workflow.enums.WorkflowEventType
 * @see com.dusk.workflow.service.IWorkflowListener
 */
@Getter
@Setter
public class WorkflowEventDto implements Serializable {
    /**
     * 事件类型
     */
    private WorkflowEventType eventType;
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    /**
     * 流程定义Key
     */
    private String processDefinitionKey;
    /**
     * 业务主键
     */
    private String businessKey;
    /**
     * 任务ID
     */
    private String taskId;
    /**
     * 任务名称
     */
    private String taskName;
    /**
     * 任务定义Key
     */
    private String taskDefinitionKey;
    /**
     * 审批人
     */
    private String assignee;
    /**
     * 操作人ID
     */
    private String operator;
    /**
     * 审批意见
     */
    private String comment;
    /**
     * 流程变量
     */
    private Map<String, Object> variables;
    /**
     * 业务数据
     */
    private Map<String, Object> businessData;
    /**
     * 事件时间
     */
    private LocalDateTime timestamp;
}

