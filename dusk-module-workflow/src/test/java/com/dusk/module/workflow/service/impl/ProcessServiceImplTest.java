package com.dusk.module.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.common.core.tenant.TenantContextHolder;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ProcessServiceImpl 单元测试
 * 覆盖：removeProcIns、getProcesses（有/无category、paginated/unPage）、getResource（image/xml/other）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProcessServiceImplTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private RuntimeService runtimeService;

    @InjectMocks
    private ProcessServiceImpl processService;

    private MockedStatic<TenantContextHolder> tenantMock;

    @BeforeEach
    void setUp() {
        tenantMock = mockStatic(TenantContextHolder.class);
        tenantMock.when(TenantContextHolder::getTenantId).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        tenantMock.close();
    }

    // ==================== removeProcIns ====================

    @Test
    void removeProcIns_shouldDeleteDeployment() {
        assertTrue(processService.removeProcIns("deploy1"));
        verify(repositoryService).deleteDeployment("deploy1", true);
    }

    // ==================== getProcesses ====================

    @Test
    void getProcesses_withoutCategory_paged() {
        GetProcessesInput input = new GetProcessesInput();
        input.setPageNumber(1);
        input.setPageSize(10);
        input.setCategory(null);
        input.setUnPage(false);

        ProcessDefinitionQuery pdq = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);
        when(pdq.processDefinitionTenantId(anyString())).thenReturn(pdq);
        when(pdq.latestVersion()).thenReturn(pdq);
        when(pdq.processDefinitionCategory(anyString())).thenReturn(pdq);
        when(pdq.count()).thenReturn(0L);
        when(pdq.listPage(0, 10)).thenReturn(Collections.emptyList());

        PagedResultDto<ProcessDefDto> result = processService.getProcesses(input);
        assertEquals(0, result.getTotal());
        verify(pdq, never()).processDefinitionCategory(anyString());
    }

    @Test
    void getProcesses_withCategory_unPage() {
        GetProcessesInput input = new GetProcessesInput();
        input.setPageNumber(1);
        input.setPageSize(10);
        input.setCategory("myCategory");
        input.setUnPage(true);

        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getDeploymentId()).thenReturn("dep1");
        when(pd.getId()).thenReturn("pd1");
        when(pd.getKey()).thenReturn("key1");

        ProcessDefinitionQuery pdq = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);
        when(pdq.processDefinitionTenantId(anyString())).thenReturn(pdq);
        when(pdq.latestVersion()).thenReturn(pdq);
        when(pdq.processDefinitionCategory("myCategory")).thenReturn(pdq);
        when(pdq.count()).thenReturn(1L);
        when(pdq.list()).thenReturn(List.of(pd));

        Deployment dep = mock(Deployment.class);
        when(dep.getId()).thenReturn("dep1");
        when(dep.getName()).thenReturn("流程1");
        when(dep.getDeploymentTime()).thenReturn(new Date());
        DeploymentQuery dq = mock(DeploymentQuery.class);
        when(repositoryService.createDeploymentQuery()).thenReturn(dq);
        when(dq.deploymentId("dep1")).thenReturn(dq);
        when(dq.singleResult()).thenReturn(dep);

        PagedResultDto<ProcessDefDto> result = processService.getProcesses(input);
        assertEquals(1, result.getTotal());
        verify(pdq).processDefinitionCategory("myCategory");
    }

    // ==================== getResource ====================

    @Test
    void getResource_image_shouldUseDiagramResourceName() {
        mockProcessInstanceAndDefinition("proc1", "defId1", "image");

        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getDiagramResourceName()).thenReturn("diagram.png");
        when(pd.getResourceName()).thenReturn("process.bpmn20.xml");
        when(pd.getDeploymentId()).thenReturn("dep1");

        ProcessDefinitionQuery pdq = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);
        when(pdq.processDefinitionId("defId1")).thenReturn(pdq);
        when(pdq.singleResult()).thenReturn(pd);

        when(repositoryService.getResourceAsStream("dep1", "diagram.png"))
                .thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        byte[] result = processService.getResource("proc1", "image");
        assertNotNull(result);
    }

    @Test
    void getResource_xml_shouldUseResourceName() {
        mockProcessInstanceAndDefinition("proc1", "defId1", "xml");

        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getDiagramResourceName()).thenReturn("diagram.png");
        when(pd.getResourceName()).thenReturn("process.bpmn20.xml");
        when(pd.getDeploymentId()).thenReturn("dep1");

        ProcessDefinitionQuery pdq = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);
        when(pdq.processDefinitionId("defId1")).thenReturn(pdq);
        when(pdq.singleResult()).thenReturn(pd);

        when(repositoryService.getResourceAsStream("dep1", "process.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(new byte[]{4, 5, 6}));

        byte[] result = processService.getResource("proc1", "xml");
        assertNotNull(result);
    }

    @Test
    void getResource_otherResType_shouldUseEmptyResourceName() {
        mockProcessInstanceAndDefinition("proc1", "defId1", "other");

        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getDeploymentId()).thenReturn("dep1");

        ProcessDefinitionQuery pdq = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdq);
        when(pdq.processDefinitionId("defId1")).thenReturn(pdq);
        when(pdq.singleResult()).thenReturn(pd);

        when(repositoryService.getResourceAsStream(eq("dep1"), eq("")))
                .thenReturn(new ByteArrayInputStream(new byte[]{}));

        byte[] result = processService.getResource("proc1", "other");
        assertNotNull(result);
    }

    private void mockProcessInstanceAndDefinition(String procInsId, String defId, String resType) {
        ProcessInstance pi = mock(ProcessInstance.class);
        when(pi.getProcessDefinitionId()).thenReturn(defId);

        ProcessInstanceQuery piq = mock(ProcessInstanceQuery.class);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(piq);
        when(piq.processInstanceId(procInsId)).thenReturn(piq);
        when(piq.singleResult()).thenReturn(pi);
    }
}

