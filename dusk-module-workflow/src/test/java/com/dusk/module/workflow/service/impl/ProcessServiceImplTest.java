package com.dusk.module.workflow.service.impl;

import com.dusk.common.model.dto.PagedResultDto;
import com.dusk.common.model.tenant.TenantContextHolder;
import com.dusk.module.workflow.dto.GetProcessesInput;
import com.dusk.module.workflow.dto.ProcessDefDto;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProcessServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ProcessServiceImplTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private RuntimeService runtimeService;

    @InjectMocks
    private ProcessServiceImpl processService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("removeProcIns：级联删除部署")
    void removeProcIns() {
        assertThat(processService.removeProcIns("dep1")).isTrue();

        verify(repositoryService).deleteDeployment("dep1", true);
    }

    @Test
    @DisplayName("getProcesses：带分类过滤且分页")
    void getProcessesPagedWithCategory() {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.count()).thenReturn(1L);

        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getDeploymentId()).thenReturn("dep1");
        when(query.listPage(0, 5)).thenReturn(List.of(pd));

        DeploymentQuery deploymentQuery = org.mockito.Mockito.mock(DeploymentQuery.class, Answers.RETURNS_SELF);
        Deployment deployment = org.mockito.Mockito.mock(Deployment.class);
        when(deployment.getId()).thenReturn("dep1");
        when(deployment.getName()).thenReturn("部署名");
        when(deployment.getDeploymentTime()).thenReturn(new Date());
        when(deploymentQuery.singleResult()).thenReturn(deployment);
        when(repositoryService.createDeploymentQuery()).thenReturn(deploymentQuery);

        GetProcessesInput input = new GetProcessesInput();
        input.setCategory("cat");
        input.setPageNumber(1);
        input.setPageSize(5);

        PagedResultDto<ProcessDefDto> result = processService.getProcesses(input);

        verify(query).processDefinitionCategory("cat");
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getDeploymentId()).isEqualTo("dep1");
        assertThat(result.getItems().getFirst().getName()).isEqualTo("部署名");
        assertThat(result.getItems().getFirst().getDeploymentTime()).isNotNull();
    }

    @Test
    @DisplayName("getProcesses：无分类且不分页")
    void getProcessesUnPagedWithoutCategory() {
        ProcessDefinitionQuery query = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.count()).thenReturn(0L);
        when(query.list()).thenReturn(List.of());

        GetProcessesInput input = new GetProcessesInput();
        input.setCategory("  ");
        input.setUnPage(true);

        PagedResultDto<ProcessDefDto> result = processService.getProcesses(input);

        assertThat(result.getItems()).isEmpty();
    }

    /** 复用：stub 运行中的流程实例查询。 */
    private void stubRunningProcessInstance() {
        ProcessInstance processInstance = org.mockito.Mockito.mock(ProcessInstance.class);
        when(processInstance.getProcessDefinitionId()).thenReturn("pd1");
        ProcessInstanceQuery piQuery = org.mockito.Mockito.mock(ProcessInstanceQuery.class, Answers.RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piQuery);
        when(piQuery.singleResult()).thenReturn(processInstance);
    }

    /** 复用：stub 流程定义查询。 */
    private ProcessDefinition stubProcessDefinition() {
        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getDeploymentId()).thenReturn("dep1");
        ProcessDefinitionQuery pdQuery = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);
        when(pdQuery.singleResult()).thenReturn(pd);
        return pd;
    }

    @Test
    @DisplayName("getResource：resType=image 取图片资源")
    void getResourceImage() {
        stubRunningProcessInstance();
        ProcessDefinition pd = stubProcessDefinition();
        when(pd.getDiagramResourceName()).thenReturn("diagram.png");

        byte[] bytes = "png".getBytes(StandardCharsets.UTF_8);
        when(repositoryService.getResourceAsStream("dep1", "diagram.png"))
                .thenReturn(new ByteArrayInputStream(bytes));

        assertThat(processService.getResource("p1", "image")).isEqualTo(bytes);
    }

    @Test
    @DisplayName("getResource：resType=xml 取XML资源")
    void getResourceXml() {
        stubRunningProcessInstance();
        ProcessDefinition pd = stubProcessDefinition();
        when(pd.getResourceName()).thenReturn("process.bpmn20.xml");

        byte[] bytes = "xml".getBytes(StandardCharsets.UTF_8);
        when(repositoryService.getResourceAsStream("dep1", "process.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(bytes));

        assertThat(processService.getResource("p1", "xml")).isEqualTo(bytes);
    }

    @Test
    @DisplayName("getResource：未知 resType 时资源名为空")
    void getResourceUnknownType() {
        stubRunningProcessInstance();
        stubProcessDefinition();

        when(repositoryService.getResourceAsStream("dep1", ""))
                .thenReturn(new ByteArrayInputStream(new byte[0]));

        assertThat(processService.getResource("p1", "other")).isEmpty();
    }
}
