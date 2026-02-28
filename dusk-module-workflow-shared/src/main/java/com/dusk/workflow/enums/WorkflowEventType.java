package com.dusk.workflow.enums;

/**
 * 工作流事件类型枚举
 * <p>
 * 定义了工作流引擎在关键节点自动发布的事件类型。
 * 事件通过 {@code WorkflowEventPublisher} 发布到 Spring ApplicationEvent 和 RabbitMQ 双通道，
 * 业务模块通过实现 {@link com.dusk.workflow.service.IWorkflowListener} 接口来监听这些事件。
 * </p>
 *
 * @author kefuming
 * @see com.dusk.workflow.dto.WorkflowEventDto
 * @see com.dusk.workflow.service.IWorkflowListener
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

