package com.dusk.workflow.service;

import com.dusk.workflow.dto.StartProcessOutDto;
import com.dusk.workflow.dto.WorkflowProcessDto;

/**
 * 工作流提交处理器接口（前置/后置处理器模式）
 * <p>
 * 业务模块实现此接口可在流程提交前后执行自定义逻辑，无需修改工作流核心代码。
 * 处理器通过 Spring Bean 自动注册到 {@code WorkflowProcessorRegistry}，
 * 在调用 {@code genericSubmit()} 时按 {@link #getProcessKey()} 匹配并自动执行。
 * </p>
 * <p>
 * 执行顺序：preSubmit → 启动流程 → postSubmit → 抄送
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Component
 * public class ContractSubmitProcessor implements IWorkflowSubmitProcessor {
 *     &#64;Override
 *     public String getProcessKey() { return "contract_approval"; }
 *
 *     &#64;Override
 *     public void preSubmit(WorkflowProcessDto input) {
 *         // 提交前校验、补充变量
 *     }
 *
 *     &#64;Override
 *     public void postSubmit(StartProcessOutDto output, WorkflowProcessDto input) {
 *         // 提交后更新业务状态
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author kefuming
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

