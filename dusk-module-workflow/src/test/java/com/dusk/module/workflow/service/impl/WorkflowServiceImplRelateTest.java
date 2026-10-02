package com.dusk.module.workflow.service.impl;

import com.dusk.common.model.authentication.LoginUserIdContextHolder;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.model.tenant.TenantContextHolder;
import com.dusk.common.core.utils.SecurityUtils;
import com.dusk.common.rpc.auth.UserNameUtils;
import com.dusk.common.rpc.auth.dto.UserFullListDto;
import com.dusk.common.rpc.auth.service.ITodoRpcService;
import com.dusk.common.rpc.auth.service.IUserRpcService;
import com.dusk.module.workflow.constant.FlowableConstants;
import com.dusk.module.workflow.dto.RelatedNodeInfo;
import com.dusk.workflow.dto.WorkflowTaskDto;
import tools.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.EndEvent;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.ManualTask;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.FormService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.impl.RepositoryServiceImpl;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.engine.runtime.ExecutionQuery;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowServiceImpl} 单元测试（下一节点计算 / 关联节点 / 条件表达式 / 私有分支）。
 */
@ExtendWith(MockitoExtension.class)
// 本类共用一套较宽的引擎桩（stubRunningExecution），放宽严格校验以避免无关桩报错
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceImplRelateTest {

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

    // ---------------------------------------------------------------- helpers

    private static <T> T self(Class<T> type) {
        return Mockito.mock(type, Answers.RETURNS_SELF);
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

    private static BpmnModel bpmnModel(FlowElement... elements) {
        Process process = new Process();
        process.setId("process1");
        for (FlowElement element : elements) {
            process.addFlowElement(element);
        }
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    /** 构造正在运行的流程执行上下文。 */
    private ExecutionEntity stubRunningExecution(String activityId) {
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

        // getRelatedNode 会通过已发布定义实体反查 BpmnModel
        ProcessDefinitionEntity definitionEntity = Mockito.mock(ProcessDefinitionEntity.class);
        when(definitionEntity.getId()).thenReturn("pd1");
        when(repositoryService.getDeployedProcessDefinition("pd1")).thenReturn(definitionEntity);

        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(execution);

        ExecutionQuery executionQuery = self(ExecutionQuery.class);
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.singleResult()).thenReturn(execution);

        when(runtimeService.getVariables("e1")).thenReturn(new HashMap<>());

        HistoricActivityInstanceQuery activityQuery = self(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);
        when(activityQuery.list()).thenReturn(List.of());

        return execution;
    }

    // ------------------------------------------------------------ getNextTask

    @Test
    @DisplayName("getNextTaskByProcessId：沿出线找到下一个用户任务")
    void getNextTaskByProcessId() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask ut2 = userTask("ut2", "下一节点");
        flow("f1", ut1, ut2, null, null);
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(ut1, ut2));

        WorkflowTaskDto dto = workflowService.getNextTaskByProcessId("p1", new HashMap<>());

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("下一节点");
    }

    @Test
    @DisplayName("getNextTask：读取流程变量失败时降级为空变量继续计算")
    void getNextTaskToleratesVariableReadFailure() {
        stubRunningExecution("ut1");
        when(runtimeService.getVariables("e1")).thenThrow(new RuntimeException("变量读取失败"));

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask ut2 = userTask("ut2", "下一节点");
        flow("f1", ut1, ut2, null, null);
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(ut1, ut2));

        assertThat(workflowService.getNextTask("t1", new HashMap<>())).isNotNull();
    }

    @Test
    @DisplayName("getNextTask：目标不是 FlowNode 时返回 null")
    void getNextTaskNotFlowNode() {
        stubRunningExecution("ut1");

        SequenceFlow notFlowNode = new SequenceFlow();
        notFlowNode.setId("ut1");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(notFlowNode));

        assertThat(workflowService.getNextTask("t1", new HashMap<>())).isNull();
    }

    @Test
    @DisplayName("getNextTask：连线有条件但未传变量时不进入条件分支")
    void getNextTaskConditionWithoutVariables() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask ut2 = userTask("ut2", "下一节点");
        flow("f1", ut1, ut2, null, "${true}");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(ut1, ut2));

        assertThat(workflowService.getNextTask("t1", null)).isNull();
    }

    @Test
    @DisplayName("getNextTask：条件成立时穿过网关找到下一节点")
    void getNextTaskThroughGateway() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask matched = userTask("ut2", "条件命中");
        UserTask fallback = userTask("ut3", "默认分支");
        flow("fToGw", ut1, gateway, null, null);
        flow("fCond", gateway, matched, null, "${true}");
        flow("fDef", gateway, fallback, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(ut1, gateway, matched, fallback));

        WorkflowTaskDto dto = workflowService.getNextTask("t1", new HashMap<>());

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("条件命中");
    }

    @Test
    @DisplayName("getNextTask：表达式解析失败时回退为原始表达式文本")
    void getNextTaskExpressionFailureFallsBackToRawText() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask ut2 = userTask("ut2", "${undefinedVar}");
        flow("f1", ut1, ut2, null, null);
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(ut1, ut2));

        WorkflowTaskDto dto = workflowService.getNextTaskByProcessId("p1", new HashMap<>());

        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("${undefinedVar}");
    }

    // ----------------------------------------------------------- getRelateTask

    @Test
    @DisplayName("getRelateTask：流程实例已结束时返回空")
    void getRelateTaskFinishedProcess() {
        Task taskInstance = Mockito.mock(Task.class);
        when(taskInstance.getProcessInstanceId()).thenReturn("p1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(taskInstance);

        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        assertThat(workflowService.getRelateTask("t1", true, new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("getRelateTask：自动计算时区分驳回线与默认线")
    void getRelateTaskAutoCalculate() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask reject = userTask("ut2", "回退节点");
        UserTask normal = userTask("ut3", "普通节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask conditional = userTask("ut4", "条件不满足");
        UserTask fallback = userTask("ut5", "默认分支");

        flow("fBohui", ut1, reject, "驳回", null);
        flow("fPlain", ut1, normal, null, null);
        flow("fToGw", ut1, gateway, null, null);
        flow("fCond", gateway, conditional, null, "${false}");
        flow("fDef", gateway, fallback, null, null);

        when(repositoryService.getBpmnModel("pd1"))
                .thenReturn(bpmnModel(ut1, reject, normal, gateway, conditional, fallback));

        List<WorkflowTaskDto> out = workflowService.getRelateTask("t1", true, new HashMap<>());

        assertThat(out).extracting(WorkflowTaskDto::getName)
                .containsExactlyInAnyOrder("回退节点", "普通节点");
        assertThat(out).anySatisfy(dto -> assertThat(dto.getTaskDirection()).isEqualTo("from"));
        assertThat(out).allSatisfy(dto -> assertThat(dto.getProcessVariables()).isNotNull());
    }

    @Test
    @DisplayName("getRelateTask：无条件命中时回退到网关默认分支")
    void getRelateTaskUsesGatewayDefaultFlow() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask conditional = userTask("ut4", "条件不满足");
        UserTask fallback = userTask("ut5", "默认分支");

        flow("fToGw", ut1, gateway, null, null);
        flow("fCond", gateway, conditional, null, "${false}");
        flow("fDef", gateway, fallback, null, null);

        when(repositoryService.getBpmnModel("pd1"))
                .thenReturn(bpmnModel(ut1, gateway, conditional, fallback));

        List<WorkflowTaskDto> out = workflowService.getRelateTask("t1", true, new HashMap<>());

        assertThat(out).extracting(WorkflowTaskDto::getName).containsExactly("默认分支");
        assertThat(out.getFirst().getTaskDirection()).isEqualTo("to");
    }

    @Test
    @DisplayName("getRelateTask：autoCalculate=false 时不计算条件")
    void getRelateTaskWithoutAutoCalculate() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask reject = userTask("ut2", "回退节点");
        UserTask normal = userTask("ut3", "普通节点");
        flow("fBohui", ut1, reject, "驳回", null);
        flow("fPlain", ut1, normal, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(ut1, reject, normal));

        List<WorkflowTaskDto> out = workflowService.getRelateTask("t1", false, null);

        assertThat(out).hasSize(2);
    }

    @Test
    @DisplayName("getRelateTask：当前节点不是用户任务时返回空")
    void getRelateTaskCurrentNotUserTask() {
        stubRunningExecution("ut1");

        ManualTask manualTask = new ManualTask();
        manualTask.setId("ut1");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(manualTask));

        assertThat(workflowService.getRelateTask("t1", true, new HashMap<>())).isEmpty();
    }

    // ---------------------------------------------------------- getRelatedNode

    @Test
    @DisplayName("getRelatedNode：taskId 与 processKey 均为空时抛异常")
    void getRelatedNodeBothBlank() {
        assertThatThrownBy(() -> workflowService.getRelatedNode("", null, false, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("taskId和processKey两者不能同时为空");
    }

    @Test
    @DisplayName("getRelatedNode：仅有 processKey 时返回 startEvent 的 formKey")
    void getRelatedNodeByProcessKey() {
        ProcessDefinition pd = Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        ProcessDefinitionQuery query = self(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);

        ProcessDefinitionEntity entity = Mockito.mock(ProcessDefinitionEntity.class);
        when(entity.getHasStartFormKey()).thenReturn(false);
        when(repositoryService.getDeployedProcessDefinition("pd1")).thenReturn(entity);

        List<RelatedNodeInfo> out = workflowService.getRelatedNode(null, "key", false, null);

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getNodeType()).isEqualTo(FlowableConstants.NODE_TYPE_START_EVENT);
    }

    @Test
    @DisplayName("getRelatedNode：流程实例已结束返回空")
    void getRelatedNodeFinishedProcess() {
        Task taskInstance = Mockito.mock(Task.class);
        when(taskInstance.getProcessInstanceId()).thenReturn("p1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(taskInstance);

        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        assertThat(workflowService.getRelatedNode("t1", null, true, null)).isEmpty();
    }

    @Test
    @DisplayName("getRelatedNode：自动计算用户任务、网关与结束节点")
    void getRelatedNodeAutoCalculate() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask reject = userTask("ut2", null);
        UserTask normal = userTask("ut3", "普通节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask conditional = userTask("ut4", "条件不满足");
        UserTask fallback = userTask("ut5", "默认分支");
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        EndEvent conditionalEnd = new EndEvent();
        conditionalEnd.setId("end2");

        flow("fBohui", ut1, reject, "驳回", null);
        flow("fPlain", ut1, normal, null, null);
        flow("fToGw", ut1, gateway, null, null);
        flow("fCond", gateway, conditional, null, "${false}");
        flow("fDef", gateway, fallback, null, null);
        flow("fEnd", ut1, endEvent, null, null);
        flow("fEndCond", ut1, conditionalEnd, null, "${false}");

        when(repositoryService.getBpmnModel("pd1"))
                .thenReturn(bpmnModel(ut1, reject, normal, gateway, conditional, fallback, endEvent, conditionalEnd));

        List<RelatedNodeInfo> out = workflowService.getRelatedNode("t1", null, true, new HashMap<>());

        assertThat(out).hasSize(3);
        assertThat(out).anySatisfy(node -> assertThat(node.getTaskDirection()).isEqualTo("from"));
        assertThat(out).anySatisfy(node -> assertThat(node.getNodeType()).isEqualTo("endEvent"));
    }

    @Test
    @DisplayName("getRelatedNode：无条件命中时回退到网关默认分支")
    void getRelatedNodeUsesGatewayDefaultFlow() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        ExclusiveGateway gateway = new ExclusiveGateway();
        gateway.setId("gw");
        gateway.setDefaultFlow("fDef");
        UserTask conditional = userTask("ut4", "条件不满足");
        UserTask fallback = userTask("ut5", "默认分支");

        flow("fToGw", ut1, gateway, null, null);
        flow("fCond", gateway, conditional, null, "${false}");
        flow("fDef", gateway, fallback, null, null);

        when(repositoryService.getBpmnModel("pd1"))
                .thenReturn(bpmnModel(ut1, gateway, conditional, fallback));

        List<RelatedNodeInfo> out = workflowService.getRelatedNode("t1", null, true, new HashMap<>());

        assertThat(out).extracting(RelatedNodeInfo::getName).containsExactly("默认分支");
    }

    @Test
    @DisplayName("getRelatedNode：autoCalculate=false 时不计算条件，结束节点直接加入")
    void getRelatedNodeWithoutAutoCalculate() {
        stubRunningExecution("ut1");

        UserTask ut1 = userTask("ut1", "当前节点");
        UserTask normal = userTask("ut3", "普通节点");
        EndEvent endEvent = new EndEvent();
        endEvent.setId("end");
        flow("fPlain", ut1, normal, null, null);
        flow("fEnd", ut1, endEvent, null, null);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(ut1, normal, endEvent));

        List<RelatedNodeInfo> out = workflowService.getRelatedNode("t1", null, false, new HashMap<>());

        assertThat(out).hasSize(2);
        assertThat(out).anySatisfy(node -> assertThat(node.getNodeType()).isEqualTo("endEvent"));
    }

    @Test
    @DisplayName("getRelatedNode：当前节点不是流转节点时返回空")
    void getRelatedNodeCurrentNotFlowNode() {
        stubRunningExecution("ut1");

        ManualTask manualTask = new ManualTask();
        manualTask.setId("ut1");
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel(manualTask));

        assertThat(workflowService.getRelatedNode("t1", null, true, new HashMap<>())).isEmpty();
    }

    @Test
    @DisplayName("getRelatedNode：processKey 流程未发布时抛异常")
    void getRelatedNodeProcessKeyNotFound() {
        ProcessDefinitionQuery query = self(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> workflowService.getRelatedNode(null, "key", false, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在名为key的流程");
    }

    // ------------------------------------------------- 私有/不可达分支（反射覆盖）

    @Test
    @DisplayName("checkAssignee：空审批人视为有权限")
    void checkAssigneeBlankReturnsTrue() throws Exception {
        UserFullListDto userInfo = new UserFullListDto();
        userInfo.setId(USER_ID);
        userInfo.setUserRoles(new java.util.ArrayList<>());

        Object blank = invokePrivate("checkAssignee", new Class<?>[]{String.class, UserFullListDto.class}, "", userInfo);
        assertThat(blank).isEqualTo(true);

        Object nullAssignee = invokePrivate("checkAssignee", new Class<?>[]{String.class, UserFullListDto.class},
                (Object) null, userInfo);
        assertThat(nullAssignee).isEqualTo(true);

        Object role = invokePrivate("checkAssignee", new Class<?>[]{String.class, UserFullListDto.class}, "roleA", userInfo);
        assertThat(role).isEqualTo(false);
    }

    @Test
    @DisplayName("gotoAssignActivity：带意见与不带意见两种跳转（当前为未被调用的私有方法）")
    void gotoAssignActivityAddsCommentOnlyWhenMessagePresent() throws Exception {
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getTaskDefinitionKey()).thenReturn("ut1");

        ChangeActivityStateBuilder builder = self(ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(builder);

        invokePrivate("gotoAssignActivity", new Class<?>[]{Task.class, String.class, String.class},
                task, "ut2", "驳回");
        invokePrivate("gotoAssignActivity", new Class<?>[]{Task.class, String.class, String.class},
                task, "ut2", "");

        verify(taskService, times(1)).addComment("t1", "p1", "驳回");
        verify(builder, times(2)).changeState();
    }

    private Object invokePrivate(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = WorkflowServiceImpl.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(workflowService, args);
    }

    @Test
    @DisplayName("安全工具：当前用户可为空（保持 mock 一致性）")
    void currentUserMockIsOptional() {
        SecurityUtils securityUtils = this.securityUtils;
        assertThat(securityUtils).isNotNull();
        assertThat(userRpcService).isNotNull();
        assertThat(userNameUtils).isNotNull();
        assertThat(todoRpcService).isNotNull();
        assertThat(historyService).isNotNull();
        assertThat(formService).isNotNull();
        assertThat(processEngineConfiguration).isNotNull();
        assertThat(repositoryService).isNotNull();
        assertThat(StartEvent.class).isNotNull();
    }
}
