package com.dusk.module.workflow.service.impl;

import com.dusk.common.model.authentication.LoginUserIdContextHolder;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.model.model.UserContext;
import com.dusk.common.model.tenant.TenantContextHolder;
import com.dusk.common.core.utils.SecurityUtils;
import com.dusk.common.rpc.auth.UserNameUtils;
import com.dusk.common.rpc.auth.dto.UserFullListDto;
import com.dusk.common.rpc.auth.dto.UserRoleDto;
import com.dusk.common.rpc.auth.service.ITodoRpcService;
import com.dusk.common.rpc.auth.service.IUserRpcService;
import com.dusk.module.workflow.constant.FlowableConstants;
import com.dusk.module.workflow.dto.UserNameDto;
import com.dusk.workflow.dto.CompleteTaskByProcessIdInputDto;
import com.dusk.workflow.dto.CompleteTaskInputDto;
import com.dusk.workflow.dto.ProcessDesOutPutDto;
import com.dusk.workflow.dto.StartProcessInputDto;
import com.dusk.workflow.dto.StartProcessOutDto;
import com.dusk.workflow.dto.UpdateFlowVariablesInput;
import com.dusk.workflow.dto.UpdateTaskAssigneeInput;
import com.dusk.workflow.dto.WorkflowCompleteTaskDto;
import com.dusk.workflow.dto.WorkflowProcessDto;
import com.dusk.workflow.dto.WorkflowTaskDetailDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import com.dusk.workflow.dto.WorkflowTaskHistoryDto;
import tools.jackson.databind.ObjectMapper;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.FormService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.form.FormProperty;
import org.flowable.engine.form.TaskFormData;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.impl.RepositoryServiceImpl;
import org.flowable.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ChangeActivityStateBuilder;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.engine.task.Comment;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowServiceImpl} 单元测试（流程生命周期 / 任务 / 待办同步 / 变量）。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowServiceImplTest {

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

    private static UserContext currentUser(Long id) {
        UserContext ctx = new UserContext();
        ctx.setId(id);
        return ctx;
    }

    private static UserFullListDto user(Long id) {
        UserFullListDto dto = new UserFullListDto();
        dto.setId(id);
        dto.setUserRoles(new ArrayList<>());
        return dto;
    }

    /** 构造仅含指定流程元素的 BpmnModel（元素需挂在主流程上）。 */
    private static BpmnModel bpmnModel(org.flowable.bpmn.model.FlowElement... elements) {
        org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
        process.setId("process1");
        for (org.flowable.bpmn.model.FlowElement element : elements) {
            process.addFlowElement(element);
        }
        BpmnModel model = new BpmnModel();
        model.addProcess(process);
        return model;
    }

    /** 构造仅含单个 UserTask 的 BpmnModel。 */
    private static BpmnModel singleUserTaskModel(String id, String assignee) {
        UserTask userTask = new UserTask();
        userTask.setId(id);
        userTask.setAssignee(assignee);
        return bpmnModel(userTask);
    }

    /** 待办同步所需的流程变量。 */
    private static Map<String, Object> todoVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put(FlowableConstants.TITLE, "标题");
        variables.put(FlowableConstants.TYPE_NAME, "类型");
        variables.put(FlowableConstants.BUSINESS_TYPE, "业务");
        variables.put(FlowableConstants.FILTER_STATION, false);
        variables.put(FlowableConstants.STARTER, "发起人");
        return variables;
    }

    // ------------------------------------------------------------ startProcess

    @Test
    @DisplayName("startProcess：显式发起人直接写入流程变量")
    void startProcessWithExplicitStarter() {
        WorkflowProcessDto dto = new WorkflowProcessDto();
        dto.setProcessDefinitionKey("leave");
        dto.setBusinessKey("bk-1");
        dto.setTitle("标题");
        dto.setTypeName("类型名");
        dto.setType("type");
        dto.setStarter("张三");
        dto.setFilterStation(true);

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessInstanceId()).thenReturn("p1");
        when(processInstance.getId()).thenReturn("p1");
        when(runtimeService.startProcessInstanceByKeyAndTenantId(eq("leave"), eq("bk-1"), anyMap(), eq("1")))
                .thenReturn(processInstance);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        StartProcessOutDto out = workflowService.startProcess(dto);

        assertThat(out.getProcessInstanceId()).isEqualTo("p1");
        assertThat(out.getTaskInfos()).isEmpty();
        verify(runtimeService).startProcessInstanceByKeyAndTenantId(eq("leave"), eq("bk-1"),
                org.mockito.ArgumentMatchers.argThat(variables -> "张三".equals(variables.get(FlowableConstants.STARTER))
                        && "标题".equals(variables.get(FlowableConstants.TITLE))
                        && Boolean.TRUE.equals(variables.get(FlowableConstants.FILTER_STATION))), eq("1"));
        verify(todoRpcService).syncActivitiTask(eq("p1"), anyList());
    }

    @Test
    @DisplayName("startProcess：未指定发起人时用当前登录人，变量为 null 时新建 Map")
    void startProcessUsesCurrentUserAsStarter() {
        WorkflowProcessDto dto = new WorkflowProcessDto();
        dto.setProcessDefinitionKey("leave");
        dto.setVariables(null);
        dto.setStarter(null);

        UserFullListDto curr = user(USER_ID);
        curr.setName("李四");
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(curr);

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessInstanceId()).thenReturn("p1");
        when(processInstance.getId()).thenReturn("p1");
        when(runtimeService.startProcessInstanceByKeyAndTenantId(eq("leave"), any(), anyMap(), eq("1")))
                .thenReturn(processInstance);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        workflowService.startProcess(dto);

        verify(runtimeService).startProcessInstanceByKeyAndTenantId(eq("leave"), any(),
                org.mockito.ArgumentMatchers.argThat(variables -> "李四".equals(variables.get(FlowableConstants.STARTER))), eq("1"));
    }

    @Test
    @DisplayName("startProcess：引擎异常被包装为 BusinessException")
    void startProcessWrapsEngineException() {
        WorkflowProcessDto dto = new WorkflowProcessDto();
        dto.setProcessDefinitionKey("leave");
        dto.setStarter("张三");
        when(runtimeService.startProcessInstanceByKeyAndTenantId(anyString(), any(), anyMap(), anyString()))
                .thenThrow(new RuntimeException("引擎挂了"));

        assertThatThrownBy(() -> workflowService.startProcess(dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("引擎挂了");
    }

    // -------------------------------------------------------------- delProcess

    @Test
    @DisplayName("delProcess：实例存在时删除并同步待办")
    void delProcessExisting() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(processInstance);

        assertThat(workflowService.delProcess("p1", "取消")).isTrue();

        verify(runtimeService).deleteProcessInstance("p1", "取消");
        verify(todoRpcService).syncActivitiTask(eq("p1"), anyList());
    }

    @Test
    @DisplayName("delProcess：实例不存在时不做任何删除")
    void delProcessNotExisting() {
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThat(workflowService.delProcess("p1", "取消")).isTrue();

        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
        verify(todoRpcService, never()).syncActivitiTask(anyString(), anyList());
    }

    // --------------------------------------------------------- checkProcessEnd

    @Test
    @DisplayName("checkProcessEnd：实例不存在视为已结束")
    void checkProcessEnd() {
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThat(workflowService.checkProcessEnd("p1")).isTrue();
    }

    @Test
    @DisplayName("checkProcessEnd：实例存在视为未结束")
    void checkProcessEndRunning() {
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(Mockito.mock(ProcessInstance.class));

        assertThat(workflowService.checkProcessEnd("p1")).isFalse();
    }

    // ------------------------------------------------------------ readResource

    @Test
    @DisplayName("readResource：流程已结束时取全部历史活动节点")
    void readResourceFinished() {
        HistoricProcessInstance hpi = Mockito.mock(HistoricProcessInstance.class);
        when(hpi.getProcessDefinitionId()).thenReturn("pd1");
        HistoricProcessInstanceQuery hpiQuery = self(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(hpi);

        HistoricActivityInstance activity = Mockito.mock(HistoricActivityInstance.class);
        when(activity.getActivityId()).thenReturn("a1");
        HistoricActivityInstanceQuery haiQuery = self(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(haiQuery);
        when(haiQuery.list()).thenReturn(List.of(activity));
        when(hpiQuery.count()).thenReturn(1L);

        BpmnModel bpmnModel = new BpmnModel();
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel);
        stubDiagramGeneration("png".getBytes(StandardCharsets.UTF_8));

        assertThat(workflowService.readResource("p1")).isEqualTo("png".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("readResource：流程未结束且无活动节点时回退到 endEvent")
    void readResourceNotFinishedAndNoActiveActivity() {
        HistoricProcessInstance hpi = Mockito.mock(HistoricProcessInstance.class);
        when(hpi.getProcessDefinitionId()).thenReturn("pd1");
        HistoricProcessInstanceQuery hpiQuery = self(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(hpi);
        when(hpiQuery.count()).thenReturn(0L);

        HistoricActivityInstance endActivity = Mockito.mock(HistoricActivityInstance.class);
        when(endActivity.getActivityId()).thenReturn("end");
        HistoricActivityInstanceQuery haiQuery = self(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(haiQuery);
        when(haiQuery.singleResult()).thenReturn(endActivity);

        when(runtimeService.getActiveActivityIds("p1")).thenReturn(new ArrayList<>());

        BpmnModel bpmnModel = new BpmnModel();
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel);
        byte[] png = "png".getBytes(StandardCharsets.UTF_8);
        stubDiagramGeneration(png);

        assertThat(workflowService.readResource("p1")).isEqualTo(png);
    }

    private void stubDiagramGeneration(byte[] png) {
        ProcessDiagramGenerator generator = Mockito.mock(ProcessDiagramGenerator.class);
        when(processEngineConfiguration.getProcessDiagramGenerator()).thenReturn(generator);
        when(processEngineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getLabelFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getAnnotationFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getClassLoader()).thenReturn(getClass().getClassLoader());
        when(generator.generateDiagram(any(), anyString(), anyList(), anyList(), anyString(), anyString(),
                anyString(), any(), org.mockito.ArgumentMatchers.anyDouble(), anyBoolean()))
                .thenReturn(new ByteArrayInputStream(png));
    }

    // ----------------------------------------------------------------- getTask

    @Test
    @DisplayName("getTask：任务不存在抛出业务异常")
    void getTaskNotFound() {
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> workflowService.getTask("t1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审批任务不存在");
    }

    @Test
    @DisplayName("getTask：返回任务详情（含表单属性与候选人）")
    void getTaskFound() {
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getTaskDefinitionKey()).thenReturn("ut1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        FormProperty formProperty = Mockito.mock(FormProperty.class);
        when(formProperty.getId()).thenReturn("f1");
        TaskFormData formData = Mockito.mock(TaskFormData.class);
        when(formData.getFormProperties()).thenReturn(List.of(formProperty));
        when(formData.getFormKey()).thenReturn("fk");
        when(formService.getTaskFormData("t1")).thenReturn(formData);

        IdentityLink identityLink = Mockito.mock(IdentityLink.class);
        when(taskService.getIdentityLinksForTask("t1")).thenReturn(List.of(identityLink));

        WorkflowTaskDto dto = workflowService.getTask("t1");

        assertThat(dto.getFormKey()).isEqualTo("fk");
        assertThat(dto.getDefinitionKey()).isEqualTo("ut1");
        assertThat(dto.getFormProperties()).hasSize(1);
        assertThat(dto.getIdentityLinks()).hasSize(1);
    }

    // ------------------------------------------------------- getProcessDescription

    @Test
    @DisplayName("getProcessDescription：processIds 为 null 返回空")
    void getProcessDescriptionNullIds() {
        assertThat(workflowService.getProcessDescription(null)).isEmpty();
    }

    @Test
    @DisplayName("getProcessDescription：processIds 为空返回空")
    void getProcessDescriptionEmptyIds() {
        assertThat(workflowService.getProcessDescription(List.of())).isEmpty();
    }

    @Test
    @DisplayName("getProcessDescription：无运行任务则标记为已结束")
    void getProcessDescriptionFinished() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of());

        List<ProcessDesOutPutDto> out = workflowService.getProcessDescription(List.of("p1"));

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().isFinished()).isTrue();
        assertThat(out.getFirst().getDescription()).isEqualTo("流程已结束");
    }

    @Test
    @DisplayName("getProcessDescription：运行中且有权限时生成待办描述")
    void getProcessDescriptionWithPermission() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        UserFullListDto userInfo = user(USER_ID);
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(userInfo);

        Task task = Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getAssignee()).thenReturn("1001");
        when(task.getName()).thenReturn("审批节点");
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("p1")).thenReturn(Map.of("k", "v"));

        UserNameDto name = new UserNameDto();
        name.setProcessInstanceId("p1");
        name.setAssigneeName("张三");
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of(name));

        List<ProcessDesOutPutDto> out = workflowService.getProcessDescription(List.of("p1"));

        assertThat(out.getFirst().isHasPermission()).isTrue();
        assertThat(out.getFirst().getTaskName()).isEqualTo("审批节点");
        assertThat(out.getFirst().getFormKey()).isEqualTo("{}");
        assertThat(out.getFirst().getVariables()).containsEntry("k", "v");
        assertThat(out.getFirst().getDescription()).contains("张三").contains("审批节点");
    }

    @Test
    @DisplayName("getProcessDescription：无当前用户（userInfo 为空）且无用户名时用任务名")
    void getProcessDescriptionWithoutUserInfo() {
        when(securityUtils.getCurrentUser()).thenReturn(null);

        Task task = Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getAssignee()).thenReturn("roleA");
        when(task.getName()).thenReturn("审批节点");
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("p1")).thenReturn(new HashMap<>());
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of());

        List<ProcessDesOutPutDto> out = workflowService.getProcessDescription(List.of("p1"));

        assertThat(out.getFirst().isHasPermission()).isFalse();
        assertThat(out.getFirst().getDescription()).isEqualTo("审批节点");
    }

    @Test
    @DisplayName("getProcessDescription：formKey 中配置候选角色时按角色授权")
    void getProcessDescriptionRolePermission() {
        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        UserFullListDto userInfo = user(USER_ID);
        UserRoleDto role = new UserRoleDto();
        role.setRoleName("roleA");
        userInfo.setUserRoles(List.of(role));
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(userInfo);

        Task task = Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getAssignee()).thenReturn("roleB");
        when(task.getName()).thenReturn("审批节点");
        when(task.getFormKey()).thenReturn("{\"activiti\":{\"candidateRoles\":\"roleA\"}}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("p1")).thenReturn(new HashMap<>());
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of());

        List<ProcessDesOutPutDto> out = workflowService.getProcessDescription(List.of("p1"));

        assertThat(out.getFirst().isHasPermission()).isTrue();
    }

    // -------------------------------------------------------- getTasksByProcess

    @Test
    @DisplayName("getTasksByProcess：默认校验权限")
    void getTasksByProcessDefaultAuth() {
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        assertThat(workflowService.getTasksByProcess(List.of("p1"))).isEmpty();
    }

    @Test
    @DisplayName("getTasksByProcess：普通代理人无需初始化直属上级")
    void getTasksByProcessNormalAssignee() {
        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("1001");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        TaskFormData formDataStub = taskFormData();
        when(formService.getTaskFormData(any())).thenReturn(formDataStub);
        when(taskService.getIdentityLinksForTask(any())).thenReturn(List.of());

        List<WorkflowTaskDto> out = workflowService.getTasksByProcessAndInitAssignee(List.of("p1"), false);

        assertThat(out).hasSize(1);
        verify(taskService, never()).saveTask(any());
    }

    @Test
    @DisplayName("getTasksByProcess：占位符可取到直属上级时回填代理人")
    void getTasksByProcessPlaceholderResolved() {
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn(FlowableConstants.PLACE_HOLDER_DIRECT_LEADER);
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        when(userRpcService.getSuperiorId(USER_ID)).thenReturn(9999L);
        TaskFormData formDataStub = taskFormData();
        when(formService.getTaskFormData(any())).thenReturn(formDataStub);
        when(taskService.getIdentityLinksForTask(any())).thenReturn(List.of());

        workflowService.getTasksByProcessAndInitAssignee(List.of("p1"), false);

        verify(task).setAssignee("9999");
        verify(taskService).saveTask(task);
    }

    @Test
    @DisplayName("getTasksByProcess：占位符取不到直属上级时抛异常")
    void getTasksByProcessPlaceholderUnresolved() {
        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn(FlowableConstants.PLACE_HOLDER_DIRECT_LEADER);
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(userRpcService.getSuperiorId(USER_ID)).thenReturn(null);

        assertThatThrownBy(() -> workflowService.getTasksByProcessAndInitAssignee(List.of("p1"), false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("can not find the leader of current user");
    }

    private TaskFormData taskFormData() {
        TaskFormData formData = Mockito.mock(TaskFormData.class);
        when(formData.getFormProperties()).thenReturn(List.of());
        // 故意给一个非法 JSON，用于覆盖 getTaskFormKey 的异常兜底分支
        when(formData.getFormKey()).thenReturn("{invalid");
        return formData;
    }

    // --------------------------------------------- getProcessDefinitionFirstFormKey

    @Test
    @DisplayName("getProcessDefinitionFirstFormKey：流程定义不存在")
    void getProcessDefinitionFirstFormKeyNotFound() {
        ProcessDefinitionQuery query = self(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> workflowService.getProcessDefinitionFirstFormKey("key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在名为key的流程");
    }

    @Test
    @DisplayName("getProcessDefinitionFirstFormKey：部署定义不存在")
    void getProcessDefinitionFirstFormKeyNotDeployed() {
        ProcessDefinition pd = Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        ProcessDefinitionQuery query = self(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        when(repositoryService.getDeployedProcessDefinition("pd1")).thenReturn(null);

        assertThatThrownBy(() -> workflowService.getProcessDefinitionFirstFormKey("key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程尚未发布");
    }

    @Test
    @DisplayName("getProcessDefinitionFirstFormKey：有开始表单时读取开始表单key")
    void getProcessDefinitionFirstFormKeyWithStartForm() {
        ProcessDefinition pd = Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        ProcessDefinitionQuery query = self(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);

        ProcessDefinitionEntity entity = Mockito.mock(ProcessDefinitionEntity.class);
        when(entity.getId()).thenReturn("pd1");
        when(entity.getHasStartFormKey()).thenReturn(true);
        when(repositoryService.getDeployedProcessDefinition("pd1")).thenReturn(entity);

        assertThat(workflowService.getProcessDefinitionFirstFormKey("key")).isNull();

        verify(formService).getStartFormKey("pd1");
    }

    @Test
    @DisplayName("getProcessDefinitionFirstFormKey：无开始表单直接返回 null")
    void getProcessDefinitionFirstFormKeyWithoutStartForm() {
        ProcessDefinition pd = Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        ProcessDefinitionQuery query = self(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);

        ProcessDefinitionEntity entity = Mockito.mock(ProcessDefinitionEntity.class);
        when(entity.getHasStartFormKey()).thenReturn(false);
        when(repositoryService.getDeployedProcessDefinition("pd1")).thenReturn(entity);

        assertThat(workflowService.getProcessDefinitionFirstFormKey("key")).isNull();

        verify(formService, never()).getStartFormKey(anyString());
    }

    // ------------------------------------------------------ completeTaskByProcessId

    @Test
    @DisplayName("completeTaskByProcessId：流程不存在")
    void completeTaskByProcessIdProcessNotFound() {
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThatThrownBy(() -> workflowService.completeTaskByProcessId(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法找到流程");
    }

    @Test
    @DisplayName("completeTaskByProcessId：正常完成任务并同步待办")
    void completeTaskByProcessIdOk() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(processInstance);

        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn("");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        // 第一次 list() 用于定位任务，第二次用于同步待办
        when(taskQuery.list()).thenReturn(List.of(task), List.of());

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThat(workflowService.completeTaskByProcessId(input)).isTrue();

        // CompleteTaskByProcessIdInputDto 未设置变量，命令的两个可变参数均为 null
        verify(taskService).complete(eq("t1"), nullable(Map.class), nullable(Map.class));
        verify(todoRpcService).syncActivitiTask(eq("p1"), anyList());
    }

    @Test
    @DisplayName("getTaskIdByProcessInstanceId：无任务时抛异常")
    void completeTaskByProcessIdNoTask() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(processInstance);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThatThrownBy(() -> workflowService.completeTaskByProcessId(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("无法查询到关联的任务");
    }

    @Test
    @DisplayName("getTaskIdByProcessInstanceId：多任务时抛异常")
    void completeTaskByProcessIdMultipleTasks() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(processInstance);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(Mockito.mock(Task.class), Mockito.mock(Task.class)));

        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("p1");

        assertThatThrownBy(() -> workflowService.completeTaskByProcessId(input))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("有多个任务在运行中");
    }

    // -------------------------------------------------- completeTask(taskId, dto)

    @Test
    @DisplayName("completeTask：任务无代理人时跳过权限校验，处理人写入当前登录用户")
    void completeTaskWithoutAssignee() {
        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();

        assertThat(workflowService.completeTask("t1", dto)).isTrue();

        // 处理人取当前登录用户 id（见 WorkflowServiceImpl#completeTask 去登录改造）
        verify(taskService).setAssignee("t1", String.valueOf(USER_ID));
        verify(taskService, never()).addComment(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("completeTask：有代理人但无权限时抛异常")
    void completeTaskWithoutPermission() {
        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("otherUser");
        when(task.getFormKey()).thenReturn("{}");
        when(task.getProcessInstanceId()).thenReturn("p1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(user(USER_ID));

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();
        dto.setComment("同意");

        assertThatThrownBy(() -> workflowService.completeTask("t1", dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有权限，非法提交");

        verify(taskService).addComment("t1", "p1", "同意");
    }

    @Test
    @DisplayName("completeTask：有权限且设置全局/局部变量")
    void completeTaskWithPermissionAndVariables() {
        Task task = Mockito.mock(Task.class);
        when(task.getAssignee()).thenReturn("1001");
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        when(securityUtils.getCurrentUser()).thenReturn(currentUser(USER_ID));
        UserFullListDto userInfo = user(USER_ID);
        when(userRpcService.getUserFullById(USER_ID)).thenReturn(userInfo);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();
        Map<String, Object> variables = new HashMap<>();
        variables.put("k", "v");
        dto.setVariables(variables);
        Map<String, Object> localVariables = new HashMap<>();
        localVariables.put("local", "x");
        dto.setLocalVariables(localVariables);

        assertThat(workflowService.completeTask("t1", dto)).isTrue();

        verify(taskService).setVariables("t1", variables);
        verify(taskService).setVariablesLocal("t1", localVariables);
        verify(taskService).complete("t1", variables, null);
    }

    @Test
    @DisplayName("completeTask：未登录用户无权限")
    void completeTaskWithAnonymousUser() {
        LoginUserIdContextHolder.clear();
        Task task = Mockito.mock(Task.class);
        // 未登录时 hasTaskPermission 直接短路返回 false，不会读取 formKey
        when(task.getAssignee()).thenReturn("1001");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();

        assertThatThrownBy(() -> workflowService.completeTask("t1", dto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有权限，非法提交");
    }

    @Test
    @DisplayName("completeTask：任务不存在时空指针（当前实现未做保护）")
    void completeTaskTaskMissing() {
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();

        assertThatThrownBy(() -> workflowService.completeTask("t1", dto))
                .isInstanceOf(NullPointerException.class);
    }

    // ------------------------------------------------------------- getTaskList

    @Test
    @DisplayName("getTaskList：批量查询并复制属性")
    void getTaskList() {
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        List<WorkflowTaskDto> out = workflowService.getTaskList(List.of("p1"));

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getId()).isEqualTo("t1");
    }

    // -------------------------------------------------------- updateTaskAssignee

    @Test
    @DisplayName("updateTaskAssignee：任务不存在")
    void updateTaskAssigneeNotFound() {
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        UpdateTaskAssigneeInput input = new UpdateTaskAssigneeInput();
        input.setTaskId("t1");

        assertThatThrownBy(() -> workflowService.updateTaskAssignee(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");
    }

    @Test
    @DisplayName("updateTaskAssignee：保存任务并推送代理人变更")
    void updateTaskAssigneeOk() {
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getAssignee()).thenReturn("newUser");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        TaskFormData formDataStub = taskFormData();
        when(formService.getTaskFormData("t1")).thenReturn(formDataStub);
        when(taskService.getIdentityLinksForTask("t1")).thenReturn(List.of());
        when(runtimeService.getVariables("p1")).thenReturn(todoVariables());

        UpdateTaskAssigneeInput input = new UpdateTaskAssigneeInput();
        input.setTaskId("t1");
        input.setAssignee("newUser");

        workflowService.updateTaskAssignee(input);

        verify(task).setAssignee("newUser");
        verify(taskService).saveTask(task);
        verify(todoRpcService).syncActivitiTaskAssigneeChanged(eq("p1"), anyList());
    }

    // -------------------------------------------------- completeTask(input, auth)

    @Test
    @DisplayName("completeTask(input)：流程不存在")
    void completeTaskInputProcessNotFound() {
        ProcessInstanceQuery query = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        CompleteTaskInputDto input = new CompleteTaskInputDto();
        input.setProcessInstanceId("p1");

        assertThatThrownBy(() -> workflowService.completeTask(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无法找到流程");
    }

    @Test
    @DisplayName("completeTask(input)：写入可选流程变量并同步待办")
    void completeTaskInputOk() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Task task = Mockito.mock(Task.class);
        // 被完成的任务只需提供代理人（用于权限判断），其余属性来自 taskId 入参
        when(task.getAssignee()).thenReturn("");

        Task nextTask = Mockito.mock(Task.class);
        when(nextTask.getId()).thenReturn("t2");
        when(nextTask.getAssignee()).thenReturn("1001");
        when(nextTask.getName()).thenReturn("下一节点");
        when(nextTask.getFormKey()).thenReturn("{}");

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of(nextTask));

        TaskFormData formDataStub = taskFormData();
        when(formService.getTaskFormData(any())).thenReturn(formDataStub);
        when(taskService.getIdentityLinksForTask(any())).thenReturn(List.of());

        when(runtimeService.getVariables("p1")).thenAnswer(invocation -> {
            Map<String, Object> variables = new HashMap<>();
            variables.put(FlowableConstants.TITLE, "标题");
            variables.put(FlowableConstants.TYPE_NAME, "类型");
            variables.put(FlowableConstants.BUSINESS_TYPE, "业务");
            variables.put(FlowableConstants.FILTER_STATION, false);
            variables.put(FlowableConstants.STARTER, "发起人");
            return variables;
        });

        CompleteTaskInputDto input = new CompleteTaskInputDto();
        input.setProcessInstanceId("p1");
        input.setTaskId("t1");
        input.setTitle("标题");
        input.setTypeName("类型");
        input.setType("业务");
        input.setFilterStation(true);
        input.setStarter("发起人");

        List<WorkflowTaskDto> out = workflowService.completeTask(input);

        assertThat(out).hasSize(1);
        // 变量经补全后非空，transientVariables 未设置保持 null
        verify(taskService).complete(eq("t1"), anyMap(), nullable(Map.class));
        verify(todoRpcService).syncActivitiTask(eq("p1"), anyList());
    }

    @Test
    @DisplayName("completeTask(input)：variables 为 null 时新建，可选字段均不写入")
    void completeTaskInputWithoutOptionalFields() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Task task = Mockito.mock(Task.class);
        // 任务 id 由入参传入，命令内部只读取代理人判断是否需要鉴权
        when(task.getAssignee()).thenReturn("");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of());

        CompleteTaskInputDto input = new CompleteTaskInputDto();
        input.setProcessInstanceId("p1");
        input.setTaskId("t1");

        List<WorkflowTaskDto> out = workflowService.completeTask(input);

        assertThat(out).isEmpty();
        // 未设置任何可选字段，变量被初始化为空 map，transientVariables 为 null
        verify(taskService).complete(eq("t1"), anyMap(), nullable(Map.class));
    }

    // ---------------------------------------------------------- updateFlowVariables

    @Test
    @DisplayName("updateFlowVariables：variables 为空")
    void updateFlowVariablesEmpty() {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setVariables(new HashMap<>());

        assertThatThrownBy(() -> workflowService.updateFlowVariables(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("variables不能为空");
    }

    @Test
    @DisplayName("updateFlowVariables：variables 为 null")
    void updateFlowVariablesNull() {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();

        assertThatThrownBy(() -> workflowService.updateFlowVariables(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("variables不能为空");
    }

    @Test
    @DisplayName("updateFlowVariables：流程不存在")
    void updateFlowVariablesProcessNotFound() {
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("p1");
        input.setVariables(Map.of("manager", "8888"));

        assertThatThrownBy(() -> workflowService.updateFlowVariables(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程不存在或已结束");
    }

    @Test
    @DisplayName("updateFlowVariables：命中表达式时重派代理人并同步待办")
    void updateFlowVariablesReassign() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Map<String, Object> variables = todoVariables();
        variables.put("manager", "8888");
        when(runtimeService.getVariables("p1")).thenReturn(variables);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(singleUserTaskModel("ut1", "${manager}"));

        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getTaskDefinitionKey()).thenReturn("ut1");
        when(task.getProcessInstanceId()).thenReturn("p1");
        when(task.getAssignee()).thenReturn("8888");
        when(task.getName()).thenReturn("节点");
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        TaskFormData formDataStub = taskFormData();
        when(formService.getTaskFormData(any())).thenReturn(formDataStub);
        when(taskService.getIdentityLinksForTask(any())).thenReturn(List.of());

        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("p1");
        input.setVariables(variables);

        workflowService.updateFlowVariables(input);

        verify(runtimeService).setVariables("p1", variables);
        verify(taskService).setAssignee("t1", "8888");
        verify(todoRpcService).syncActivitiTaskAssigneeChanged(eq("p1"), anyList());
    }

    @Test
    @DisplayName("updateFlowVariables：表达式值解析为空时抛异常")
    void updateFlowVariablesBlankAssignee() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Map<String, Object> variables = Map.of("manager", "");
        when(runtimeService.getVariables("p1")).thenReturn(variables);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(singleUserTaskModel("ut1", "${manager}"));

        Task task = Mockito.mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn("ut1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("p1");
        input.setVariables(variables);

        assertThatThrownBy(() -> workflowService.updateFlowVariables(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("代理人不能为空");
    }

    @Test
    @DisplayName("updateFlowVariables：未命中表达式时不推送待办")
    void updateFlowVariablesNotMatched() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Map<String, Object> variables = Map.of("manager", "8888");
        when(runtimeService.getVariables("p1")).thenReturn(variables);

        when(repositoryService.getBpmnModel("pd1")).thenReturn(singleUserTaskModel("ut1", "${other}"));

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

    @Test
    @DisplayName("updateFlowVariables：节点不是 UserTask 时跳过")
    void updateFlowVariablesNonUserTask() {
        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        Map<String, Object> variables = Map.of("manager", "8888");
        when(runtimeService.getVariables("p1")).thenReturn(variables);

        BpmnModel bpmnModel = new BpmnModel();
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel);

        Task task = Mockito.mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn("missing");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("p1");
        input.setVariables(variables);

        workflowService.updateFlowVariables(input);

        verify(todoRpcService, never()).syncActivitiTaskAssigneeChanged(anyString(), anyList());
    }

    // ------------------------------------------------------ startProcessAndCompleteFirst

    @Test
    @DisplayName("startProcessAndCompleteFirst：先启动再完成首节点")
    void startProcessAndCompleteFirst() {
        StartProcessInputDto input = new StartProcessInputDto();
        input.setProcessDefinitionKey("leave");
        input.setStarter("张三");
        input.setTitle("标题");

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        when(runtimeService.startProcessInstanceByKeyAndTenantId(anyString(), any(), anyMap(), anyString()))
                .thenReturn(processInstance);

        // 完成首节点
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);
        when(taskQuery.list()).thenReturn(List.of(task), List.of());

        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        StartProcessOutDto out = workflowService.startProcessAndCompleteFirst(input);

        assertThat(out.getProcessInstanceId()).isEqualTo("p1");
    }

    // ------------------------------------------------------------- getTaskHistory

    @Test
    @DisplayName("getTaskHistory：复制任务历史并回填审批意见与变量")
    void getTaskHistory() {
        HistoricTaskInstance historicTask = Mockito.mock(HistoricTaskInstance.class);
        when(historicTask.getId()).thenReturn("t1");
        when(historicTask.getAssignee()).thenReturn("1001");
        when(historicTask.getClaimTime()).thenReturn(new Date());
        when(historicTask.getEndTime()).thenReturn(new Date());

        HistoricTaskInstanceQuery query = self(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
        when(query.list()).thenReturn(List.of(historicTask));

        Comment comment = Mockito.mock(Comment.class);
        when(comment.getFullMessage()).thenReturn("同意");
        when(taskService.getTaskComments("t1")).thenReturn(List.of(comment));

        HistoricVariableInstance variable = Mockito.mock(HistoricVariableInstance.class);
        when(variable.getVariableName()).thenReturn("pass");
        when(variable.getValue()).thenReturn(true);
        HistoricVariableInstanceQuery variableQuery = self(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
        when(variableQuery.list()).thenReturn(List.of(variable));

        when(userNameUtils.mapList(anyList(), eq(WorkflowTaskHistoryDto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<WorkflowTaskHistoryDto> out = workflowService.getTaskHistory("p1");

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getAssigneeId()).isEqualTo(1001L);
        assertThat(out.getFirst().getComment()).isEqualTo("同意");
        assertThat(out.getFirst().getVariables()).containsEntry("pass", true);
        assertThat(out.getFirst().getBeginTime()).isNotNull();
        assertThat(out.getFirst().getStopTime()).isNotNull();
    }

    @Test
    @DisplayName("getTaskHistory：角色代理人 + 无评论 + 无变量")
    void getTaskHistoryRoleAssignee() {
        HistoricTaskInstance historicTask = Mockito.mock(HistoricTaskInstance.class);
        when(historicTask.getId()).thenReturn("t1");
        when(historicTask.getAssignee()).thenReturn("roleA");

        HistoricTaskInstanceQuery query = self(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
        when(query.list()).thenReturn(List.of(historicTask));

        when(taskService.getTaskComments("t1")).thenReturn(null);
        HistoricVariableInstanceQuery variableQuery = self(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
        when(variableQuery.list()).thenReturn(List.of());

        when(userNameUtils.mapList(anyList(), eq(WorkflowTaskHistoryDto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<WorkflowTaskHistoryDto> out = workflowService.getTaskHistory("p1");

        assertThat(out.getFirst().getAssigneeId()).isNull();
        assertThat(out.getFirst().getComment()).isNull();
        assertThat(out.getFirst().getVariables()).isEmpty();
    }

    @Test
    @DisplayName("getTaskHistories：批量查询任务历史")
    void getTaskHistories() {
        HistoricTaskInstance historicTask = Mockito.mock(HistoricTaskInstance.class);
        when(historicTask.getId()).thenReturn("t1");
        when(historicTask.getAssignee()).thenReturn("roleA");

        HistoricTaskInstanceQuery query = self(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(query);
        when(query.list()).thenReturn(List.of(historicTask));
        when(taskService.getTaskComments("t1")).thenReturn(List.of());
        HistoricVariableInstanceQuery variableQuery = self(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(variableQuery);
        when(variableQuery.list()).thenReturn(List.of());
        when(userNameUtils.mapList(anyList(), eq(WorkflowTaskHistoryDto.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(workflowService.getTaskHistories(List.of("p1"))).hasSize(1);
    }

    // ------------------------------------------- getCurrTasksWithAssigneeInfos

    @Test
    @DisplayName("getCurrTasksWithAssigneeInfos：无任务返回空")
    void getCurrTasksWithAssigneeInfosEmpty() {
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of());

        assertThat(workflowService.getCurrTasksWithAssigneeInfos("p1")).isEmpty();
    }

    @Test
    @DisplayName("getCurrTasksWithAssigneeInfos：区分用户id与角色名")
    void getCurrTasksWithAssigneeInfos() {
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn("1001,roleA");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        TaskFormData formDataStub = taskFormData();
        when(formService.getTaskFormData("t1")).thenReturn(formDataStub);
        when(taskService.getIdentityLinksForTask("t1")).thenReturn(List.of());

        UserNameDto name = new UserNameDto();
        name.setProcessInstanceId("t1");
        name.setAssigneeName("张三");
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of(name));

        List<WorkflowTaskDetailDto> out = workflowService.getCurrTasksWithAssigneeInfos("p1");

        assertThat(out).hasSize(1);
        assertThat(out.getFirst().getRoleNames()).isEqualTo("roleA");
        assertThat(out.getFirst().getUserNames()).isEqualTo("张三");
    }

    @Test
    @DisplayName("getCurrTasksWithAssigneeInfos：无代理人时角色名为空")
    void getCurrTasksWithAssigneeInfosBlankAssignee() {
        Task task = Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(task.getAssignee()).thenReturn("  ");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(task));

        TaskFormData formDataStub = taskFormData();
        when(formService.getTaskFormData("t1")).thenReturn(formDataStub);
        when(taskService.getIdentityLinksForTask("t1")).thenReturn(List.of());
        when(userNameUtils.mapList(anyList(), eq(UserNameDto.class))).thenReturn(List.of());

        List<WorkflowTaskDetailDto> out = workflowService.getCurrTasksWithAssigneeInfos("p1");

        assertThat(out.getFirst().getRoleNames()).isEmpty();
        assertThat(out.getFirst().getUserNames()).isNull();
    }

    // ------------------------------------------------------------- recall

    @Test
    @DisplayName("checkProcessCanRecallPre：历史任务为空时不可撤回")
    void checkProcessCanRecallPre() {
        HistoricTaskInstanceQuery historicQuery = self(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.list()).thenReturn(List.of());

        ProcessInstance processInstance = Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = self(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        ProcessDefinitionEntity entity = Mockito.mock(ProcessDefinitionEntity.class);
        when(entity.getId()).thenReturn("pd1");
        when(repositoryService.getProcessDefinition("pd1")).thenReturn(entity);

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of());

        assertThat(workflowService.checkProcessCanRecallPre("p1")).isFalse();
    }

    @Test
    @DisplayName("recallPre：上一节点不是自己审批时抛异常")
    void recallPreNotAllowed() {
        HistoricTaskInstance lastTask = Mockito.mock(HistoricTaskInstance.class);
        when(lastTask.getProcessDefinitionId()).thenReturn("pd1");
        when(lastTask.getAssignee()).thenReturn("9999");

        HistoricTaskInstanceQuery historicQuery = self(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.list()).thenReturn(List.of(lastTask));

        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);

        assertThatThrownBy(() -> workflowService.recallPre("p1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前节点无法撤回");
    }

    @Test
    @DisplayName("recallPre：校验通过时跳转节点并清理历史任务")
    void recallPreAllowed() {
        HistoricTaskInstance lastTask = Mockito.mock(HistoricTaskInstance.class);
        when(lastTask.getId()).thenReturn("t0");
        when(lastTask.getProcessDefinitionId()).thenReturn("pd1");
        when(lastTask.getTaskDefinitionKey()).thenReturn("ut1");
        when(lastTask.getAssignee()).thenReturn(String.valueOf(USER_ID));

        HistoricTaskInstanceQuery historicQuery = self(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(historicQuery);
        when(historicQuery.list()).thenReturn(List.of(lastTask));

        Task currentTask = Mockito.mock(Task.class);
        when(currentTask.getTaskDefinitionKey()).thenReturn("ut2");
        when(currentTask.getFormKey()).thenReturn("{\"activiti\":{\"callBackPre\":true}}");
        TaskQuery taskQuery = self(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.list()).thenReturn(List.of(currentTask));

        when(repositoryService.getBpmnModel("pd1")).thenReturn(singleUserTaskModel("ut1", null));

        ChangeActivityStateBuilder builder = self(ChangeActivityStateBuilder.class);
        when(runtimeService.createChangeActivityStateBuilder()).thenReturn(builder);

        workflowService.recallPre("p1");

        verify(builder).moveActivityIdTo("ut2", "ut1");
        verify(builder).changeState();
        verify(historyService).deleteHistoricTaskInstance("t0");
    }
}
