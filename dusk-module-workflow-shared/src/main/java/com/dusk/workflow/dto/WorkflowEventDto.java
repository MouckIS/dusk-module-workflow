package com.dusk.workflow.dto;

import com.dusk.workflow.enums.WorkflowEventType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 工作流事件DTO，用于MQ消息传递
 *
 * @author kefuming
 * @date 2026-02-28
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

