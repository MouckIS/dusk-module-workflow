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
}
