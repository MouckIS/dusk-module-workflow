package com.dusk.workflow.service;

import com.dusk.workflow.dto.WorkflowEventDto;

/**
 * 工作流事件监听器接口
 * <p>
 * 业务模块实现此接口即可监听工作流事件。
 * 通过 {@link #getProcessKey()} 指定监听的流程定义Key，返回 null 表示监听所有流程。
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
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

