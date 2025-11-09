package com.dusk.module.workflow.service.impl;

import com.dusk.module.workflow.service.IWorkflowService;
import com.dusk.workflow.dto.*;
import com.dusk.workflow.service.IWorkFlowRpcService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.Map;

/**
 * @author : kefuming
 * @date : 2025/11/9 13:49
 */
@DubboService
public class WorkflowRpcServiceImpl implements IWorkFlowRpcService {

    @DubboReference
    private IWorkflowService workflowService;

    @Override
    public StartProcessOutDto startProcessAndCompleteFirst(StartProcessInputDto input) {
        return workflowService.startProcessAndCompleteFirst(input);
    }

    @Override
    public StartProcessOutDto startProcess(WorkflowProcessDto input) {
        return workflowService.startProcess(input);
    }

    @Override
    public boolean completeTaskByProcessId(CompleteTaskByProcessIdInputDto input) {
        return workflowService.completeTaskByProcessId(input);
    }

    @Override
    public List<WorkflowTaskDto> completeTask(CompleteTaskInputDto input) {
        return workflowService.completeTask(input);
    }

    @Override
    public boolean delProcess(String processInstanceId, String deleteReason) {
        return workflowService.delProcess(processInstanceId, deleteReason);
    }

    @Override
    public boolean checkProcessEnd(String processInstanceId) {
        return workflowService.checkProcessEnd(processInstanceId);
    }

    @Override
    public WorkflowTaskDto getTask(String taskId) {
        return workflowService.getTask(taskId);
    }

    @Override
    public List<ProcessDesOutPutDto> getProcessDescription(List<String> processIds) {
        return workflowService.getProcessDescription(processIds);
    }

    @Override
    public String getProcessDefinitionFirstFormKey(String processKey) {
        return workflowService.getProcessDefinitionFirstFormKey(processKey);
    }

    @Override
    public List<WorkflowTaskDto> getRelateTask(String taskId, boolean autoCalculate, Map<String, Object> variables) {
        return workflowService.getRelateTask(taskId, autoCalculate, variables);
    }

    @Override
    public List<WorkflowTaskDto> getTaskList(List<String> processInstanceIds) {
        return workflowService.getTaskList(processInstanceIds);
    }

    @Override
    public void updateTaskAssignee(UpdateTaskAssigneeInput input) {
        workflowService.updateTaskAssignee(input);
    }

    @Override
    public List<WorkflowTaskDto> getTasksByProcess(List<String> processInstanceIds, boolean checkAuth) {
        return workflowService.getTasksByProcess(processInstanceIds, checkAuth);
    }

    @Override
    public List<WorkflowTaskDto> completeTask(CompleteTaskInputDto input, boolean checkAuth) {
        return workflowService.completeTask(input, checkAuth);
    }

    @Override
    public void updateFlowVariables(UpdateFlowVariablesInput input) {
        workflowService.updateFlowVariables(input);
    }
}
