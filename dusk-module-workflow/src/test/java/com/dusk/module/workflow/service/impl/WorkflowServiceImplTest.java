tpackage com.dusk.module.workflow.service.impl;

import com.dusk.common.core.auth.authentication.LoginUserIdContextHolder;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.core.model.UserContext;
import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.common.core.utils.SecurityUtils;
import com.dusk.common.rpc.auth.UserNameUtils;
import com.dusk.common.rpc.auth.dto.UserFullListDto;
import com.dusk.common.rpc.auth.dto.UserRoleDto;
import com.dusk.common.rpc.auth.service.ITodoRpcService;
import com.dusk.common.rpc.auth.service.IUserRpcService;
import com.dusk.module.workflow.constant.ActivitiConstants;
import com.dusk.module.workflow.dto.RelatedNodeInfo;
import com.dusk.module.workflow.dto.TaskFormKey;
import com.dusk.module.workflow.event.WorkflowEventPublisher;
import com.dusk.module.workflow.service.WorkflowProcessorRegistry;
import com.dusk.workflow.dto.*;
import com.dusk.workflow.enums.WorkflowEventType;
import com.dusk.workflow.service.IWorkflowApprovalProcessor;
import com.dusk.workflow.service.IWorkflowRecallHandler;
import com.dusk.workflow.service.IWorkflowSubmitProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.engine.*;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.form.TaskFormData;
import org.activiti.engine.history.*;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.activiti.engine.impl.form.DefaultStartFormHandler;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.pvm.process.TransitionImpl;
import org.activiti.engine.impl.task.TaskDefinition;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.repository.ProcessDefinitionQuery;
import org.activiti.engine.runtime.ExecutionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.runtime.ProcessInstanceQuery;
import org.activiti.engine.task.*;
import org.activiti.image.ProcessDiagramGenerator;
import org.activiti.engine.delegate.Expression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WorkflowServiceImpl 核心单元测试
 * 最小依赖：纯Mockito，不启动Spring上下文
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkflowServiceImplTest {

    @Mock private RuntimeService runtimeService;
    @Mock private TaskService taskService;
    @Mock private RepositoryService repositoryService;
    @Mock private ProcessEngineConfiguration processEngineConfiguration;
    @Mock private HistoryService historyService;
    @Mock private FormService formService;
    @Mock private IUserRpcService userRpcService;
    @Mock private SecurityUtils securityUtils;
    @Mock private UserNameUtils userNameUtils;
    @Mock private ITodoRpcService todoRpcService;
    @Mock private ObjectMapper objectMapper;
    @Mock private WorkflowEventPublisher eventPublisher;
    @Mock private WorkflowProcessorRegistry processorRegistry;
    @Mock private WorkflowCarbonCopyService carbonCopyService;

    @InjectMocks
    private WorkflowServiceImpl workflowService;

    private MockedStatic<LoginUserIdContextHolder> loginMock;
    private MockedStatic<TenantContextHolder> tenantMock;

    @BeforeEach
    void setUp() {
        loginMock = mockStatic(LoginUserIdContextHolder.class);
        tenantMock = mockStatic(TenantContextHolder.class);
        tenantMock.when(TenantContextHolder::getTenantId).thenReturn(1L);
        loginMock.when(LoginUserIdContextHolder::getUserId).thenReturn(100L);
    }

    @AfterEach
    void tearDown() {
        loginMock.close();
        tenantMock.close();
    }

    // ==================== delProcess ====================

    @Test
    void delProcess_processExists_shouldDelete() {
        ProcessInstanceQuery query = mockProcessInstanceQuery("proc1", mock(ProcessInstance.class));

        boolean result = workflowService.delProcess("proc1", "test reason");

        assertTrue(result);
        verify(runtimeService).deleteProcessInstance("proc1", "test reason");
        verify(todoRpcService).syncActivitiTask(eq("proc1"), anyList());
    }

    @Test
    void delProcess_processNull_shouldNotDelete() {
        mockProcessInstanceQuery("proc1", null);

        boolean result = workflowService.delProcess("proc1", "reason");

        assertTrue(result);
        verify(runtimeService, never()).deleteProcessInstance(anyString(), anyString());
    }

    // ==================== checkProcessEnd ====================

    @Test
    void checkProcessEnd_processNull_shouldReturnTrue() {
        mockProcessInstanceQuery("proc1", null);
        assertTrue(workflowService.checkProcessEnd("proc1"));
    }

    @Test
    void checkProcessEnd_processExists_shouldReturnFalse() {
        mockProcessInstanceQuery("proc1", mock(ProcessInstance.class));
        assertFalse(workflowService.checkProcessEnd("proc1"));
    }

    // ==================== getTask ====================

    @Test
    void getTask_taskExists_shouldReturnDto() {
        Task task = mockTask("task1", "proc1", "审批", "100", "taskDef1");
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(task);

        // mock formService for task2TaskDto
        mockFormService("task1");

        WorkflowTaskDto result = workflowService.getTask("task1");
        assertNotNull(result);
    }

    @Test
    void getTask_taskNull_shouldThrowException() {
        TaskQuery taskQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.taskId("task1")).thenReturn(taskQuery);
        when(taskQuery.singleResult()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.getTask("task1"));
        assertTrue(ex.getMessage().contains("审批任务不存在"));
    }

    // ==================== getProcessDefinitionFirstFormKey ====================

    @Test
    void getProcessDefinitionFirstFormKey_pdNull_shouldThrow() {
        ProcessDefinitionQuery pdQuery = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.processDefinitionTenantId(anyString())).thenReturn(pdQuery);
        when(pdQuery.processDefinitionKey("myKey")).thenReturn(pdQuery);
        when(pdQuery.latestVersion()).thenReturn(pdQuery);
        when(pdQuery.singleResult()).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.getProcessDefinitionFirstFormKey("myKey"));
        assertTrue(ex.getMessage().contains("不存在名为myKey的流程"));
    }

    @Test
    void getProcessDefinitionFirstFormKey_processDefEntityNull_shouldThrow() {
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");

        ProcessDefinitionQuery pdQuery = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.processDefinitionTenantId(anyString())).thenReturn(pdQuery);
        when(pdQuery.processDefinitionKey("myKey")).thenReturn(pdQuery);
        when(pdQuery.latestVersion()).thenReturn(pdQuery);
        when(pdQuery.singleResult()).thenReturn(pd);

        // Cast repositoryService to RepositoryServiceImpl for getDeployedProcessDefinition
        // Since repositoryService is a mock of RepositoryService interface,
        // we need a special approach - mock it as RepositoryServiceImpl
        // This requires using spy or a different approach
        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        when(repoImpl.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(repoImpl.getDeployedProcessDefinition("pd1")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> workflowService.getProcessDefinitionFirstFormKey("myKey"));
        assertTrue(ex.getMessage().contains("流程尚未发布"));

        // restore
        setField(workflowService, "repositoryService", repositoryService);
    }

    @Test
    void getProcessDefinitionFirstFormKey_hasStartFormKey_shouldReturnFormKey() {
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");

        ProcessDefinitionQuery pdQuery = mock(ProcessDefinitionQuery.class);

        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        when(repoImpl.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.processDefinitionTenantId(anyString())).thenReturn(pdQuery);
        when(pdQuery.processDefinitionKey("myKey")).thenReturn(pdQuery);
        when(pdQuery.latestVersion()).thenReturn(pdQuery);
        when(pdQuery.singleResult()).thenReturn(pd);

        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repoImpl.getDeployedProcessDefinition("pd1")).thenReturn(pdEntity);
        when(pdEntity.getHasStartFormKey()).thenReturn(true);

        DefaultStartFormHandler handler = mock(DefaultStartFormHandler.class);
        when(pdEntity.getStartFormHandler()).thenReturn(handler);
        Expression formKeyExpr = mock(Expression.class);
        when(handler.getFormKey()).thenReturn(formKeyExpr);
        when(formKeyExpr.getExpressionText()).thenReturn("{\"form\":\"config\"}");

        String result = workflowService.getProcessDefinitionFirstFormKey("myKey");
        assertEquals("{\"form\":\"config\"}", result);

        setField(workflowService, "repositoryService", repositoryService);
    }

    @Test
    void getProcessDefinitionFirstFormKey_noStartFormKey_shouldReturnNull() {
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");

        ProcessDefinitionQuery pdQuery = mock(ProcessDefinitionQuery.class);

        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        when(repoImpl.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.processDefinitionTenantId(anyString())).thenReturn(pdQuery);
        when(pdQuery.processDefinitionKey("myKey")).thenReturn(pdQuery);
        when(pdQuery.latestVersion()).thenReturn(pdQuery);
        when(pdQuery.singleResult()).thenReturn(pd);

        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repoImpl.getDeployedProcessDefinition("pd1")).thenReturn(pdEntity);
        when(pdEntity.getHasStartFormKey()).thenReturn(false);

        assertNull(workflowService.getProcessDefinitionFirstFormKey("myKey"));

        setField(workflowService, "repositoryService", repositoryService);
    }

    // ==================== completeTaskByProcessId ====================

    @Test
    void completeTaskByProcessId_processNull_shouldThrow() {
        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("proc1");
        mockProcessInstanceQuery("proc1", null);

        assertThrows(BusinessException.class,
                () -> workflowService.completeTaskByProcessId(input));
    }

    @Test
    void completeTaskByProcessId_multipleTask_shouldThrow() {
        CompleteTaskByProcessIdInputDto input = new CompleteTaskByProcessIdInputDto();
        input.setProcessInstanceId("proc1");

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("proc1");
        mockProcessInstanceQuery("proc1", pi);

        // getTaskIdByProcessInstanceId => multiple tasks
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId("proc1")).thenReturn(tq);
        Task task1 = mockTask("t1", "proc1", "n1", "100", "dk1");
        Task task2 = mockTask("t2", "proc1", "n2", "200", "dk2");
        when(tq.list()).thenReturn(Arrays.asList(task1, task2));

        assertThrows(RuntimeException.class,
                () -> workflowService.completeTaskByProcessId(input));
    }

    // ==================== completeTask(taskId, dto) ====================

    @Test
    void completeTask_withComment_shouldAddComment() {
        Task task = mockTask("task1", "proc1", "审批", "100", "taskDef1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("task1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        // hasTaskPermission需要的mock
        mockHasTaskPermission(task, true);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();
        dto.setComment("同意");
        dto.setVariables(Map.of("pass", true));
        dto.setLocalVariables(Map.of("localKey", "localVal"));
        dto.setTransientVariables(Map.of("transKey", "transVal"));

        boolean result = workflowService.completeTask("task1", dto);

        assertTrue(result);
        verify(taskService).addComment("task1", "proc1", "同意");
        verify(taskService).setVariables("task1", Map.of("pass", true));
        verify(taskService).setVariablesLocal("task1", Map.of("localKey", "localVal"));
        verify(taskService).complete("task1", Map.of("pass", true), Map.of("transKey", "transVal"));
    }

    @Test
    void completeTask_blankComment_shouldNotAddComment() {
        Task task = mockTask("task1", "proc1", "审批", "", "taskDef1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("task1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();
        dto.setComment("");

        boolean result = workflowService.completeTask("task1", dto);

        assertTrue(result);
        verify(taskService, never()).addComment(anyString(), anyString(), anyString());
    }

    @Test
    void completeTask_noPermission_shouldThrow() {
        Task task = mockTask("task1", "proc1", "审批", "999", "taskDef1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("task1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        // mock no permission
        mockHasTaskPermission(task, false);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();

        assertThrows(BusinessException.class,
                () -> workflowService.completeTask("task1", dto));
    }

    @Test
    void completeTask_blankAssignee_shouldSkipAuth() {
        Task task = mockTask("task1", "proc1", "审批", "", "taskDef1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("task1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();

        boolean result = workflowService.completeTask("task1", dto);
        assertTrue(result);
    }

    @Test
    void completeTask_nullVariables_shouldNotSetVariables() {
        Task task = mockTask("task1", "proc1", "审批", "", "taskDef1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("task1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();
        dto.setVariables(null);
        dto.setLocalVariables(null);

        workflowService.completeTask("task1", dto);
        verify(taskService, never()).setVariables(anyString(), anyMap());
        verify(taskService, never()).setVariablesLocal(anyString(), anyMap());
    }

    @Test
    void completeTask_emptyVariables_shouldNotSetVariables() {
        Task task = mockTask("task1", "proc1", "审批", "", "taskDef1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("task1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        WorkflowCompleteTaskDto dto = new WorkflowCompleteTaskDto();
        dto.setVariables(Collections.emptyMap());
        dto.setLocalVariables(Collections.emptyMap());

        workflowService.completeTask("task1", dto);
        verify(taskService, never()).setVariables(anyString(), anyMap());
        verify(taskService, never()).setVariablesLocal(anyString(), anyMap());
    }

    // ==================== getTaskList ====================

    @Test
    void getTaskList_shouldReturnMappedDtos() {
        Task task = mockTask("t1", "proc1", "审批", "100", "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        List<WorkflowTaskDto> result = workflowService.getTaskList(List.of("proc1"));
        assertEquals(1, result.size());
    }

    // ==================== updateTaskAssignee ====================

    @Test
    void updateTaskAssignee_taskNull_shouldThrow() {
        UpdateTaskAssigneeInput input = new UpdateTaskAssigneeInput();
        input.setTaskId("t1");

        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("t1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> workflowService.updateTaskAssignee(input));
    }

    @Test
    void updateTaskAssignee_taskExists_shouldUpdateAndSync() throws Exception {
        UpdateTaskAssigneeInput input = new UpdateTaskAssigneeInput();
        input.setTaskId("t1");
        input.setAssignee("200");

        Task task = mockTask("t1", "proc1", "审批", "100", "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("t1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("proc1");
        when(pi.getBusinessKey()).thenReturn("bk1");
        mockProcessInstanceQuery("proc1", pi);

        mockFormService("t1");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(runtimeService.getVariables("proc1")).thenReturn(createStdVariables());

        workflowService.updateTaskAssignee(input);

        verify(task).setAssignee("200");
        verify(taskService).saveTask(task);
    }

    // ==================== updateFlowVariables ====================

    @Test
    void updateFlowVariables_nullVariables_shouldThrow() {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setVariables(null);

        assertThrows(BusinessException.class,
                () -> workflowService.updateFlowVariables(input));
    }

    @Test
    void updateFlowVariables_emptyVariables_shouldThrow() {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setVariables(Collections.emptyMap());

        assertThrows(BusinessException.class,
                () -> workflowService.updateFlowVariables(input));
    }

    @Test
    void updateFlowVariables_processNull_shouldThrow() {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("proc1");
        input.setVariables(Map.of("key", "val"));

        mockProcessInstanceQuery("proc1", null);

        assertThrows(BusinessException.class,
                () -> workflowService.updateFlowVariables(input));
    }

    @Test
    void updateFlowVariables_matchingAssigneeExpression_shouldUpdateAssignee() throws Exception {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("proc1");
        input.setVariables(Map.of("approver", "300"));

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("proc1");
        when(pi.getProcessDefinitionId()).thenReturn("defId1");
        when(pi.getBusinessKey()).thenReturn("bk1");
        mockProcessInstanceQuery("proc1", pi);

        Task task = mockTask("t1", "proc1", "审批", "100", "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId("proc1")).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("proc1")).thenReturn(new HashMap<>(Map.of("approver", "300")));

        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repoImpl.getDeployedProcessDefinition("defId1")).thenReturn(pdEntity);

        TaskDefinition taskDef = mock(TaskDefinition.class);
        Expression assigneeExpr = mock(Expression.class);
        when(assigneeExpr.getExpressionText()).thenReturn("${approver}");
        when(taskDef.getAssigneeExpression()).thenReturn(assigneeExpr);
        when(pdEntity.getTaskDefinitions()).thenReturn(Map.of("dk1", taskDef));

        mockFormService("t1");
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        Map<String, Object> stdVars = createStdVariables();
        stdVars.put("approver", "300");
        when(runtimeService.getVariables("proc1")).thenReturn(stdVars);

        workflowService.updateFlowVariables(input);

        verify(task).setAssignee("300");
        verify(taskService).saveTask(task);

        setField(workflowService, "repositoryService", repositoryService);
    }

    @Test
    void updateFlowVariables_blankAssignee_shouldThrow() throws Exception {
        UpdateFlowVariablesInput input = new UpdateFlowVariablesInput();
        input.setProcessInstanceId("proc1");
        input.setVariables(Map.of("approver", ""));

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("proc1");
        when(pi.getProcessDefinitionId()).thenReturn("defId1");
        mockProcessInstanceQuery("proc1", pi);

        Task task = mockTask("t1", "proc1", "审批", "100", "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId("proc1")).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        // 先返回一个包含空approver的变量map
        when(runtimeService.getVariables("proc1")).thenReturn(new HashMap<>(Map.of("approver", "")));

        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repoImpl.getDeployedProcessDefinition("defId1")).thenReturn(pdEntity);

        TaskDefinition taskDef = mock(TaskDefinition.class);
        Expression assigneeExpr = mock(Expression.class);
        when(assigneeExpr.getExpressionText()).thenReturn("${approver}");
        when(taskDef.getAssigneeExpression()).thenReturn(assigneeExpr);
        when(pdEntity.getTaskDefinitions()).thenReturn(Map.of("dk1", taskDef));

        assertThrows(BusinessException.class,
                () -> workflowService.updateFlowVariables(input));

        setField(workflowService, "repositoryService", repositoryService);
    }

    // ==================== getProcessDescription ====================

    @Test
    void getProcessDescription_nullProcessIds_shouldReturnEmpty() {
        List<ProcessDesOutPutDto> result = workflowService.getProcessDescription(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void getProcessDescription_emptyProcessIds_shouldReturnEmpty() {
        List<ProcessDesOutPutDto> result = workflowService.getProcessDescription(Collections.emptyList());
        assertTrue(result.isEmpty());
    }

    @Test
    void getProcessDescription_finishedProcess_shouldSetFinished() {
        UserContext uc = mock(UserContext.class);
        when(uc.getId()).thenReturn(100L);
        when(securityUtils.getCurrentUser()).thenReturn(uc);
        when(userRpcService.getUserFullById(100L)).thenReturn(createUserFullInfo(100L));

        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(Collections.emptyList()); // no active tasks

        when(userNameUtils.mapList(anyList(), any())).thenReturn(Collections.emptyList());

        List<ProcessDesOutPutDto> result = workflowService.getProcessDescription(List.of("proc1"));

        assertEquals(1, result.size());
        assertTrue(result.getFirst().isFinished());
        assertEquals("流程已结束", result.getFirst().getDescription());
    }

    @Test
    void getProcessDescription_activeProcess_withNumericAssignee() {
        UserContext uc = mock(UserContext.class);
        when(uc.getId()).thenReturn(100L);
        when(securityUtils.getCurrentUser()).thenReturn(uc);
        when(userRpcService.getUserFullById(100L)).thenReturn(createUserFullInfo(100L));

        Task task = mockTask("t1", "proc1", "审批节点", "100", "dk1");
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("proc1")).thenReturn(Map.of("key", "val"));

        com.dusk.module.workflow.dto.UserNameDto nameDto = new com.dusk.module.workflow.dto.UserNameDto();
        nameDto.setProcessInstanceId("proc1");
        nameDto.setAssigneeId(100L);
        nameDto.setAssigneeName("张三");
        when(userNameUtils.mapList(anyList(), any())).thenReturn(List.of(nameDto));

        List<ProcessDesOutPutDto> result = workflowService.getProcessDescription(List.of("proc1"));

        assertEquals(1, result.size());
        assertFalse(result.getFirst().isFinished());
        assertTrue(result.getFirst().getDescription().contains("张三"));
    }

    @Test
    void getProcessDescription_activeProcess_withNonNumericAssignee() {
        UserContext uc = mock(UserContext.class);
        when(uc.getId()).thenReturn(100L);
        when(securityUtils.getCurrentUser()).thenReturn(null);

        Task task = mockTask("t1", "proc1", "审批节点", "管理员", "dk1");
        when(task.getFormKey()).thenReturn("{}");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        when(runtimeService.getVariables("proc1")).thenReturn(Map.of("key", "val"));
        when(userNameUtils.mapList(anyList(), any())).thenReturn(Collections.emptyList());

        List<ProcessDesOutPutDto> result = workflowService.getProcessDescription(List.of("proc1"));

        assertEquals(1, result.size());
        assertFalse(result.getFirst().isFinished());
        // 没有userName时description = taskName
        assertEquals("审批节点", result.getFirst().getDescription());
    }

    // ==================== getTasksByProcess ====================

    @Test
    void getTasksByProcess_withInitAssignee_directLeader_shouldResolveSuperior() throws Exception {
        Task task = mockTask("t1", "proc1", "审批", ActivitiConstants.PLACE_HOLDER_DIRECT_LEADER, "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        when(userRpcService.getSuperiorId(100L)).thenReturn(200L);
        mockFormService("t1");

        List<WorkflowTaskDto> result = workflowService.getTasksByProcessAndInitAssignee(List.of("proc1"), false);

        verify(task).setAssignee("200");
        verify(taskService).saveTask(task);
    }

    @Test
    void getTasksByProcess_withInitAssignee_noSuperior_shouldThrow() {
        Task task = mockTask("t1", "proc1", "审批", ActivitiConstants.PLACE_HOLDER_DIRECT_LEADER, "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(List.of(task));

        when(userRpcService.getSuperiorId(100L)).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> workflowService.getTasksByProcessAndInitAssignee(List.of("proc1"), false));
    }

    // ==================== getRelatedNode ====================

    @Test
    void getRelatedNode_blankTaskIdAndBlankProcessKey_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> workflowService.getRelatedNode("", "", false, null));
    }

    @Test
    void getRelatedNode_blankTaskId_withProcessKey_shouldReturnStartEvent() {
        // mock getProcessDefinitionFirstFormKey
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        ProcessDefinitionQuery pdQuery = mock(ProcessDefinitionQuery.class);

        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        when(repoImpl.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.processDefinitionTenantId(anyString())).thenReturn(pdQuery);
        when(pdQuery.processDefinitionKey("myKey")).thenReturn(pdQuery);
        when(pdQuery.latestVersion()).thenReturn(pdQuery);
        when(pdQuery.singleResult()).thenReturn(pd);

        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repoImpl.getDeployedProcessDefinition("pd1")).thenReturn(pdEntity);
        when(pdEntity.getHasStartFormKey()).thenReturn(false);

        List<RelatedNodeInfo> result = workflowService.getRelatedNode("", "myKey", false, null);

        assertEquals(1, result.size());
        assertEquals(ActivitiConstants.NODE_TYPE_START_EVENT, result.getFirst().getNodeType());

        setField(workflowService, "repositoryService", repositoryService);
    }

    @Test
    void getRelatedNode_withTaskId_processNull_shouldReturnEmpty() {
        Task task = mockTask("t1", "proc1", "审批", "100", "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("t1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        mockProcessInstanceQuery("proc1", null);

        List<RelatedNodeInfo> result = workflowService.getRelatedNode("t1", null, false, null);
        assertTrue(result.isEmpty());
    }

    // ==================== genericSubmit ====================

    @Test
    void genericSubmit_withProcessor_completeFirstFalse_withCc() throws Exception {
        GenericSubmitInput input = new GenericSubmitInput();
        input.setProcessDefinitionKey("testKey");
        input.setBusinessKey("bk1");
        input.setTitle("标题");
        input.setTypeName("类型");
        input.setType("bizType");
        input.setStarter("张三");
        input.setCompleteFirst(false);
        input.setCcUserIds("1001,1002");

        IWorkflowSubmitProcessor processor = mock(IWorkflowSubmitProcessor.class);
        when(processorRegistry.getSubmitProcessor("testKey")).thenReturn(processor);

        // mock startProcess dependencies
        mockStartProcess(input);

        workflowService.genericSubmit(input);

        verify(processor).preSubmit(input);
        verify(processor).postSubmit(any(StartProcessOutDto.class), eq(input));
        verify(carbonCopyService).sendCarbonCopy(eq("1001,1002"), anyString(),
                eq("testKey"), eq("bk1"), isNull(), eq("标题"), anyString());
    }

    @Test
    void genericSubmit_withoutProcessor_noCc() throws Exception {
        GenericSubmitInput input = new GenericSubmitInput();
        input.setProcessDefinitionKey("testKey");
        input.setBusinessKey("bk1");
        input.setTitle("标题");
        input.setTypeName("类型");
        input.setType("bizType");
        input.setStarter("张三");
        input.setCompleteFirst(false);
        input.setCcUserIds(null);

        when(processorRegistry.getSubmitProcessor("testKey")).thenReturn(null);

        mockStartProcess(input);

        workflowService.genericSubmit(input);

        verify(carbonCopyService, never()).sendCarbonCopy(anyString(), anyString(),
                anyString(), anyString(), anyString(), anyString(), anyString());
    }

    // ==================== genericApproval ====================

    @Test
    void genericApproval_processNull_shouldThrow() {
        GenericApprovalInput input = new GenericApprovalInput();
        input.setProcessInstanceId("proc1");

        mockProcessInstanceQuery("proc1", null);

        assertThrows(BusinessException.class,
                () -> workflowService.genericApproval(input));
    }

    // ==================== checkProcessCanRecallPre ====================

    @Test
    void checkProcessCanRecallPre_emptyHistory_shouldReturnFalse() {
        mockProcessInstanceQuery("proc1", mock(ProcessInstance.class));

        // history
        HistoricTaskInstanceQuery htq = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(htq);
        when(htq.processUnfinished()).thenReturn(htq);
        when(htq.processInstanceId("proc1")).thenReturn(htq);
        when(htq.finished()).thenReturn(htq);
        when(htq.orderByTaskCreateTime()).thenReturn(htq);
        when(htq.desc()).thenReturn(htq);
        when(htq.list()).thenReturn(Collections.emptyList());

        // process def
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getProcessDefinitionId()).thenReturn("defId1");
        ProcessInstanceQuery piq = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId("proc1")).thenReturn(piq);
        when(piq.singleResult()).thenReturn(pi);

        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repositoryService.getProcessDefinition("defId1")).thenReturn(pdEntity);

        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId("proc1")).thenReturn(tq);
        when(tq.list()).thenReturn(Collections.emptyList());

        assertFalse(workflowService.checkProcessCanRecallPre("proc1"));
    }

    // ==================== jumpToNode ====================

    @Test
    void jumpToNode_processNull_shouldThrow() {
        JumpToNodeInput input = new JumpToNodeInput();
        input.setProcessInstanceId("proc1");

        mockProcessInstanceQuery("proc1", null);

        assertThrows(BusinessException.class,
                () -> workflowService.jumpToNode(input));
    }

    @Test
    void jumpToNode_targetNull_shouldThrow() {
        JumpToNodeInput input = new JumpToNodeInput();
        input.setProcessInstanceId("proc1");
        input.setTargetTaskDefinitionKey("nonExistent");

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getProcessDefinitionId()).thenReturn("defId1");
        mockProcessInstanceQuery("proc1", pi);

        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repoImpl.getDeployedProcessDefinition("defId1")).thenReturn(pdEntity);
        when(pdEntity.findActivity("nonExistent")).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> workflowService.jumpToNode(input));

        setField(workflowService, "repositoryService", repositoryService);
    }

    @Test
    void jumpToNode_noCurrentTasks_shouldThrow() {
        JumpToNodeInput input = new JumpToNodeInput();
        input.setProcessInstanceId("proc1");
        input.setTargetTaskDefinitionKey("targetDk");

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getProcessDefinitionId()).thenReturn("defId1");
        mockProcessInstanceQuery("proc1", pi);

        RepositoryServiceImpl repoImpl = mock(RepositoryServiceImpl.class);
        setField(workflowService, "repositoryService", repoImpl);
        ProcessDefinitionEntity pdEntity = mock(ProcessDefinitionEntity.class);
        when(repoImpl.getDeployedProcessDefinition("defId1")).thenReturn(pdEntity);
        ActivityImpl targetActivity = mock(ActivityImpl.class);
        when(pdEntity.findActivity("targetDk")).thenReturn(targetActivity);

        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId("proc1")).thenReturn(tq);
        when(tq.list()).thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class,
                () -> workflowService.jumpToNode(input));

        setField(workflowService, "repositoryService", repositoryService);
    }

    // ==================== sendCarbonCopy ====================

    @Test
    void sendCarbonCopy_shouldDelegate() {
        CarbonCopyInput input = new CarbonCopyInput();
        input.setCcUserIds(List.of("1001"));
        workflowService.sendCarbonCopy(input);
        verify(carbonCopyService).sendCarbonCopy(input);
    }

    // ==================== readResource ====================

    @Test
    void readResource_finishedProcess_shouldGetEndEvent() {
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(hpi.getProcessDefinitionId()).thenReturn("defId1");

        HistoricProcessInstanceQuery hpiq = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiq);
        when(hpiq.processInstanceId("proc1")).thenReturn(hpiq);
        when(hpiq.singleResult()).thenReturn(hpi);

        // isFinished = true
        when(hpiq.finished()).thenReturn(hpiq);
        when(hpiq.count()).thenReturn(1L);

        // history activities
        HistoricActivityInstanceQuery haiq = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(haiq);
        when(haiq.processInstanceId("proc1")).thenReturn(haiq);
        when(haiq.orderByHistoricActivityInstanceStartTime()).thenReturn(haiq);
        when(haiq.asc()).thenReturn(haiq);
        when(haiq.list()).thenReturn(Collections.emptyList());

        // activeActivityIds is empty → get endEvent
        when(haiq.activityType("endEvent")).thenReturn(haiq);
        HistoricActivityInstance endActivity = mock(HistoricActivityInstance.class);
        when(endActivity.getActivityId()).thenReturn("endEvent1");
        when(haiq.singleResult()).thenReturn(endActivity);

        // BpmnModel
        when(repositoryService.getBpmnModel("defId1")).thenReturn(mock(org.activiti.bpmn.model.BpmnModel.class));

        ProcessDiagramGenerator diagramGen = mock(ProcessDiagramGenerator.class);
        when(processEngineConfiguration.getProcessDiagramGenerator()).thenReturn(diagramGen);
        when(processEngineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getLabelFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getAnnotationFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getClassLoader()).thenReturn(getClass().getClassLoader());
        when(diagramGen.generateDiagram(any(), eq("png"), anyList(), anyList(),
                anyString(), anyString(), anyString(), any(), eq(1.0)))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        byte[] result = workflowService.readResource("proc1");
        assertNotNull(result);
    }

    @Test
    void readResource_activeProcess_shouldGetActiveActivityIds() {
        HistoricProcessInstance hpi = mock(HistoricProcessInstance.class);
        when(hpi.getProcessDefinitionId()).thenReturn("defId1");

        HistoricProcessInstanceQuery hpiq = mock(HistoricProcessInstanceQuery.class);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiq);
        when(hpiq.processInstanceId("proc1")).thenReturn(hpiq);
        when(hpiq.singleResult()).thenReturn(hpi);

        // isFinished = false
        when(hpiq.finished()).thenReturn(hpiq);
        when(hpiq.count()).thenReturn(0L);

        // history activities
        HistoricActivityInstanceQuery haiq = mock(HistoricActivityInstanceQuery.class);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(haiq);
        when(haiq.processInstanceId("proc1")).thenReturn(haiq);
        when(haiq.orderByHistoricActivityInstanceStartTime()).thenReturn(haiq);
        when(haiq.asc()).thenReturn(haiq);
        when(haiq.list()).thenReturn(Collections.emptyList());

        // active activities
        when(runtimeService.getActiveActivityIds("proc1")).thenReturn(new ArrayList<>(List.of("activity1")));

        when(repositoryService.getBpmnModel("defId1")).thenReturn(mock(org.activiti.bpmn.model.BpmnModel.class));

        ProcessDiagramGenerator diagramGen = mock(ProcessDiagramGenerator.class);
        when(processEngineConfiguration.getProcessDiagramGenerator()).thenReturn(diagramGen);
        when(processEngineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getLabelFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getAnnotationFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getClassLoader()).thenReturn(getClass().getClassLoader());
        when(diagramGen.generateDiagram(any(), eq("png"), anyList(), anyList(),
                anyString(), anyString(), anyString(), any(), eq(1.0)))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        byte[] result = workflowService.readResource("proc1");
        assertNotNull(result);
    }

    // ==================== getTaskHistory ====================

    @Test
    void getTaskHistory_shouldReturnMapped() {
        HistoricTaskInstance hti = mock(HistoricTaskInstance.class);
        when(hti.getAssignee()).thenReturn("100");
        when(hti.getId()).thenReturn("ht1");
        when(hti.getStartTime()).thenReturn(new Date());
        when(hti.getEndTime()).thenReturn(new Date());

        HistoricTaskInstanceQuery htiq = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(htiq);
        when(htiq.processInstanceId("proc1")).thenReturn(htiq);
        when(htiq.taskDeleteReasonLike("completed")).thenReturn(htiq);
        when(htiq.finished()).thenReturn(htiq);
        when(htiq.list()).thenReturn(List.of(hti));

        // comments
        when(taskService.getTaskComments("ht1")).thenReturn(Collections.emptyList());

        // variables
        HistoricVariableInstanceQuery hviq = mock(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(hviq);
        when(hviq.taskId("ht1")).thenReturn(hviq);
        when(hviq.list()).thenReturn(Collections.emptyList());

        when(userNameUtils.mapList(anyList(), any())).thenAnswer(i -> i.getArgument(0));

        List<WorkflowTaskHistoryDto> result = workflowService.getTaskHistory("proc1");
        assertEquals(1, result.size());
        assertEquals(100L, result.getFirst().getAssigneeId());
    }

    @Test
    void getTaskHistory_nonNumericAssignee_shouldNotSetAssigneeId() {
        HistoricTaskInstance hti = mock(HistoricTaskInstance.class);
        when(hti.getAssignee()).thenReturn("管理员");
        when(hti.getId()).thenReturn("ht1");
        when(hti.getStartTime()).thenReturn(new Date());
        when(hti.getEndTime()).thenReturn(new Date());

        HistoricTaskInstanceQuery htiq = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(htiq);
        when(htiq.processInstanceId("proc1")).thenReturn(htiq);
        when(htiq.taskDeleteReasonLike("completed")).thenReturn(htiq);
        when(htiq.finished()).thenReturn(htiq);
        when(htiq.list()).thenReturn(List.of(hti));

        when(taskService.getTaskComments("ht1")).thenReturn(Collections.emptyList());

        HistoricVariableInstanceQuery hviq = mock(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(hviq);
        when(hviq.taskId("ht1")).thenReturn(hviq);
        when(hviq.list()).thenReturn(Collections.emptyList());

        when(userNameUtils.mapList(anyList(), any())).thenAnswer(i -> i.getArgument(0));

        List<WorkflowTaskHistoryDto> result = workflowService.getTaskHistory("proc1");
        assertEquals(1, result.size());
        assertNull(result.getFirst().getAssigneeId());
    }

    @Test
    void getTaskHistory_withComments_shouldSetComment() {
        HistoricTaskInstance hti = mock(HistoricTaskInstance.class);
        when(hti.getAssignee()).thenReturn("100");
        when(hti.getId()).thenReturn("ht1");
        when(hti.getStartTime()).thenReturn(new Date());
        when(hti.getEndTime()).thenReturn(new Date());

        HistoricTaskInstanceQuery htiq = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(htiq);
        when(htiq.processInstanceId("proc1")).thenReturn(htiq);
        when(htiq.taskDeleteReasonLike("completed")).thenReturn(htiq);
        when(htiq.finished()).thenReturn(htiq);
        when(htiq.list()).thenReturn(List.of(hti));

        Comment comment = mock(Comment.class);
        when(comment.getFullMessage()).thenReturn("同意通过");
        when(taskService.getTaskComments("ht1")).thenReturn(List.of(comment));

        HistoricVariableInstanceQuery hviq = mock(HistoricVariableInstanceQuery.class);
        when(historyService.createHistoricVariableInstanceQuery()).thenReturn(hviq);
        when(hviq.taskId("ht1")).thenReturn(hviq);
        HistoricVariableInstance hvi = mock(HistoricVariableInstance.class);
        when(hvi.getVariableName()).thenReturn("pass");
        when(hvi.getValue()).thenReturn(true);
        when(hviq.list()).thenReturn(List.of(hvi));

        when(userNameUtils.mapList(anyList(), any())).thenAnswer(i -> i.getArgument(0));

        List<WorkflowTaskHistoryDto> result = workflowService.getTaskHistory("proc1");
        assertEquals("同意通过", result.getFirst().getComment());
        assertEquals(true, result.getFirst().getVariables().get("pass"));
    }

    // ==================== recallProcess ====================

    @Test
    void recallProcess_shouldDelegate() {
        RecallProcessInput input = new RecallProcessInput();
        input.setProcessInstanceId("proc1");
        input.setBusinessData(Map.of("k", "v"));

        // 模拟checkProcessCanRecallPre返回false（测试不满足条件的分支）
        HistoricTaskInstanceQuery htq = mock(HistoricTaskInstanceQuery.class);
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(htq);
        when(htq.processUnfinished()).thenReturn(htq);
        when(htq.processInstanceId("proc1")).thenReturn(htq);
        when(htq.finished()).thenReturn(htq);
        when(htq.orderByTaskCreateTime()).thenReturn(htq);
        when(htq.desc()).thenReturn(htq);
        when(htq.list()).thenReturn(Collections.emptyList());

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getProcessDefinitionId()).thenReturn("defId1");
        ProcessInstanceQuery piq = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId("proc1")).thenReturn(piq);
        when(piq.singleResult()).thenReturn(pi);

        ProcessDefinitionEntity pde = mock(ProcessDefinitionEntity.class);
        when(repositoryService.getProcessDefinition("defId1")).thenReturn(pde);

        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceId("proc1")).thenReturn(tq);
        when(tq.list()).thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class,
                () -> workflowService.recallProcess(input));
    }

    // ==================== completeTask(CompleteTaskInputDto) ====================

    @Test
    void completeTaskInputDto_processNull_shouldThrow() {
        CompleteTaskInputDto input = new CompleteTaskInputDto();
        input.setProcessInstanceId("proc1");
        mockProcessInstanceQuery("proc1", null);

        assertThrows(BusinessException.class,
                () -> workflowService.completeTask(input));
    }

    @Test
    void completeTaskInputDto_withTitleTypeStarter_shouldSetVariables() throws Exception {
        CompleteTaskInputDto input = new CompleteTaskInputDto();
        input.setProcessInstanceId("proc1");
        input.setTaskId("t1");
        input.setTitle("标题");
        input.setTypeName("类型名");
        input.setType("bizType");
        input.setStarter("张三");
        input.setFilterStation(true);
        input.setVariables(null); // test null variables branch

        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getId()).thenReturn("proc1");
        when(pi.getProcessDefinitionKey()).thenReturn("defKey");
        when(pi.getBusinessKey()).thenReturn("bk1");
        mockProcessInstanceQuery("proc1", pi);

        Task task = mockTask("t1", "proc1", "审批", "", "dk1");
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.taskId("t1")).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.processInstanceId("proc1")).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);
        when(tq.list()).thenReturn(Collections.emptyList());

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(runtimeService.getVariables("proc1")).thenReturn(createStdVariables());

        // checkProcessEnd
        ProcessInstanceQuery piq2 = mock(ProcessInstanceQuery.class);
        // 这里重用同一个mock，通过返回null模拟流程已结束
        // 需要在completeTask后查询流程是否结束

        List<WorkflowTaskDto> result = workflowService.completeTask(input);

        // 验证variables被设置
        verify(taskService).complete(eq("t1"), any(), any());
    }

    // ==================== 辅助方法 ====================

    private ProcessInstanceQuery mockProcessInstanceQuery(String processInstanceId, ProcessInstance returnValue) {
        ProcessInstanceQuery piq = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId(processInstanceId)).thenReturn(piq);
        when(piq.singleResult()).thenReturn(returnValue);
        return piq;
    }

    private Task mockTask(String id, String processInstanceId, String name, String assignee, String taskDefKey) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(id);
        when(task.getProcessInstanceId()).thenReturn(processInstanceId);
        when(task.getName()).thenReturn(name);
        when(task.getAssignee()).thenReturn(assignee);
        when(task.getTaskDefinitionKey()).thenReturn(taskDefKey);
        when(task.getFormKey()).thenReturn("{}");
        when(task.getExecutionId()).thenReturn("exec1");
        return task;
    }

    private void mockFormService(String taskId) {
        TaskFormData formData = mock(TaskFormData.class);
        when(formService.getTaskFormData(taskId)).thenReturn(formData);
        when(formData.getFormProperties()).thenReturn(Collections.emptyList());
        when(formData.getFormKey()).thenReturn("{}");

        // identity links
        when(taskService.getIdentityLinksForTask(taskId)).thenReturn(Collections.emptyList());
    }

    private void mockHasTaskPermission(Task task, boolean hasPermission) {
        if (hasPermission) {
            loginMock.when(LoginUserIdContextHolder::getUserId).thenReturn(100L);
            UserContext uc = mock(UserContext.class);
            when(uc.getId()).thenReturn(100L);
            when(securityUtils.getCurrentUser()).thenReturn(uc);

            UserFullListDto userInfo = createUserFullInfo(100L);
            when(userRpcService.getUserFullById(100L)).thenReturn(userInfo);

            // task assignee = "100" => checkAssignee会通过
            when(task.getAssignee()).thenReturn("100");
            when(task.getFormKey()).thenReturn("{}");
        } else {
            loginMock.when(LoginUserIdContextHolder::getUserId).thenReturn(999L);
            UserContext uc = mock(UserContext.class);
            when(uc.getId()).thenReturn(999L);
            when(securityUtils.getCurrentUser()).thenReturn(uc);

            UserFullListDto userInfo = createUserFullInfo(999L);
            when(userRpcService.getUserFullById(999L)).thenReturn(userInfo);

            // task assignee = "888" => user 999 无权
            when(task.getAssignee()).thenReturn("888");
            when(task.getFormKey()).thenReturn("{}");
        }
    }

    private UserFullListDto createUserFullInfo(Long userId) {
        UserFullListDto dto = new UserFullListDto();
        dto.setId(userId);
        UserRoleDto role = new UserRoleDto();
        role.setRoleName("普通用户");
        dto.setUserRoles(List.of(role));
        return dto;
    }

    private Map<String, Object> createStdVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put(ActivitiConstants.TITLE, "测试标题");
        vars.put(ActivitiConstants.TYPE_NAME, "测试类型");
        vars.put(ActivitiConstants.BUSINESS_TYPE, "bizType");
        vars.put(ActivitiConstants.FILTER_STATION, false);
        vars.put(ActivitiConstants.STARTER, "张三");
        return vars;
    }

    private void mockStartProcess(WorkflowProcessDto input) {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getProcessInstanceId()).thenReturn("proc1");
        when(pi.getId()).thenReturn("proc1");
        when(pi.getBusinessKey()).thenReturn(input.getBusinessKey());
        when(pi.getProcessDefinitionKey()).thenReturn(input.getProcessDefinitionKey());

        when(runtimeService.startProcessInstanceByKeyAndTenantId(
                anyString(), anyString(), anyMap(), anyString())).thenReturn(pi);

        // getTasksByProcessAndInitAssignee
        TaskQuery tq = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.processInstanceIdIn(anyList())).thenReturn(tq);
        when(tq.list()).thenReturn(Collections.emptyList());

        when(runtimeService.getVariables(anyString())).thenReturn(createStdVariables());

        // userRpcService for starter
        UserFullListDto user = createUserFullInfo(100L);
        user.setName("张三");
        when(userRpcService.getUserFullById(100L)).thenReturn(user);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

