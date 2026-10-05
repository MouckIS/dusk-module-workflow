package com.dusk.module.workflow.service.impl;

import com.dusk.module.workflow.service.IWorkflowService;
import com.dusk.workflow.dto.CompleteTaskByProcessIdInputDto;
import com.dusk.workflow.dto.CompleteTaskInputDto;
import com.dusk.workflow.dto.ProcessDesOutPutDto;
import com.dusk.workflow.dto.StartProcessInputDto;
import com.dusk.workflow.dto.StartProcessOutDto;
import com.dusk.workflow.dto.UpdateFlowVariablesInput;
import com.dusk.workflow.dto.UpdateTaskAssigneeInput;
import com.dusk.workflow.dto.WorkflowProcessDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowRpcServiceImpl} 单元测试：校验所有 RPC 方法正确委托到本地服务。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowRpcServiceImplTest {

    @Mock
    private IWorkflowService workflowService;

    @InjectMocks
    private WorkflowRpcServiceImpl rpcService;

    @Test
    @DisplayName("startProcessAndCompleteFirst 委托")
    void startProcessAndCompleteFirst() {
        StartProcessInputDto input = new StartProcessInputDto();
        StartProcessOutDto expected = new StartProcessOutDto();
        when(workflowService.startProcessAndCompleteFirst(input)).thenReturn(expected);

        assertThat(rpcService.startProcessAndCompleteFirst(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("startProcess 委托")
    void startProcess() {
        WorkflowProcessDto input = new WorkflowProcessDto();
        StartProcessOutDto expected = new StartProcessOutDto();
        when(workflowService.startProcess(input)).thenReturn(expected);

        assertThat(rpcService.startProcess(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("completeTaskByProcessId 委托")
    void completeTaskByProcessId() {
        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        when(workflowService.completeTaskByProcessId(input)).thenReturn(true);

        assertThat(rpcService.completeTaskByProcessId(input)).isTrue();
    }

    @Test
    @DisplayName("completeTask(CompleteTaskInputDto) 委托")
    void completeTask() {
        CompleteTaskInputDto input = new CompleteTaskInputDto();
        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        when(workflowService.completeTask(input)).thenReturn(expected);

        assertThat(rpcService.completeTask(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("completeTask(input, checkAuth) 委托")
    void completeTaskWithAuth() {
        CompleteTaskInputDto input = new CompleteTaskInputDto();
        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        when(workflowService.completeTask(input, false)).thenReturn(expected);

        assertThat(rpcService.completeTask(input, false)).isSameAs(expected);
    }

    @Test
    @DisplayName("delProcess 委托")
    void delProcess() {
        when(workflowService.delProcess("p1", "reason")).thenReturn(true);

        assertThat(rpcService.delProcess("p1", "reason")).isTrue();
    }

    @Test
    @DisplayName("checkProcessEnd 委托")
    void checkProcessEnd() {
        when(workflowService.checkProcessEnd("p1")).thenReturn(true);

        assertThat(rpcService.checkProcessEnd("p1")).isTrue();
    }

    @Test
    @DisplayName("getTask 委托")
    void getTask() {
        WorkflowTaskDto expected = new WorkflowTaskDto();
        when(workflowService.getTask("t1")).thenReturn(expected);

        assertThat(rpcService.getTask("t1")).isSameAs(expected);
    }

    @Test
    @DisplayName("getProcessDescription 委托")
    void getProcessDescription() {
        List<ProcessDesOutPutDto> expected = List.of(new ProcessDesOutPutDto());
        List<String> ids = List.of("p1");
        when(workflowService.getProcessDescription(ids)).thenReturn(expected);

        assertThat(rpcService.getProcessDescription(ids)).isSameAs(expected);
    }

    @Test
    @DisplayName("getProcessDefinitionFirstFormKey 委托")
    void getProcessDefinitionFirstFormKey() {
        when(workflowService.getProcessDefinitionFirstFormKey("key")).thenReturn("formKey");

        assertThat(rpcService.getProcessDefinitionFirstFormKey("key")).isEqualTo("formKey");
    }

    @Test
    @DisplayName("getRelateTask 委托")
    void getRelateTask() {
        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        Map<String, Object> variables = Map.of("k", "v");
        when(workflowService.getRelateTask("t1", true, variables)).thenReturn(expected);

        assertThat(rpcService.getRelateTask("t1", true, variables)).isSameAs(expected);
    }

    @Test
    @DisplayName("getTaskList 委托")
    void getTaskList() {
        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        List<String> ids = List.of("p1");
        when(workflowService.getTaskList(ids)).thenReturn(expected);

        assertThat(rpcService.getTaskList(ids)).isSameAs(expected);
    }

    @Test
    @DisplayName("updateTaskAssignee 委托")
    void updateTaskAssignee() {
        UpdateTaskAssigneeInput input = new UpdateTaskAssigneeInput();

        rpcService.updateTaskAssignee(input);

        verify(workflowService).updateTaskAssignee(input);
    }

    @Test
    @DisplayName("getTasksByProcess 委托")
    void getTasksByProcess() {
        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        List<String> ids = List.of("p1");
        when(workflowService.getTasksByProcess(ids, true)).thenReturn(expected);

        assertThat(rpcService.getTasksByProcess(ids, true)).isSameAs(expected);
    }

    @Test
    @DisplayName("updateFlowVariables 委托")
    void updateFlowVariables() {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();

        rpcService.updateFlowVariables(input);

        verify(workflowService).updateFlowVariables(input);
    }
}
