package com.dusk.workflow.service;

import com.dusk.workflow.dto.CompleteTaskInputDto;
import com.dusk.workflow.dto.WorkflowTaskDto;

import java.util.List;

/**
 * 工作流审批处理器接口（前置/后置处理器模式）
 * <p>
 * 业务模块实现此接口可在审批前后执行自定义逻辑，无需修改工作流核心代码。
 * 处理器通过 Spring Bean 自动注册到 {@code WorkflowProcessorRegistry}，
 * 在调用 {@code genericApproval()} 时按 {@link #getProcessKey()} 匹配并自动执行。
 * </p>
 * <p>
 * 执行顺序：preApproval → 完成任务 → postApproval → 抄送
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Component
 * public class ContractApprovalProcessor implements IWorkflowApprovalProcessor {
 *     &#64;Override
 *     public String getProcessKey() { return "contract_approval"; }
 *
 *     &#64;Override
 *     public void preApproval(CompleteTaskInputDto input) {
 *         // 审批前记录日志
 *     }
 *
 *     &#64;Override
 *     public void postApproval(List&lt;WorkflowTaskDto&gt; result, CompleteTaskInputDto input) {
 *         // 审批后：如果 result 为空表示流程已结束
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author kefuming
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

