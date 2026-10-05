package com.dusk.module.workflow.service.impl;

import com.dusk.common.model.dto.PagedAndSortedInputDto;
import com.dusk.common.model.dto.PagedResultDto;
import com.dusk.common.model.tenant.TenantContextHolder;
import com.dusk.workflow.dto.WorkflowTaskDto;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.image.ProcessDiagramGenerator;
import org.flowable.spring.ProcessEngineFactoryBean;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link ActTaskServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ActTaskServiceImplTest {

    @Mock
    private TaskService taskService;

    @Mock
    private RuntimeService runtimeService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private HistoryService historyService;

    @Mock
    private ProcessEngineFactoryBean processEngine;

    @InjectMocks
    private ActTaskServiceImpl actTaskService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(66L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("getTasks：按租户分页查询任务")
    void getTasks() {
        TaskQuery taskQuery = org.mockito.Mockito.mock(TaskQuery.class, Answers.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(taskQuery);
        when(taskQuery.count()).thenReturn(2L);
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getId()).thenReturn("t1");
        when(taskQuery.listPage(0, 10)).thenReturn(List.of(task));

        PagedAndSortedInputDto input = new PagedAndSortedInputDto();
        PagedResultDto<WorkflowTaskDto> result = actTaskService.getTasks(input);

        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getId()).isEqualTo("t1");
        org.mockito.Mockito.verify(taskQuery).taskTenantId("66");
    }

    @Test
    @DisplayName("viewByTaskId：流程实例仍在运行时取活动节点")
    void viewByTaskIdRunningInstance() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        TaskQuery tq = org.mockito.Mockito.mock(TaskQuery.class, Answers.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        ProcessInstance processInstance = org.mockito.Mockito.mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("p1");
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = org.mockito.Mockito.mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);

        when(runtimeService.getActiveActivityIds("p1")).thenReturn(List.of("node1"));

        // 历史流程实例查询同样会被调用，此处返回 null 即可（运行实例优先）
        HistoricProcessInstanceQuery hpiQuery = org.mockito.Mockito.mock(HistoricProcessInstanceQuery.class, Answers.RETURNS_SELF);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);

        BpmnModel bpmnModel = new BpmnModel();
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel);

        ProcessEngineConfigurationImpl engineConfiguration = org.mockito.Mockito.mock(ProcessEngineConfigurationImpl.class);
        when(processEngine.getProcessEngineConfiguration()).thenReturn(engineConfiguration);
        when(engineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(engineConfiguration.getLabelFontName()).thenReturn("宋体");

        ProcessDiagramGenerator diagramGenerator = org.mockito.Mockito.mock(ProcessDiagramGenerator.class);
        when(engineConfiguration.getProcessDiagramGenerator()).thenReturn(diagramGenerator);
        byte[] png = "png".getBytes(StandardCharsets.UTF_8);
        when(diagramGenerator.generateDiagram(any(), anyString(), anyList(), any(), anyString(), anyString(),
                anyString(), any(), anyDouble(), anyBoolean())).thenReturn(new ByteArrayInputStream(png));

        assertThat(actTaskService.viewByTaskId("t1")).isEqualTo(png);
    }

    @Test
    @DisplayName("viewByTaskId：流程实例已结束，回退到历史活动节点")
    void viewByTaskIdHistoricInstance() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        TaskQuery tq = org.mockito.Mockito.mock(TaskQuery.class, Answers.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        // 运行实例不存在
        ProcessInstanceQuery piQuery = org.mockito.Mockito.mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        HistoricProcessInstance historic = org.mockito.Mockito.mock(HistoricProcessInstance.class);
        when(historic.getProcessDefinitionId()).thenReturn("pd1");
        HistoricProcessInstanceQuery hpiQuery = org.mockito.Mockito.mock(HistoricProcessInstanceQuery.class, Answers.RETURNS_SELF);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(historic);

        HistoricActivityInstance activity = org.mockito.Mockito.mock(HistoricActivityInstance.class);
        when(activity.getActivityId()).thenReturn("node1");
        HistoricActivityInstanceQuery haiQuery = org.mockito.Mockito.mock(HistoricActivityInstanceQuery.class, Answers.RETURNS_SELF);
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(haiQuery);
        when(haiQuery.list()).thenReturn(List.of(activity));

        BpmnModel bpmnModel = new BpmnModel();
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel);

        ProcessEngineConfigurationImpl engineConfiguration = org.mockito.Mockito.mock(ProcessEngineConfigurationImpl.class);
        when(processEngine.getProcessEngineConfiguration()).thenReturn(engineConfiguration);
        when(engineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(engineConfiguration.getLabelFontName()).thenReturn("宋体");

        ProcessDiagramGenerator diagramGenerator = org.mockito.Mockito.mock(ProcessDiagramGenerator.class);
        when(engineConfiguration.getProcessDiagramGenerator()).thenReturn(diagramGenerator);
        byte[] png = "png".getBytes(StandardCharsets.UTF_8);
        when(diagramGenerator.generateDiagram(any(), anyString(), anyList(), any(), anyString(), anyString(),
                anyString(), any(), anyDouble(), anyBoolean())).thenReturn(new ByteArrayInputStream(png));

        assertThat(actTaskService.viewByTaskId("t1")).isEqualTo(png);
    }

    @Test
    @DisplayName("viewByTaskId：运行实例与历史实例均不存在时回退为空定义 id")
    void viewByTaskIdNoInstanceAtAll() {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getProcessInstanceId()).thenReturn("p1");
        TaskQuery tq = org.mockito.Mockito.mock(TaskQuery.class, Answers.RETURNS_SELF);
        when(taskService.createTaskQuery()).thenReturn(tq);
        when(tq.singleResult()).thenReturn(task);

        ProcessInstanceQuery piQuery = org.mockito.Mockito.mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(null);

        HistoricProcessInstanceQuery hpiQuery = org.mockito.Mockito.mock(HistoricProcessInstanceQuery.class, Answers.RETURNS_SELF);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(hpiQuery);
        when(hpiQuery.singleResult()).thenReturn(null);

        BpmnModel bpmnModel = new BpmnModel();
        when(repositoryService.getBpmnModel(any())).thenReturn(bpmnModel);

        ProcessEngineConfigurationImpl engineConfiguration = org.mockito.Mockito.mock(ProcessEngineConfigurationImpl.class);
        when(processEngine.getProcessEngineConfiguration()).thenReturn(engineConfiguration);
        when(engineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(engineConfiguration.getLabelFontName()).thenReturn("宋体");

        ProcessDiagramGenerator diagramGenerator = org.mockito.Mockito.mock(ProcessDiagramGenerator.class);
        when(engineConfiguration.getProcessDiagramGenerator()).thenReturn(diagramGenerator);
        byte[] png = "png".getBytes(StandardCharsets.UTF_8);
        when(diagramGenerator.generateDiagram(any(), anyString(), anyList(), any(), anyString(), anyString(),
                anyString(), any(), anyDouble(), anyBoolean())).thenReturn(new ByteArrayInputStream(png));

        assertThat(actTaskService.viewByTaskId("t1")).isEqualTo(png);
    }
}
