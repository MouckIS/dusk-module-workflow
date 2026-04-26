package com.dusk.workflow.service.callback;

import com.dusk.workflow.dto.callback.WorkflowCallbackContext;
import com.dusk.workflow.dto.callback.WorkflowCallbackResult;

import java.util.List;
import java.util.Map;

/**
 * 工作流回调 RPC 接口
 * <p>
 * 业务微服务实现此接口，注册为 Dubbo 服务。工作流服务在流程关键节点通过 RPC 回调业务服务，
 * 执行业务校验、状态更新等个性化逻辑。
 * </p>
 * <p>
 * <b>使用方式：</b>
 * <pre>
 * &#64;DubboService(version = "1.0.0", group = "${spring.application.name}")
 * public class ContractWorkflowCallback implements IWorkflowCallbackRpcService {
 *
 *     &#64;Override
 *     public String getProcessKey() {
 *         return "contract_approval";
 *     }
 *
 *     &#64;Override
 *     public WorkflowCallbackResult onBeforeSubmit(WorkflowCallbackContext context) {
 *         // 业务校验
 *         Contract contract = contractService.getById(context.getBusinessKey());
 *         if (contract.getStatus() != ContractStatus.DRAFT) {
 *             return WorkflowCallbackResult.reject("只有草稿状态的合同可以提交审批");
 *         }
 *         // 补充流程变量
 *         return WorkflowCallbackResult.proceed()
 *             .addVariable("contractAmount", contract.getAmount());
 *     }
 *
 *     &#64;Override
 *     public void onAfterSubmit(WorkflowCallbackContext context) {
 *         // 更新业务状态
 *         contractService.updateStatus(context.getBusinessKey(), ContractStatus.APPROVING);
 *     }
 * }
 * </pre>
 * </p>
 * <p>
 * <b>回调时机：</b>
 * <ul>
 *   <li>流程提交：onBeforeSubmit → 启动流程 → onAfterSubmit</li>
 *   <li>任务审批：onBeforeApproval → 完成任务 → onAfterApproval</li>
 *   <li>流程撤回：onBeforeRecall → 撤回操作 → onAfterRecall</li>
 *   <li>节点跳转：跳转操作 → onAfterJump</li>
 * </ul>
 * </p>
 * <p>
 * <b>事务说明：</b>
 * <ul>
 *   <li>前置回调（onBefore*）：在工作流事务内同步执行，抛异常会导致流程回滚</li>
 *   <li>后置回调（onAfter*）：在工作流事务内同步执行，但异常不会导致流程回滚（仅记录日志）</li>
 *   <li>业务服务应在自己的事务中处理业务逻辑</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 * @see WorkflowCallbackContext
 * @see WorkflowCallbackResult
 */
public interface IWorkflowCallbackRpcService {

    /**
     * 获取此回调服务关联的流程定义Key
     * <p>
     * 工作流服务根据此值路由到对应的业务回调服务。
     * 每个流程Key只能有一个回调服务实现。
     * </p>
     *
     * @return 流程定义Key，如 "contract_approval"、"leave_request"
     */
    String getProcessKey();

    // ==================== 流程提交回调 ====================

    /**
     * 流程提交前回调
     * <p>
     * 在流程启动之前调用，可以：
     * <ul>
     *   <li>校验业务规则，不满足条件时返回 reject 中断流程</li>
     *   <li>补充/修改流程变量</li>
     *   <li>记录操作日志</li>
     * </ul>
     * </p>
     *
     * @param context 回调上下文，包含 processKey、businessKey、variables 等
     * @return 回调结果，proceed=false 时中断流程并抛出异常
     */
    default WorkflowCallbackResult onBeforeSubmit(WorkflowCallbackContext context) {
        return WorkflowCallbackResult.proceed();
    }

    /**
     * 流程提交后回调
     * <p>
     * 在流程启动成功后调用，可以：
     * <ul>
     *   <li>更新业务状态（如设置为"审批中"）</li>
     *   <li>保存流程实例ID到业务表</li>
     *   <li>发送通知</li>
     * </ul>
     * </p>
     * <p>
     * 注意：此方法抛出异常不会导致流程回滚，仅记录错误日志。
     * </p>
     *
     * @param context 回调上下文，包含 processInstanceId、nextTasks 等
     */
    default void onAfterSubmit(WorkflowCallbackContext context) {
    }

