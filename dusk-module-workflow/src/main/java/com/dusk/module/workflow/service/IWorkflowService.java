package com.dusk.module.workflow.service;

import com.dusk.module.workflow.dto.RelatedNodeInfo;
import com.dusk.workflow.dto.*;
import com.dusk.workflow.service.IWorkFlowRpcService;

import java.util.List;
import java.util.Map;

/**
 * @author kefuming
 * @date 2020-11-16 13:51
 */
public interface IWorkflowService extends IWorkFlowRpcService {


    /**
     * 根据流程实例id获取流程图
     *
     * @param processInstanceId
     * @return
     */
    byte[] readResource(String processInstanceId);


    /**
     * 根据 processInstanceId ,获取 task
     *
     * @param processInstanceIds
     * @return
     */
    List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds);

    List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds, boolean auth);


    /**
     * 获取关联的审批任务
     *
     * @param processInstanceIds 流程实例id
     * @param auth               是否验证权限
     * @param initAssignee       为 true则 设置关联的任务的代理人参数
     * @return
     */
    List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds, boolean auth, boolean initAssignee);

    List<WorkflowTaskDto> getTasksByProcessAndInitAssignee(List<String> processInstanceIds, boolean auth);

    /**
     * 根据流程实现id完成当前task
     *
     * @param input
     * @return
     */
    boolean completeTaskByProcessId(CompleteTaskByProcessIdInputDto input);

    /**
     * 完成指定任务
     *
     * @param taskId
     * @param workflowCompleteTaskDto
     * @return
     */
    boolean completeTask(String taskId, WorkflowCompleteTaskDto workflowCompleteTaskDto);


    /**
     * 获取下一个任务节点
     *
     * @param processInstanceId
     * @param variables
     * @return
     */
    WorkflowTaskDto getNextTaskByProcessId(String processInstanceId, Map<String, Object> variables);

    /**
     * 获取下一个任务节点
     *
     * @param taskId
     * @param variables
     * @return
     */
    WorkflowTaskDto getNextTask(String taskId, Map<String, Object> variables);


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
     * 按条件计算关联的节点
     *
     * @param taskId        taskId
     * @param processKey    流程任务key 当taskId为空并且该值不为空的时候获取startEvent返回给前端
     * @param autoCalculate 是否计算条件表达式
     * @param variables     临时计算的值
     * @return
     */
    List<RelatedNodeInfo> getRelatedNode(String taskId, String processKey, boolean autoCalculate, Map<String, Object> variables);

    /**
     * 获取任务历史
     *
     * @param processInstanceId
     * @return
     */
    List<WorkflowTaskHistoryDto> getTaskHistory(String processInstanceId);


    /**
     * 获取任务历史(多个流程id)
     *
     * @param processInstanceIds
     * @return
     */
    List<WorkflowTaskHistoryDto> getTaskHistories(List<String> processInstanceIds);


    /**
     * 根据运行实例,获取当前任务包含待处理人的信息
     *
     * @param processInstanceId
     * @return
     */
    List<WorkflowTaskDetailDto> getCurrTasksWithAssigneeInfos(String processInstanceId);


    /**
     * 根据流程实例id 判断当前是否可往前撤回
     * 必须上一个节点是普通节点（不是并行网关，或者会签）
     * 上一节点是自己审批的
     * 当前节点标记允许撤回
     *
     * @param processInstanceId
     * @return
     */
    boolean checkProcessCanRecallPre(String processInstanceId);


    /**
     * 驳回至上一节点，依赖checkProcessCanRecallPre 校验
     *
     * @param processInstanceId
     */
    void recallPre(String processInstanceId);

}
