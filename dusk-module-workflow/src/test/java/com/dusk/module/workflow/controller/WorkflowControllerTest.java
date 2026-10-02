package com.dusk.module.workflow.controller;

import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.model.tenant.TenantContextHolder;
import com.dusk.module.workflow.dto.GetRelateNodeInput;
import com.dusk.module.workflow.dto.GetRelateTaskInput;
import com.dusk.module.workflow.dto.RelatedNodeInfo;
import com.dusk.module.workflow.service.IWorkflowService;
import com.dusk.workflow.dto.WorkflowTaskDetailDto;
import com.dusk.workflow.dto.WorkflowTaskDto;
import com.dusk.workflow.dto.WorkflowTaskHistoryDto;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.image.ProcessDiagramGenerator;
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkflowController} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class WorkflowControllerTest {

    @Mock
    private IWorkflowService workflowService;

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ProcessEngineConfiguration processEngineConfiguration;

    @Mock
    private HttpServletResponse response;

    @Mock
    private ServletOutputStream servletOutputStream;

    @InjectMocks
    private WorkflowController controller;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("resourceRead：写出PNG流程图")
    void resourceRead() throws Exception {
        byte[] data = "png".getBytes(StandardCharsets.UTF_8);
        when(workflowService.readResource("p1")).thenReturn(data);
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        controller.resourceRead("p1", response);

        verify(response).setContentType("image/png");
        verify(servletOutputStream).write(data);
        verify(servletOutputStream).flush();
        verify(servletOutputStream).close();
    }

    @Test
    @DisplayName("getTaskHistory 委托")
    void getTaskHistory() {
        List<WorkflowTaskHistoryDto> expected = List.of(new WorkflowTaskHistoryDto());
        when(workflowService.getTaskHistory("p1")).thenReturn(expected);

        assertThat(controller.getTaskHistory("p1")).isSameAs(expected);
    }

    @Test
    @DisplayName("getTaskHistories：数组转列表后委托")
    void getTaskHistories() {
        List<WorkflowTaskHistoryDto> expected = List.of(new WorkflowTaskHistoryDto());
        when(workflowService.getTaskHistories(List.of("p1", "p2"))).thenReturn(expected);

        assertThat(controller.getTaskHistories(new String[]{"p1", "p2"})).isSameAs(expected);
    }

    @Test
    @DisplayName("checkProcessCanRecallPre 委托")
    void checkProcessCanRecallPre() {
        when(workflowService.checkProcessCanRecallPre("p1")).thenReturn(true);

        assertThat(controller.checkProcessCanRecallPre("p1")).isTrue();
    }

    @Test
    @DisplayName("recallPre 委托")
    void recallPre() {
        controller.recallPre("p1");

        verify(workflowService).recallPre("p1");
    }

    @Test
    @DisplayName("getWorkFlowImgByProcessKey：流程定义不存在抛出业务异常")
    void getWorkFlowImgByProcessKeyNotFound() {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> controller.getWorkFlowImgByProcessKey(response, "key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在名为key的流程");
    }

    @Test
    @DisplayName("getWorkFlowImgByProcessKey：正常写出图片")
    void getWorkFlowImgByProcessKey() throws Exception {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);

        BpmnModel bpmnModel = new BpmnModel();
        when(repositoryService.getBpmnModel("pd1")).thenReturn(bpmnModel);

        ProcessDiagramGenerator generator = org.mockito.Mockito.mock(ProcessDiagramGenerator.class);
        when(processEngineConfiguration.getProcessDiagramGenerator()).thenReturn(generator);
        when(processEngineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getLabelFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getAnnotationFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getClassLoader()).thenReturn(getClass().getClassLoader());

        byte[] data = "png".getBytes(StandardCharsets.UTF_8);
        when(generator.generateDiagram(any(), anyString(), anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(new ByteArrayInputStream(data));
        when(response.getOutputStream()).thenReturn(servletOutputStream);

        controller.getWorkFlowImgByProcessKey(response, "key");

        verify(response).setContentType("image/png");
        verify(servletOutputStream).write(any(byte[].class));
        verify(servletOutputStream).flush();
        verify(servletOutputStream).close();
    }

    @Test
    @DisplayName("getWorkFlowImgByProcessKey：写响应异常被吞掉不影响主流程")
    void getWorkFlowImgByProcessKeySwallowsWriteException() throws Exception {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        when(repositoryService.getBpmnModel("pd1")).thenReturn(new BpmnModel());

        ProcessDiagramGenerator generator = org.mockito.Mockito.mock(ProcessDiagramGenerator.class);
        when(processEngineConfiguration.getProcessDiagramGenerator()).thenReturn(generator);
        when(processEngineConfiguration.getActivityFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getLabelFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getAnnotationFontName()).thenReturn("宋体");
        when(processEngineConfiguration.getClassLoader()).thenReturn(getClass().getClassLoader());
        when(generator.generateDiagram(any(), anyString(), anyString(), anyString(), anyString(), any(), anyBoolean()))
                .thenReturn(new ByteArrayInputStream("png".getBytes(StandardCharsets.UTF_8)));

        when(response.getOutputStream()).thenThrow(new IOException("boom"));

        assertThatCode(() -> controller.getWorkFlowImgByProcessKey(response, "key")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("getTasksByProcess 委托")
    void getTasksByProcess() {
        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        when(workflowService.getTasksByProcess(List.of("p1"))).thenReturn(expected);

        assertThat(controller.getTasksByProcess(new String[]{"p1"})).isSameAs(expected);
    }

    @Test
    @DisplayName("getTasksByProcessWithoutAuth 委托（不过滤权限）")
    void getTasksByProcessWithoutAuth() {
        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        when(workflowService.getTasksByProcess(List.of("p1"), false)).thenReturn(expected);

        assertThat(controller.getTasksByProcessWithoutAuth(new String[]{"p1"})).isSameAs(expected);
    }

    @Test
    @DisplayName("getRelateTask 委托")
    void getRelateTask() {
        GetRelateTaskInput input = new GetRelateTaskInput();
        input.setTaskId("t1");
        input.setAutoCalculate(true);
        Map<String, Object> variables = Map.of("k", "v");
        input.setVariables(variables);

        List<WorkflowTaskDto> expected = List.of(new WorkflowTaskDto());
        when(workflowService.getRelateTask("t1", true, variables)).thenReturn(expected);

        assertThat(controller.getRelateTask(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("getRelateNode 委托")
    void getRelateNode() {
        GetRelateNodeInput input = new GetRelateNodeInput();
        input.setTaskId("t1");
        input.setProcessKey("key");
        input.setAutoCalculate(false);

        List<RelatedNodeInfo> expected = List.of(new RelatedNodeInfo());
        when(workflowService.getRelatedNode("t1", "key", false, null)).thenReturn(expected);

        assertThat(controller.getRelateNode(input)).isSameAs(expected);
    }

    @Test
    @DisplayName("getProcessDefinitionFirstFormKey 委托")
    void getProcessDefinitionFirstFormKey() {
        when(workflowService.getProcessDefinitionFirstFormKey("key")).thenReturn("formKey");

        assertThat(controller.getProcessDefinitionFirstFormKey("key")).isEqualTo("formKey");
    }

    @Test
    @DisplayName("getCurrTasksWithAssigneeInfos 委托")
    void getCurrTasksWithAssigneeInfos() {
        List<WorkflowTaskDetailDto> expected = List.of(new WorkflowTaskDetailDto());
        when(workflowService.getCurrTasksWithAssigneeInfos("p1")).thenReturn(expected);

        assertThat(controller.getCurrTasksWithAssigneeInfos("p1")).isSameAs(expected);
    }
}
