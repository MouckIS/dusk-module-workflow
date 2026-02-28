package com.dusk.workflow.enums;

/**
 * 工作流事件类型
 *
 * @author kefuming
 * @date 2026-02-28
 */
public enum WorkflowEventType {
    /**
     * 流程启动
     */
    PROCESS_STARTED,
    /**
     * 任务创建
     */
    TASK_CREATED,
    /**
     * 任务完成
     */
    TASK_COMPLETED,
    /**
     * 流程结束
     */
    PROCESS_COMPLETED,
    /**
     * 流程撤回
     */
    PROCESS_RECALLED,
    /**
     * 节点跳转
     */
    TASK_JUMPED,
    /**
     * 抄送
     */
    TASK_CC
}

