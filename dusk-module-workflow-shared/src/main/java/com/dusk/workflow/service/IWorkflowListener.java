package com.dusk.workflow.service;

import com.dusk.workflow.dto.WorkflowEventDto;

/**
 * 工作流事件监听器接口
 * <p>
 * 业务模块实现此接口即可监听工作流事件（流程启动、任务创建/完成、流程结束、撤回、跳转、抄送等）。
 * 监听器通过 Spring Bean 自动注册，无需额外配置。
 * </p>
 * <p>
 * 事件分发策略：
 * <ul>
 *   <li>{@link #getProcessKey()} 返回 null —— 监听所有流程的事件</li>
 *   <li>{@link #getProcessKey()} 返回具体流程Key —— 仅监听该流程的事件</li>
 * </ul>
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Component
 * public class OrderWorkflowListener implements IWorkflowListener {
 *     &#64;Override
 *     public String getProcessKey() {
 *         return "order_approval"; // 只监听订单审批流程
 *     }
 *
 *     &#64;Override
 *     public void onWorkflowEvent(WorkflowEventDto event) {
 *         switch (event.getEventType()) {
 *             case TASK_COMPLETED -> handleTaskCompleted(event);
 *             case PROCESS_COMPLETED -> handleProcessCompleted(event);
 *         }
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author kefuming
 * @see WorkflowEventDto
 * @see com.dusk.workflow.enums.WorkflowEventType
 */
public interface IWorkflowListener {

    /**
     * 获取监听的流程定义Key，返回null则监听所有流程事件
     *
     * @return 流程定义Key
     */
    default String getProcessKey() {
        return null;
    }

    /**
     * 工作流事件回调
     *
     * @param event 事件信息
     */
    void onWorkflowEvent(WorkflowEventDto event);
}

