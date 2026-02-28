package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 撤回上下文DTO
 * <p>
 * 作为 {@link com.dusk.workflow.service.IWorkflowRecallHandler#onRecall(WorkflowRecallDto)} 的回调参数，
 * 携带撤回操作的完整上下文信息，供业务模块执行撤回时的状态回滚等操作。
 * </p>
 * <p>
 * 包含信息：
 * <ul>
 *   <li>流程实例ID、业务主键、流程定义Key</li>
 *   <li>撤回前的节点Key（fromTaskDefinitionKey）和撤回目标节点Key（toTaskDefinitionKey）</li>
 *   <li>操作人ID、当前流程变量</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 * @see com.dusk.workflow.service.IWorkflowRecallHandler
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