    // ==================== 任务审批回调 ====================

    /**
     * 任务审批前回调
     * <p>
     * 在任务完成之前调用，可以：
     * <ul>
     *   <li>校验审批权限或业务规则</li>
     *   <li>根据业务条件修改流程变量（影响后续流转）</li>
     *   <li>记录审批日志</li>
     * </ul>
     * </p>
     *
     * @param context 回调上下文，包含 taskId、taskDefinitionKey、comment 等
     * @return 回调结果，proceed=false 时中断审批并抛出异常
     */
    default WorkflowCallbackResult onBeforeApproval(WorkflowCallbackContext context) {
        return WorkflowCallbackResult.proceed();
    }

    /**
     * 任务审批后回调
     * <p>
     * 在任务完成后调用，可以：
     * <ul>
     *   <li>根据审批结果更新业务状态</li>
     *   <li>流程结束时执行最终业务处理</li>
     *   <li>发送审批结果通知</li>
     * </ul>
     * </p>
     * <p>
     * 通过 {@code context.isProcessEnded()} 判断流程是否已结束。
     * </p>
     *
     * @param context 回调上下文，包含 processEnded、nextTasks 等
     */
    default void onAfterApproval(WorkflowCallbackContext context) {
    }

    // ==================== 流程撤回回调 ====================

    /**
     * 流程撤回前回调
     * <p>
     * 在撤回操作之前调用，可以：
     * <ul>
     *   <li>校验是否允许撤回</li>
     *   <li>记录撤回原因</li>
     * </ul>
     * </p>
     *
     * @param context 回调上下文
     * @return 回调结果，proceed=false 时阻止撤回
     */
    default WorkflowCallbackResult onBeforeRecall(WorkflowCallbackContext context) {
        return WorkflowCallbackResult.proceed();
    }

    /**
     * 流程撤回后回调
     * <p>
     * 在撤回成功后调用，用于业务状态回滚，如：
     * <ul>
     *   <li>将业务状态从"审批中"改回"草稿"</li>
     *   <li>撤销已执行的业务操作</li>
     * </ul>
     * </p>
     *
     * @param context 回调上下文，包含 targetTaskDefinitionKey（撤回到的节点）
     */
    default void onAfterRecall(WorkflowCallbackContext context) {
    }

    // ==================== 节点跳转回调 ====================

    /**
     * 节点跳转后回调
     * <p>
     * 在跳转成功后调用，可以：
     * <ul>
     *   <li>记录跳转日志</li>
     *   <li>根据目标节点更新业务状态</li>
     * </ul>
     * </p>
     *
     * @param context 回调上下文，包含 targetTaskDefinitionKey（跳转到的节点）
     */
    default void onAfterJump(WorkflowCallbackContext context) {
    }

    // ==================== 动态计算 ====================

    /**
     * 动态计算审批人
     * <p>
     * 在任务创建时调用（如果节点配置了动态审批人），可以根据业务规则返回审批人列表。
     * </p>
     *
     * @param context 回调上下文，包含 taskDefinitionKey、variables 等
     * @return 审批人ID列表，返回 null 或空列表时使用流程定义中的配置
     */
    default List<String> resolveApprovers(WorkflowCallbackContext context) {
        return null;
    }

    /**
     * 动态计算流程变量
     * <p>
     * 在流程启动或任务完成前调用，可以根据业务数据动态计算流程变量。
     * </p>
     *
     * @param context 回调上下文
     * @return 需要添加/覆盖的变量，返回 null 或空 Map 不修改
     */
    default Map<String, Object> resolveVariables(WorkflowCallbackContext context) {
        return null;
    }

    // ==================== 配置方法 ====================

    /**
     * 获取回调超时时间（毫秒）
     * <p>
     * 超时后工作流服务将继续执行（视为成功），不会阻塞流程。
     * </p>
     *
     * @return 超时时间，默认 5000ms
     */
    default long getTimeoutMs() {
        return 5000L;
    }

    /**
     * 前置回调失败时是否中断流程
     * <p>
     * true: 前置回调抛异常时流程中断（默认）
     * false: 前置回调抛异常时仅记录日志，流程继续
     * </p>
     *
     * @return 是否中断
     */
    default boolean isFailFast() {
        return true;
    }
}
