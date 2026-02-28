package com.dusk.workflow.service;

import com.dusk.workflow.dto.WorkflowRecallDto;

/**
 * 工作流撤回业务处理器接口
 * <p>
 * 业务模块实现此接口可在流程撤回时执行自定义业务逻辑（如状态回滚等）。
 * 通过 {@link #getProcessKey()} 关联具体的流程定义。
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
 */
public interface IWorkflowRecallHandler {

    /**
     * 获取关联的流程定义Key
     *
     * @return 流程定义Key
     */
    String getProcessKey();

    /**
     * 撤回业务处理回调
     *
     * @param context 撤回上下文信息
     */
    void onRecall(WorkflowRecallDto context);
}

