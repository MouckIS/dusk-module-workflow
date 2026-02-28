package com.dusk.workflow.service;

import com.dusk.workflow.dto.StartProcessOutDto;
import com.dusk.workflow.dto.WorkflowProcessDto;

/**
 * 工作流提交处理器接口
 * <p>
 * 业务模块实现此接口可在流程提交前后执行自定义逻辑。
 * 通过 {@link #getProcessKey()} 关联具体的流程定义。
 * </p>
 *
 * @author kefuming
 * @date 2026-02-28
 */
public interface IWorkflowSubmitProcessor {

    /**
     * 获取关联的流程定义Key
     *
     * @return 流程定义Key
     */
    String getProcessKey();

    /**
     * 提交前置处理器
     *
     * @param input 提交输入参数，可以在此修改变量等
     */
    default void preSubmit(WorkflowProcessDto input) {
    }

    /**
     * 提交后置处理器
     *
     * @param output 提交结果
     * @param input  提交输入参数
     */
    default void postSubmit(StartProcessOutDto output, WorkflowProcessDto input) {
    }
}

