package com.dusk.workflow.service;

import com.dusk.workflow.dto.WorkflowRecallDto;

/**
 * 工作流撤回业务处理器接口
 * <p>
 * 业务模块实现此接口可在流程撤回时执行自定义业务逻辑（如业务状态回滚、数据清理等）。
 * 处理器通过 Spring Bean 自动注册，在 {@code recallPre()} 撤回完成后按 {@link #getProcessKey()} 匹配并调用。
 * </p>
 * <p>
 * 撤回执行顺序：校验条件 → 执行跳转 → 删除历史记录 → 同步待办 → 调用 onRecall() → 发布事件
 * </p>
 * <p>
 * 使用示例：
 * <pre>
 * &#64;Component
 * public class ContractRecallHandler implements IWorkflowRecallHandler {
 *     &#64;Override
 *     public String getProcessKey() { return "contract_approval"; }
 *
 *     &#64;Override
 *     public void onRecall(WorkflowRecallDto context) {
 *         // 撤回时将合同状态回滚为"草稿"
 *         contractService.updateStatus(context.getBusinessKey(), "DRAFT");
 *     }
 * }
 * </pre>
 * </p>
 *
 * @author kefuming
 * @see WorkflowRecallDto
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

