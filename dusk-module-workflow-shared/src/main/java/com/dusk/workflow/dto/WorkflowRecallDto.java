package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 撤回上下文DTO，用于回调业务处理
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Getter
@Setter
public class WorkflowRecallDto implements Serializable {
    /**
     * 流程实例ID
     */
    private String processInstanceId;
    /**
     * 业务主键
     */
    private String businessKey;
    /**
     * 流程定义Key
     */
    private String processDefinitionKey;
    /**
     * 撤回前的任务定义Key
     */
    private String fromTaskDefinitionKey;
    /**
     * 撤回目标的任务定义Key
     */
    private String toTaskDefinitionKey;
    /**
     * 操作人ID
     */
    private String operatorId;
    /**
     * 流程变量
     */
    private Map<String, Object> variables;
}

