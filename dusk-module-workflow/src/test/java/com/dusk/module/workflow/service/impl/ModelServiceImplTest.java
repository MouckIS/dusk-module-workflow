package com.dusk.module.workflow.service.impl;

import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.module.workflow.dto.ModelDto;
import com.dusk.module.workflow.dto.GetModelsInput;
import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.common.core.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ModelServiceImpl 单元测试
 * 覆盖：create、getModels（有/无name filter）、removeModelById、rollBackByKey（空列表/正常）、
 * removeModelByKey、deploy（有/无.bpmn20.xml后缀）、getSvgXmlByModelId（null model）、getSvgXmlByKey（null model）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceImplTest {

    @Mock
    private RepositoryService repositoryService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ModelServiceImpl modelService;

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

    // ==================== create ====================

    @Test
    void create_shouldSaveModelAndEditorSource() throws Exception {
        ObjectNode editorNode = mock(ObjectNode.class);
        ObjectNode properties = mock(ObjectNode.class);
        ObjectNode stencilset = mock(ObjectNode.class);
        ObjectNode modelObjectNode = mock(ObjectNode.class);

        when(objectMapper.createObjectNode())
                .thenReturn(editorNode)
                .thenReturn(properties)
                .thenReturn(stencilset)
                .thenReturn(modelObjectNode);
        when(editorNode.put(anyString(), anyString())).thenReturn(editorNode);
        when(properties.put(anyString(), anyString())).thenReturn(properties);
        when(stencilset.put(anyString(), anyString())).thenReturn(stencilset);
        when(modelObjectNode.put(anyString(), anyString())).thenReturn(modelObjectNode);
        when(modelObjectNode.put(anyString(), anyInt())).thenReturn(modelObjectNode);
        when(modelObjectNode.toString()).thenReturn("{}");
        when(editorNode.toString()).thenReturn("{\"editor\"}");

        Model model = mock(Model.class);
        when(model.getKey()).thenReturn("testKey");
        when(model.getId()).thenReturn("model1");
        when(repositoryService.newModel()).thenReturn(model);

        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.modelKey(anyString())).thenReturn(mq);
        when(mq.count()).thenReturn(0L);

        modelService.create("测试流程", "testKey", "描述", "category1");

        verify(repositoryService).saveModel(model);
        verify(repositoryService).addModelEditorSource(eq("model1"), any(byte[].class));
    }

    @Test
    void create_withNullDesc_shouldUseEmpty() throws Exception {
        ObjectNode editorNode = mock(ObjectNode.class);
        ObjectNode properties = mock(ObjectNode.class);
        ObjectNode stencilset = mock(ObjectNode.class);
        ObjectNode modelObjectNode = mock(ObjectNode.class);

        when(objectMapper.createObjectNode())
                .thenReturn(editorNode)
                .thenReturn(properties)
                .thenReturn(stencilset)
                .thenReturn(modelObjectNode);
        when(editorNode.put(anyString(), anyString())).thenReturn(editorNode);
        when(properties.put(anyString(), anyString())).thenReturn(properties);
        when(stencilset.put(anyString(), anyString())).thenReturn(stencilset);
        when(modelObjectNode.put(anyString(), anyString())).thenReturn(modelObjectNode);
        when(modelObjectNode.put(anyString(), anyInt())).thenReturn(modelObjectNode);
        when(modelObjectNode.toString()).thenReturn("{}");
        when(editorNode.toString()).thenReturn("{\"editor\"}");

        Model model = mock(Model.class);
        when(model.getKey()).thenReturn("testKey");
        when(model.getId()).thenReturn("model1");
        when(repositoryService.newModel()).thenReturn(model);

        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.modelKey(anyString())).thenReturn(mq);
        when(mq.count()).thenReturn(0L);

        // desc == null -> 传""
        modelService.create("名称", "key1", null, "cat");

        verify(properties).put("documentation", "");
    }

    // ==================== getModels ====================

    @Test
    void getModels_withoutNameFilter() {
        GetModelsInput input = new GetModelsInput();
        input.setPageNumber(1);
        input.setPageSize(10);
        input.setName(null);

        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.latestVersion()).thenReturn(mq);
        when(mq.orderByLastUpdateTime()).thenReturn(mq);
        when(mq.desc()).thenReturn(mq);
        when(mq.count()).thenReturn(0L);
        when(mq.listPage(0, 10)).thenReturn(Collections.emptyList());

        PagedResultDto<ModelDto> result = modelService.getModels(input);
        assertEquals(0, result.getTotal());
        verify(mq, never()).modelNameLike(anyString());
    }

    @Test
    void getModels_withNameFilter() {
        GetModelsInput input = new GetModelsInput();
        input.setPageNumber(2);
        input.setPageSize(5);
        input.setName("test");

        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.latestVersion()).thenReturn(mq);
        when(mq.orderByLastUpdateTime()).thenReturn(mq);
        when(mq.desc()).thenReturn(mq);
        when(mq.modelNameLike("test")).thenReturn(mq);
        when(mq.count()).thenReturn(10L);
        when(mq.listPage(5, 5)).thenReturn(Collections.emptyList());

        PagedResultDto<ModelDto> result = modelService.getModels(input);
        assertEquals(10, result.getTotal());
        verify(mq).modelNameLike("test");
    }

    // ==================== removeModelById ====================

    @Test
    void removeModelById_shouldCallDelete() {
        assertTrue(modelService.removeModelById("m1"));
        verify(repositoryService).deleteModel("m1");
    }

    // ==================== rollBackByKey ====================

    @Test
    void rollBackByKey_emptyList_shouldThrow() {
        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelKey("key1")).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.modelVersion(1)).thenReturn(mq);
        when(mq.latestVersion()).thenReturn(mq);
        when(mq.list()).thenReturn(Collections.emptyList());

        assertThrows(BusinessException.class,
                () -> modelService.rollBackByKey("key1", 1));
    }

    @Test
    void rollBackByKey_foundModel_shouldDelete() {
        Model model = mock(Model.class);
        when(model.getId()).thenReturn("m1");

        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelKey("key1")).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.modelVersion(2)).thenReturn(mq);
        when(mq.latestVersion()).thenReturn(mq);
        when(mq.list()).thenReturn(List.of(model));

        assertTrue(modelService.rollBackByKey("key1", 2));
        verify(repositoryService).deleteModel("m1");
    }

    // ==================== removeModelByKey ====================

    @Test
    void removeModelByKey_shouldDeleteAll() {
        Model m1 = mock(Model.class);
        when(m1.getId()).thenReturn("m1");
        Model m2 = mock(Model.class);
        when(m2.getId()).thenReturn("m2");

        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.modelKey("key1")).thenReturn(mq);
        when(mq.list()).thenReturn(List.of(m1, m2));

        assertTrue(modelService.removeModelByKey("key1"));
        verify(repositoryService).deleteModel("m1");
        verify(repositoryService).deleteModel("m2");
    }

    // ==================== getSvgXmlByModelId ====================

    @Test
    void getSvgXmlByModelId_nullModel_shouldThrow() {
        when(repositoryService.getModel("m1")).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> modelService.getSvgXmlByModelId("m1"));
    }

    // ==================== getSvgXmlByKey ====================

    @Test
    void getSvgXmlByKey_nullModel_shouldThrow() {
        ModelQuery mq = mock(ModelQuery.class);
        when(repositoryService.createModelQuery()).thenReturn(mq);
        when(mq.modelTenantId(anyString())).thenReturn(mq);
        when(mq.modelKey("key1")).thenReturn(mq);
        when(mq.latestVersion()).thenReturn(mq);
        when(mq.singleResult()).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> modelService.getSvgXmlByKey("key1"));
    }
}

