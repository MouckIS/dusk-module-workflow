package com.dusk.workflow.service;

import com.dusk.workflow.dto.CompleteTaskInputDto;
import com.dusk.workflow.dto.WorkflowTaskDto;

import java.util.List;

/**
 * 工作流审批处理器接口
 * <p>
 * 业务模块实现此接口可在审批前后执行自定义逻辑。
 * 通过 {@link #getProcessKey()} 关联具体的流程定义。
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
 */
public interface IWorkflowApprovalProcessor {

    /**
     * 获取关联的流程定义Key
     *
     * @return 流程定义Key
     */
    String getProcessKey();

    /**
     * 审批前置处理器
     *
     * @param input 审批输入参数，可以在此修改变量等
     */
    default void preApproval(CompleteTaskInputDto input) {
    }

    /**
     * 审批后置处理器
     *
     * @param result 审批后产生的新任务列表
     * @param input  审批输入参数
     */
    default void postApproval(List<WorkflowTaskDto> result, CompleteTaskInputDto input) {
    }
}

