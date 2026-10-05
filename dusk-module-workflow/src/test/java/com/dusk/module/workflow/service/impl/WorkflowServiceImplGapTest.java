package com.dusk.module.workflow.service.impl;

import com.dusk.common.model.authentication.LoginUserIdContextHolder;
import com.dusk.common.model.model.UserContext;
import com.dusk.common.model.tenant.TenantContextHolder;
import com.dusk.common.core.utils.SecurityUtils;
import com.dusk.common.rpc.auth.UserNameUtils;
import com.dusk.common.rpc.auth.dto.UserFullListDto;
import com.dusk.common.rpc.auth.service.ITodoRpcService;
import com.dusk.common.rpc.auth.service.IUserRpcService;
import com.dusk.module.workflow.constant.FlowableConstants;
import com.dusk.module.workflow.dto.RelatedNodeInfo;
import com.dusk.module.workflow.dto.UserNameDto;
import com.dusk.workflow.dto.CompleteTaskByProcessIdInputDto;
import com.dusk.workflow.dto.CompleteTaskInputDto;
import com.dusk.workflow.dto.ProcessDesOutPutDto;
import com.dusk.workflow.dto.UpdateFlowVariablesInput;
import com.dusk.workflow.dto.WorkflowCompleteTaskDto;
import com.dusk.workflow.dto.WorkflowProcessDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import tools.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.FormService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.form.TaskFormData;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.impl.RepositoryServiceImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowServiceImpl} 补充边界分支测试：补齐主测试类未覆盖的条件分支
 * （空/非空变量、匿名提交、非 UserTask/非网关目标、网关条件命中、撤回前置校验等）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceImplGapTest {

    private static final Long USER_ID = 1001L;

    @Mock
    private RuntimeService runtimeService;
    @Mock
    private TaskService taskService;
    @Mock
    private RepositoryServiceImpl repositoryService;
    @Mock
    private ProcessEngineConfiguration processEngineConfiguration;
    @Mock
    private HistoryService historyService;
    @Mock
    private FormService formService;
    @Mock
    private IUserRpcService userRpcService;
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private UserNameUtils userNameUtils;
    @Mock
    private ITodoRpcService todoRpcService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        LoginUserIdContextHolder.setUserId(USER_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
        LoginUserIdContextHolder.clear();
    }

    // ------------------------------------------------------------------ helpers

    private static <T> T self(Class<T> type) {
        return Mockito.mock(type, Answers.RETURNS_SELF);
    }

    private static UserContext currentUser(Long id) {
        UserContext ctx = new UserContext();
        ctx.setId(id);
        return ctx;
    }

    private static UserTask userTask(String id, String name) {
        UserTask userTask = new UserTask();
        userTask.setId(id);
        userTask.setName(name);
        return userTask;
    }

    private static SequenceFlow flow(String id, FlowNode source, FlowElement target, String name, String condition) {
        SequenceFlow sequenceFlow = new SequenceFlow();
        sequenceFlow.setId(id);
        sequenceFlow.setName(name);
        sequenceFlow.setConditionExpression(condition);
        sequenceFlow.setSourceFlowElement(source);
        sequenceFlow.setTargetFlowElement(target);
        if (source != null) {
            source.getOutgoingFlows().add(sequenceFlow);
        }
        return sequenceFlow;
    }

    private static BpmnModel model(FlowElement... elements) {
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("process1");
        for (FlowElement element : elements) {
            process.addFlowElement(element);
        }
        BpmnModel bpmnModel = new BpmnModel();
        bpmnModel.addProcess(process);
        return bpmnModel;
    }

    private static Map<String, Object> todoVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(FlowableConstants.TITLE, "标题");
        variables.put(FlowableConstants.TYPE_NAME, "类型");
        variables.put(FlowableConstants.BUSINESS_TYPE, "业务");
        variables.put(FlowableConstants.FILTER_STATION, false);
        variables.put(FlowableConstants.STARTER, "发起人");
        return variables;
    }

    /** 构造"运行中流程 + 当前任务 + BpmnModel"所需的引擎上下文。 */
    private ExecutionEntity stubRelateContext(String activityId) {
        Task taskInstance = Mockito.mock(Task.class);
        when(taskInstance.getProcessInstanceId()).thenReturn("p1");
        when(taskInstance.getExecutionId()).thenReturn("e1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(taskInstance);
        when(taskQuery.list()).thenReturn(List.of(taskInstance));

        ExecutionEntity execution = Mockito.mock(ExecutionEntity.class);
        when(execution.getProcessDefinitionId()).thenReturn("pd1");
        when(execution.getActivityId()).thenReturn(activityId);

        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(execution);

        ExecutionQuery executionQuery = self(ExecutionQuery.class);
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.singleResult()).thenReturn(execution);

        when(runtimeService.getVariables("e1")).thenReturn(new HashMap<>());
        when(runtimeService.getVariables("p1")).thenReturn(new HashMap<>());

        ProcessDefinitionEntity definitionEntity = Mockito.mock(ProcessDefinitionEntity.class);
        when(definitionEntity.getId()).thenReturn("pd1");
        when(repositoryService.getDeployedProcessDefinition("pd1")).thenReturn(definitionEntity);

        HistoricActivityInstanceQuery activityQuery = self(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);
        when(activityQuery.list()).thenReturn(List.of());

        return execution;
    }

    private void stubSingleTask(Task task) {
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
    }

    // --------------------------------------------------------------- completeTask

    @Test
    @DisplayName("completeTaskByProcessId：任务已有代理人但 needAuth=false，跳过权限校验")
    void completeTaskByProcessIdSkipsAuthWhenTaskHasAssignee() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn("otherUser");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of(task), List.of());

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThat(workflowService.completeTaskByProcessId(input)).isTrue();

        verify(taskService).setAssignee("t1", String.valueOf(USER_ID));
    }

    @Test
    @DisplayName("completeTask：匿名（未登录）时处理人写空串")
    void completeTaskWritesEmptyAssigneeWhenAnonymous() {
        LoginUserIdContextHolder.clear();

        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("");
        stubSingleTask(task);

        assertThat(workflowService.completeTask("t1", new WorkflowCompleteTaskDto())).isTrue();

        verify(taskService).setAssignee("t1", "");
    }

    @Test
    @DisplayName("completeTask：localVariables 为空 Map 时不写局部变量")
    void completeTaskIgnoresEmptyLocalVariables() {
        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("");
        stubSingleTask(task);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();
        dto.setLocalVariables(new HashMap<>());

        workflowService.completeTask("t1", dto);

        verify(taskService, never()).setVariablesLocal(anyString(), anyMap());
    }

    @Test
    @DisplayName("completeTask(input)：入参 variables 非空时沿用原 Map")
    void completeTaskInputKeepsProvidedVariables() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of());

        CompleteTaskInputDto input = new CompleteTaskInputDto();
        input.setProcessInstanceId("p1");
        input.setTaskId("t1");
        Map<String, Object> variables = new HashMap<>();
        variables.put("k", "v");
        input.setVariables(variables);

        assertThat(workflowService.completeTask(input)).isEmpty();

        verify(taskService).setVariables(eq("t1"), eq(variables));
    }

    @Test
    @DisplayName("completeTaskByProcessId：任务查询返回 null 列表时抛异常")
    void completeTaskByProcessIdNullTaskListThrows() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(null);

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThatThrownBy(() -> workflowService.completeTaskByProcessId(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无法查询到关联的任务");
    }

    @Test
    @DisplayName("completeTaskByProcessId：待办同步时装配任务明细")
    void completeTaskByProcessIdSyncsTodoWithTaskDetails() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn("1001");
        when(task.getName()).thenReturn("审批节点");
        when(task.getTaskDefinitionKey()).thenReturn("ut1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of(task));

        TaskFormData taskFormData = Mockito.mock(TaskFormData.class);
        when(taskFormData.getFormKey()).thenReturn("{}");
        when(taskFormData.getFormProperties()).thenReturn(List.of());
        when(formService.getTaskFormData(any())).thenReturn(taskFormData);
        when(taskService.getIdentityLinksForTask(any())).thenReturn(List.of());

        when(runtimeService.getVariables("p1")).thenReturn(todoVariables());

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThat(workflowService.completeTaskByProcessId(input)).isTrue();

        verify(todoRpcService).syncActivitiTask(eq("p1"), argThat(list -> !list.isEmpty()));
    }

    // ------------------------------------------------------------------ startProcess

    @Test
    @DisplayName("startProcess：已提供 variables 时沿用原 Map")
    void startProcessWithProvidedVariables() {
        WorkflowProcessDto dto = new WorkflowProcessDto();
        dto.setProcessDefinitionKey("leave");
        dto.setStarter("张三");
        dto.setVariables(new HashMap<>());

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        when(processInstance.getProcessInstanceId()).thenReturn("p1");
        when(runtimeService.startProcessInstanceByKeyAndTenantId(eq("leave"), any(), anyMap(), anyString()))
                .thenReturn(processInstance);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        workflowService.startProcess(dto);

        verify(runtimeService).startProcessInstanceByKeyAndTenantId(eq("leave"), any(), anyMap(), anyString());
    }

    // ------------------------------------------------------------------ getNextTask

    private void stubNextTaskContext(String activityId) {
        Task taskInstance = Mockito.mock(Task.class);
        when(taskInstance.getProcessInstanceId()).thenReturn("p1");
        when(taskInstance.getExecutionId()).thenReturn("e1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(taskInstance);

        ExecutionEntity execution = Mockito.mock(ExecutionEntity.class);
        when(execution.getProcessDefinitionId()).thenReturn("pd1");
        when(execution.getActivityId()).thenReturn(activityId);

        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(execution);

        when(runtimeService.getVariables("e1")).thenReturn(new HashMap<>());
    }

    @Test
    @DisplayName("getNextTask：目标不是流转节点时返回 null")
    void getNextTaskSkipsNonFlowNodeTarget() {
        stubNextTaskContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        // SequenceFlow 属于 FlowElement 但不是 FlowNode，用于覆盖"非流转节点"分支
        SequenceFlow notFlowNode = new SequenceFlow();
        notFlowNode.setId("sfx");
        flow("f1", ut1, notFlowNode, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, notFlowNode));

        assertThat(workflowService.getNextTask("t1", new HashMap<>())).isNull();
    }

    @Test
    @DisplayName("getNextTask：带变量时条件表达式命中下一节点")
    void getNextTaskEvaluatesConditionWithVariables() {
        stubNextTaskContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask ut2 = userTask("ut2", "下一节点");
        flow("f1", ut1, ut2, null, "${true}");

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, ut2));

        WorkflowTaskDto dto = workflowService.getNextTask("t1", Map.of("k", "v"));

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("下一节点");
    }

    // ------------------------------------------------------------------ getRelateTask

    @Test
    @DisplayName("getRelateTask：目标节点带候选人与候选组")
    void getRelateTaskCollectsCandidateIdentityLinks() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask target = userTask("ut2", "目标节点");
        target.setCandidateUsers(List.of("userA"));
        target.setCandidateGroups(List.of("roleA"));
        flow("f1", ut1, target, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, target));

        List<WorkflowTaskDto> out = workflowService.getRelateTask("t1", true, new HashMap<>());

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getIdentityLinks()).hasSize(2);
    }

    @Test
    @DisplayName("getRelateTask：网关非默认连线条件命中时纳入目标")
    void getRelateTaskGatewaySatisfiedCondition() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask hit = userTask("ut2", "命中节点");
        UserTask fallback = userTask("ut3", "默认节点");
        flow("fToGw", ut1, gateway, null, null);
        flow("fHit", gateway, hit, null, "${true}");
        flow("fDef", gateway, fallback, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, gateway, hit, fallback));

        List<WorkflowTaskDto> out = workflowService.getRelateTask("t1", true, new HashMap<>());

        assertThat(out).extracting(WorkflowTaskDto::getName).containsExactly("命中节点");
    }

    @Test
    @DisplayName("getRelateTask：目标既非用户任务也非网关时忽略")
    void getRelateTaskIgnoresServiceTaskTarget() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId("st");
        flow("f1", ut1, serviceTask, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, serviceTask));

        assertThat(workflowService.getRelateTask("t1", true, new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("getRelateTask：网关默认线指向非用户任务时任务列表为空")
    void getRelateTaskGatewayDefaultToEndEvent() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask conditional = userTask("ut2", "条件不满足");
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        flow("fToGw", ut1, gateway, null, null);
        flow("fCond", gateway, conditional, null, "${false}");
        flow("fDef", gateway, endEvent, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, gateway, conditional, endEvent));

        assertThat(workflowService.getRelateTask("t1", true, new HashMap<>())).isEmpty();
    }

    // ------------------------------------------------------------------ getRelatedNode

    @Test
    @DisplayName("getRelatedNode：variables 为 null 时直接使用流程变量")
    void getRelatedNodeWithNullVariables() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1));

        assertThat(workflowService.getRelatedNode("t1", null, true, null)).isEmpty();
    }

    @Test
    @DisplayName("getRelatedNode：当前节点不是流转节点时返回空")
    void getRelatedNodeWithNonFlowNodeElement() {
        stubRelateContext("ut1");

        SequenceFlow sequenceFlow = new SequenceFlow();
        sequenceFlow.setId("ut1");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(sequenceFlow));

        assertThat(workflowService.getRelatedNode("t1", null, true, new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("getRelatedNode：目标节点带 formKey 时解析表达式")
    void getRelatedNodeResolvesTargetFormKey() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask target = userTask("ut2", "目标节点");
        target.setFormKey("fk");
        flow("f1", ut1, target, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, target));

        List<RelatedNodeInfo> out = workflowService.getRelatedNode("t1", null, true, new HashMap<>());

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getFormKey()).isEqualTo("fk");
    }

    @Test
    @DisplayName("getRelatedNode：网关非默认连线条件命中时纳入目标")
    void getRelatedNodeGatewaySatisfiedCondition() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask hit = userTask("ut2", "命中节点");
        UserTask fallback = userTask("ut3", "默认节点");
        flow("fToGw", ut1, gateway, null, null);
        flow("fHit", gateway, hit, null, "${true}");
        flow("fDef", gateway, fallback, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, gateway, hit, fallback));

        List<RelatedNodeInfo> out = workflowService.getRelatedNode("t1", null, true, new HashMap<>());

        assertThat(out).extracting(RelatedNodeInfo::getName).containsExactly("命中节点");
    }

    @Test
    @DisplayName("getRelatedNode：目标既非用户任务/网关/结束节点时忽略")
    void getRelatedNodeIgnoresServiceTaskTarget() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId("st");
        flow("f1", ut1, serviceTask, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, serviceTask));

        assertThat(workflowService.getRelatedNode("t1", null, true, new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("getRelatedNode：结束节点连线条件命中时纳入")
    void getRelatedNodeEndEventWithSatisfiedCondition() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        flow("f1", ut1, endEvent, null, "${true}");

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, endEvent));

        List<RelatedNodeInfo> out = workflowService.getRelatedNode("t1", null, true, new HashMap<>());

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getNodeType()).isEqualTo("endEvent");
    }

    // ------------------------------------------------------------------ recall

    private HistoricTaskInstance stubRecallContext(List<Task> userTasks, String lastTaskKey) {
        HistoricTaskInstance lastTask = Mockito.mock(HistoricTaskInstance.class);
        when(lastTask.getAssignee()).thenReturn(String.valueOf(USER_ID));
        when(lastTask.getTaskDefinitionKey()).thenReturn(lastTaskKey);

        HistoricTaskInstanceQuery historicQuery = self(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.list()).thenReturn(List.of(lastTask));

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        ProcessDefinitionEntity definitionEntity = Mockito.mock(ProcessDefinitionEntity.class);
        when(definitionEntity.getId()).thenReturn("pd1");
        when(repositoryService.getProcessDefinition("pd1")).thenReturn(definitionEntity);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(userTasks);

        return lastTask;
    }

    private static Task currentTask(String taskDefinitionKey, String formKey) {
        Task task = Mockito.mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn(taskDefinitionKey);
        when(task.getFormKey()).thenReturn(formKey);
        return task;
    }

    @Test
    @DisplayName("checkProcessCanRecallPre：上一节点非用户任务且当前节点允许撤回")
    void checkProcessCanRecallPreNonUserTaskLastNode() {
        stubRecallContext(List.of(currentTask("ut2", "{\"activiti\":{\"callBackPre\":true}}")), "svc");

        ServiceTask serviceTask = new ServiceTask();
        serviceTask.setId("svc");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(serviceTask));

        assertThat(workflowService.checkProcessCanRecallPre("p1")).isTrue();
    }

    @Test
    @DisplayName("checkProcessCanRecallPre：上一节点为会签节点时不可撤回")
    void checkProcessCanRecallPreMultiInstanceLastNode() {
        stubRecallContext(List.of(currentTask("ut2", "{\"activiti\":{\"callBackPre\":true}}")), "ut1");

        UserTask lastNode = userTask("ut1", "会签节点");
        lastNode.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(lastNode));

        assertThat(workflowService.checkProcessCanRecallPre("p1")).isFalse();
    }

    @Test
    @DisplayName("checkProcessCanRecallPre：当前任务数不为 1 时不可撤回")
    void checkProcessCanRecallPreNoCurrentTask() {
        stubRecallContext(List.of(), "ut1");

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(userTask("ut1", "普通节点")));

        assertThat(workflowService.checkProcessCanRecallPre("p1")).isFalse();
    }

    @Test
    @DisplayName("checkProcessCanRecallPre：当前节点 formKey 反序列化为 null 时不可撤回")
    void checkProcessCanRecallPreWithJsonNullFormKey() {
        // formKey 为字面量 "null"（合法 JSON null）时，getTaskFormKey 反序列化结果为 null，
        // 命中 taskFormKey == null 的防御分支。
        stubRecallContext(List.of(currentTask("ut2", "null")), "ut1");

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(userTask("ut1", "普通节点")));

        assertThat(workflowService.checkProcessCanRecallPre("p1")).isFalse();
    }

    // ------------------------------------------------------------------ getProcessDescription

    @Test
    @DisplayName("getProcessDescription：formKey 中配置候选人以人员维度参与鉴权")
    void getProcessDescriptionWithCandidatePsns() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        UserFullListDto userInfo = new UserFullListDto();
        userInfo.setId(USER_ID);
        userInfo.setUserRoles(new ArrayList<>());
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(userInfo);

        Task task = Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getAssignee()).thenReturn("roleB");
        when(task.getName()).thenReturn("审批节点");
        when(task.getFormKey()).thenReturn("{\"activiti\":{\"candidatePsns\":\"2001\"}}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("p1")).thenReturn(new HashMap<>());
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of());

        List<ProcessDesOutPutDto> out = workflowService.getProcessDescription(List.of("p1"));

        assertThat(out.getFirst().isHasPermission()).isFalse();
    }

    // ------------------------------------------------- 长尾分支：鉴权与待办装配

    @Test
    @DisplayName("getTasksByProcess：checkAuth=true 且任务无代理人时被过滤")
    void getTasksByProcessWithAuthFiltersBlankAssigneeTask() {
        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        assertThat(workflowService.getTasksByProcess(List.of("p1"), true)).isEmpty();
    }

    @Test
    @DisplayName("getTasksByProcess：checkAuth=true 且无权限、formKey 为空时被过滤")
    void getTasksByProcessWithAuthFiltersNoPermissionTask() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        UserFullListDto userInfo = new UserFullListDto();
        userInfo.setId(USER_ID);
        userInfo.setUserRoles(new ArrayList<>());
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(userInfo);

        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("roleZ");
        // formKey 为 null，覆盖 getTaskFormKey 的空值兜底
        when(task.getFormKey()).thenReturn(null);
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        assertThat(workflowService.getTasksByProcess(List.of("p1"), true)).isEmpty();
    }

    @Test
    @DisplayName("getProcessDescription：任务代理人为空时鉴权列表为空")
    void getProcessDescriptionWithEmptyAssignees() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        UserFullListDto userInfo = new UserFullListDto();
        userInfo.setId(USER_ID);
        userInfo.setUserRoles(new ArrayList<>());
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(userInfo);

        Task task = Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getAssignee()).thenReturn("");
        when(task.getName()).thenReturn("审批节点");
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("p1")).thenReturn(new HashMap<>());
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of());

        List<ProcessDesOutPutDto> out = workflowService.getProcessDescription(List.of("p1"));

        assertThat(out.getFirst().isHasPermission()).isFalse();
    }

    @Test
    @DisplayName("startProcess：匿名且未指定发起人时发起人为空")
    void startProcessAnonymousWithoutStarter() {
        LoginUserIdContextHolder.clear();

        WorkflowProcessDto dto = new WorkflowProcessDto();
        dto.setProcessDefinitionKey("leave");
        dto.setVariables(new HashMap<>());

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        when(runtimeService.startProcessInstanceByKeyAndTenantId(eq("leave"), any(), anyMap(), anyString()))
                .thenReturn(processInstance);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        workflowService.startProcess(dto);

        verify(runtimeService).startProcessInstanceByKeyAndTenantId(eq("leave"), any(),
                argThat(variables -> variables.get(FlowableConstants.STARTER) == null), anyString());
    }

    @Test
    @DisplayName("getNextTask：条件表达式恒为 false 时返回空")
    void getNextTaskConditionFalseWithVariables() {
        stubNextTaskContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask ut2 = userTask("ut2", "下一节点");
        flow("f1", ut1, ut2, null, "${false}");

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, ut2));

        assertThat(workflowService.getNextTask("t1", Map.of("k", "v"))).isNull();
    }

    @Test
    @DisplayName("getRelatedNode：网关默认线指向结束节点时兜底为空")
    void getRelatedNodeGatewayDefaultToEndEvent() {
        stubRelateContext("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask conditional = userTask("ut2", "条件不满足");
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        flow("fToGw", ut1, gateway, null, null);
        flow("fCond", gateway, conditional, null, "${false}");
        flow("fDef", gateway, endEvent, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(ut1, gateway, conditional, endEvent));

        List<RelatedNodeInfo> out = workflowService.getRelatedNode("t1", null, true, new HashMap<>());

        // 默认线指向结束节点，未命中用户任务的兜底逻辑
        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getNodeType()).isEqualTo("endEvent");
    }

    /** 构造"流程实例 + 单个任务 + 任务表单"的待办同步上下文。 */
    private void stubTodoSyncContext(String assignee, String formKey) {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getName()).thenReturn("审批节点");
        when(task.getTaskDefinitionKey()).thenReturn("ut1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of(task));

        TaskFormData taskFormData = Mockito.mock(TaskFormData.class);
        when(taskFormData.getFormKey()).thenReturn(formKey);
        when(taskFormData.getFormProperties()).thenReturn(List.of());
        when(formService.getTaskFormData(any())).thenReturn(taskFormData);
        when(taskService.getIdentityLinksForTask(any())).thenReturn(List.of());

        when(runtimeService.getVariables("p1")).thenReturn(todoVariables());
    }

    @Test
    @DisplayName("completeTaskByProcessId：任务无代理人时不生成待办")
    void completeTaskByProcessIdSkipsTodoWithoutAssignee() {
        stubTodoSyncContext("", "{}");

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThat(workflowService.completeTaskByProcessId(input)).isTrue();

        verify(todoRpcService).syncActivitiTask(eq("p1"), argThat(List::isEmpty));
    }

    @Test
    @DisplayName("completeTaskByProcessId：formKey 关闭 addTodo 时不生成待办")
    void completeTaskByProcessIdSkipsTodoWhenAddTodoDisabled() {
        stubTodoSyncContext("1001", "{\"activiti\":{\"notice\":{\"addTodo\":false}}}");

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThat(workflowService.completeTaskByProcessId(input)).isTrue();

        verify(todoRpcService).syncActivitiTask(eq("p1"), argThat(List::isEmpty));
    }

    @Test
    @DisplayName("checkProcessCanRecallPre：当前节点未开启撤回标记时不可撤回")
    void checkProcessCanRecallPreCallBackDisabled() {
        stubRecallContext(List.of(currentTask("ut2", "{}")), "ut1");

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(userTask("ut1", "普通节点")));

        assertThat(workflowService.checkProcessCanRecallPre("p1")).isFalse();
    }

    @Test
    @DisplayName("checkProcessCanRecallPre：当前节点与上一节点相同时不可撤回")
    void checkProcessCanRecallPreSameNode() {
        stubRecallContext(List.of(currentTask("ut1", "{\"activiti\":{\"callBackPre\":true}}")), "ut1");

        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(userTask("ut1", "普通节点")));

        assertThat(workflowService.checkProcessCanRecallPre("p1")).isFalse();
    }

    // ------------------------------------------------- 长尾分支：鉴权放行与表达式为空

    @Test
    @DisplayName("getTasksByProcess：checkAuth=true 且权限校验通过时保留任务")
    void getTasksByProcessWithAuthKeepsPermittedTask() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        UserFullListDto userInfo = new UserFullListDto();
        userInfo.setId(USER_ID);
        userInfo.setUserRoles(new ArrayList<>());
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(userInfo);

        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn(String.valueOf(USER_ID));
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        TaskFormData taskFormData = Mockito.mock(TaskFormData.class);
        when(taskFormData.getFormKey()).thenReturn("{}");
        when(taskFormData.getFormProperties()).thenReturn(List.of());
        when(formService.getTaskFormData(any())).thenReturn(taskFormData);
        when(taskService.getIdentityLinksForTask(any())).thenReturn(List.of());

        List<WorkflowTaskDto> out = workflowService.getTasksByProcess(List.of("p1"), true);

        assertThat(out).hasSize(1);
    }

    @Test
    @DisplayName("updateFlowVariables：节点未配置代理人表达式时不重派")
    void updateFlowVariablesWithBlankAssigneeExpression() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Map<String, Object> variables = Map.of("manager", "8888");
        when(runtimeService.getVariables("p1")).thenReturn(variables);

        UserTask blankAssigneeNode = userTask("ut1", "无代理人节点");
        blankAssigneeNode.setAssignee("");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(model(blankAssigneeNode));

        Task task = Mockito.mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn("ut1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("p1");
        input.setVariables(variables);

        workflowService.updateFlowVariables(input);

        verify(todoRpcService, never()).syncActivitiTaskAssigneeChanged(anyString(), anyList());
    }
}
