package com.dusk.workflow.dto.callback;

/**
 * 工作流阶段枚举
 *
 * @author kefuming
 */
public enum WorkflowPhase {
    
    /**
     * 流程提交阶段
     */
    SUBMIT,
    
    /**
     * 任务审批阶段
     */
    APPROVAL,
    
    /**
     * 流程撤回阶段
     */
    RECALL,
    
    /**
     * 节点跳转阶段
     */
    JUMP,
    
    /**
     * 抄送阶段
     */
    CARBON_COPY
}
