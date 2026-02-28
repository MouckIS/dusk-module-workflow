package com.dusk.workflow.service;


import com.dusk.workflow.dto.*;

import java.util.List;
import java.util.Map;


/**
 * 工作流 rpc接口
 *
 * @author kefuming
 * @date 2020-11-16 14:25
 */
public interface IWorkFlowRpcService {

    /**
     * 提交流程并且审批掉第一个节点
     *
     * @param input 流程以及接下去的任务信息
     * @return
     */
    StartProcessOutDto startProcessAndCompleteFirst(StartProcessInputDto input);


    /**
     * 提交流程
     *
     * @param input 流程以及接下去的任务信息
     * @return
     */
    StartProcessOutDto startProcess(WorkflowProcessDto input);


    /**
     * 根据流程实现id完成当前task
     *
     * @param input
     * @return
     */
    boolean completeTaskByProcessId(CompleteTaskByProcessIdInputDto input);

    /**
     * 根据流程id审批任务节点
     *
     * @param input
     * @return
     */
    List<WorkflowTaskDto> completeTask(CompleteTaskInputDto input);

    /**
     * 删除一个流程实例
     *
     * @param processInstanceId 不可为空
     * @param deleteReason      可为空
     * @return
     */
    boolean delProcess(String processInstanceId, String deleteReason);

    /**
     * 检查流程是否结束
     *
     * @param processInstanceId
     * @return
     */
    boolean checkProcessEnd(String processInstanceId);

    /**
     * 根据 taskId ,获取 Task
     *
     * @param taskId
     * @return
     */
    WorkflowTaskDto getTask(String taskId);


    /**
     * 根据流程实例带出相关的描述以及能否审批
     *
     * @param processIds
     * @return
     */
    List<ProcessDesOutPutDto> getProcessDescription(List<String> processIds);


    /**
     * 获取第一个节点的流程定义（formkey必须定义step）
     *
     * @param processKey
     * @return
     */
    String getProcessDefinitionFirstFormKey(String processKey);

    /**
     * 计算关联节点
     *
     * @param taskId
     * @param autoCalculate
     * @param variables
     * @return
     */
    List<WorkflowTaskDto> getRelateTask(String taskId, boolean autoCalculate, Map<String, Object> variables);

    /**
     * 根据实例 IDs ,获取 task 列表
     *
     * @param processInstanceIds 实例ids
     * @return
     */
    List<WorkflowTaskDto> getTaskList(List<String> processInstanceIds);

    /**
     * 变更任务候选人
     *
     * @param input
     * @return
     */
    void updateTaskAssignee(UpdateTaskAssigneeInput input);

    /**
     * 根据运行实例,获取当前任务包含待处理人的信息
     *
     * @param processInstanceIds
     * @param checkAuth
     * @return
     */
    List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds, boolean checkAuth);

    /**
     * 根据流程实现id完成当前task
     *
     * @param input
     * @param checkAuth
     * @return
     */
    List<WorkflowTaskDto> completeTask(CompleteTaskInputDto input, boolean checkAuth);

    /**
     * 更新流程变量 -- 如果有修改到任务代理人中的变量，则同时为该任务重新指派处理人，并重新生产待办
     *
     * @param input
     */
    void updateFlowVariables(UpdateFlowVariablesInput input);

    /**
     * 通用流程提交（带前置/后置处理器）
     * <p>
     * 执行顺序：IWorkflowSubmitProcessor.preSubmit → startProcess → postSubmit → 抄送
     * 处理器按 processDefinitionKey 自动匹配，未匹配到则跳过处理器直接提交。
     * </p>
     *
     * @param input 提交参数，包含流程定义Key、业务Key、变量、抄送人等
     * @return 提交结果，包含流程实例ID和当前待办任务列表
     * @see IWorkflowSubmitProcessor
     */
    StartProcessOutDto genericSubmit(GenericSubmitInput input);

    /**
     * 通用流程审批（带前置/后置处理器）
     * <p>
     * 执行顺序：IWorkflowApprovalProcessor.preApproval → completeTask → postApproval → 抄送
     * 处理器按 processDefinitionKey 自动匹配，未匹配到则跳过处理器直接审批。
     * </p>
     *
     * @param input 审批参数，继承自CompleteTaskInputDto，新增ccUserIds
     * @return 审批后产生的新任务列表（空列表表示流程已结束）
     * @see IWorkflowApprovalProcessor
     */
    List<WorkflowTaskDto> genericApproval(GenericApprovalInput input);

    /**
     * 撤回流程至上一节点（带业务回调处理）
     * <p>
     * 撤回完成后会：同步待办 → 调用IWorkflowRecallHandler.onRecall → 发布PROCESS_RECALLED事件
     * </p>
     *
     * @param input 撤回参数
     * @see IWorkflowRecallHandler
     */
    void recallProcess(RecallProcessInput input);

    /**
     * 节点跳转 —— 将流程从当前节点直接跳转到目标任意节点
     * <p>
     * 管理级功能，跳转完成后会同步待办并发布TASK_JUMPED事件。
     * 支持多任务并行场景（并行网关），会将所有当前任务跳转到目标节点。
     * </p>
     *
     * @param input 跳转参数，包含processInstanceId和targetTaskDefinitionKey
     */
    void jumpToNode(JumpToNodeInput input);

    /**
     * 发送抄送通知 —— 通过站内信 {@link INotificationRpcService} 向指定用户发送通知
     * <p>
     * 发送成功后会发布TASK_CC事件。通知服务不可用时仅记录错误日志，不阻断主流程。
     * </p>
     *
     * @param input 抄送参数，包含ccUserIds、title、content等
     * @see INotificationRpcService
     */
    void sendCarbonCopy(CarbonCopyInput input);
}
