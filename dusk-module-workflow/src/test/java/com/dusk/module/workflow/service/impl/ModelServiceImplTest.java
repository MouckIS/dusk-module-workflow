package com.dusk.module.workflow.service.impl;

import com.dusk.common.model.dto.PagedResultDto;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.model.tenant.TenantContextHolder;
import com.dusk.module.workflow.dto.GetModelsInput;
import com.dusk.module.workflow.dto.ModelDto;
import tools.jackson.databind.ObjectMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.Model;
import org.flowable.engine.repository.ModelQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ModelServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ModelServiceImplTest {

    private static final String BPMN_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n"
                    + "             xmlns:flowable=\"http://flowable.org/bpmn\"\n"
                    + "             targetNamespace=\"http://flowable.org/processdef\">\n"
                    + "  <process id=\"myProcess\" name=\"My Process\" isExecutable=\"true\">\n"
                    + "    <startEvent id=\"start\"/>\n"
                    + "    <sequenceFlow id=\"f1\" sourceRef=\"start\" targetRef=\"usr\"/>\n"
                    + "    <userTask id=\"usr\" name=\"User\"/>\n"
                    + "    <sequenceFlow id=\"f2\" sourceRef=\"usr\" targetRef=\"end\"/>\n"
                    + "    <endEvent id=\"end\"/>\n"
                    + "  </process>\n"
                    + "</definitions>";

    private static final String EMPTY_DEFINITIONS_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n"
                    + "             targetNamespace=\"http://flowable.org/processdef\">\n"
                    + "</definitions>";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private RepositoryService repositoryService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ModelServiceImpl modelService;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(9527L);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private ModelQuery mockModelQuery() {
        ModelQuery query = org.mockito.Mockito.mock(ModelQuery.class, Answers.RETURNS_SELF);
        when(repositoryService.createModelQuery()).thenReturn(query);
        return query;
    }

    @Test
    @DisplayName("create：desc 非空时写入 description")
    void createWithDesc() {
        ModelQuery query = mockModelQuery();
        when(query.count()).thenReturn(0L);
        Model model = org.mockito.Mockito.mock(Model.class);
        when(model.getVersion()).thenReturn(1);
        when(model.getId()).thenReturn("m1");
        when(repositoryService.newModel()).thenReturn(model);

        modelService.create("name", "key", "desc", "category");

        verify(model).setKey("key");
        verify(model).setName("name");
        verify(model).setCategory("category");
        verify(model).setVersion(1);
        verify(model).setTenantId("9527");
        verify(repositoryService).saveModel(model);
        verify(repositoryService).addModelEditorSource(eq("m1"), any(byte[].class));
    }

    @Test
    @DisplayName("create：desc 为 null 时写入空字符串")
    void createWithNullDesc() {
        ModelQuery query = mockModelQuery();
        when(query.count()).thenReturn(4L);
        Model model = org.mockito.Mockito.mock(Model.class);
        when(model.getVersion()).thenReturn(5);
        when(repositoryService.newModel()).thenReturn(model);

        modelService.create("name", "key", null, "category");

        // 版本号 = 已有数量 + 1
        verify(model).setVersion(5);
        verify(repositoryService).saveModel(model);
    }

    @Test
    @DisplayName("getModels：带名称过滤")
    void getModelsWithName() {
        ModelQuery query = mockModelQuery();
        when(query.count()).thenReturn(1L);
        Model model = org.mockito.Mockito.mock(Model.class);
        when(query.listPage(0, 10)).thenReturn(List.of(model));

        GetModelsInput input = new GetModelsInput();
        input.setName("abc");
        input.setPageNumber(1);
        input.setPageSize(10);

        PagedResultDto<ModelDto> result = modelService.getModels(input);

        verify(query).modelNameLike("abc");
        assertThat(result.getTotalCount()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
    }

    @Test
    @DisplayName("getModels：名称空时不添加过滤条件")
    void getModelsWithoutName() {
        ModelQuery query = mockModelQuery();
        when(query.count()).thenReturn(0L);
        when(query.listPage(10, 10)).thenReturn(List.of());

        GetModelsInput input = new GetModelsInput();
        input.setName("");
        input.setPageNumber(2);
        input.setPageSize(10);

        PagedResultDto<ModelDto> result = modelService.getModels(input);

        verify(query, never()).modelNameLike(anyString());
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    @DisplayName("removeModelById：委托删除并返回 true")
    void removeModelById() {
        assertThat(modelService.removeModelById("m1")).isTrue();

        verify(repositoryService).deleteModel("m1");
    }

    @Test
    @DisplayName("rollBackByKey：版本不存在时抛出业务异常")
    void rollBackByKeyNotFound() {
        ModelQuery query = mockModelQuery();
        when(query.list()).thenReturn(List.of());

        assertThatThrownBy(() -> modelService.rollBackByKey("key", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数据已被他人修改");
    }

    @Test
    @DisplayName("rollBackByKey：命中版本时删除该模型")
    void rollBackByKeyFound() {
        ModelQuery query = mockModelQuery();
        Model model = org.mockito.Mockito.mock(Model.class);
        when(model.getId()).thenReturn("m1");
        when(query.list()).thenReturn(List.of(model));

        assertThat(modelService.rollBackByKey("key", 1)).isTrue();

        verify(repositoryService).deleteModel("m1");
    }

    @Test
    @DisplayName("removeModelByKey：删除该 key 下所有版本")
    void removeModelByKey() {
        ModelQuery query = mockModelQuery();
        Model m1 = org.mockito.Mockito.mock(Model.class);
        Model m2 = org.mockito.Mockito.mock(Model.class);
        when(m1.getId()).thenReturn("m1");
        when(m2.getId()).thenReturn("m2");
        when(query.list()).thenReturn(List.of(m1, m2));

        assertThat(modelService.removeModelByKey("key")).isTrue();

        verify(repositoryService).deleteModel("m1");
        verify(repositoryService).deleteModel("m2");
    }

    @Test
    @DisplayName("deploy：模型不存在时抛出业务异常")
    void deployModelNotFound() {
        when(repositoryService.getModel("m1")).thenReturn(null);

        assertThatThrownBy(() -> modelService.deploy("m1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程模型不存在");
    }

    @Test
    @DisplayName("deploy：名称已包含 .bpmn20.xml 后缀时不再追加，并设置流程分类")
    void deployWithSuffixAlreadyPresent() {
        Model model = org.mockito.Mockito.mock(Model.class);
        when(model.getId()).thenReturn("m1");
        when(model.getName()).thenReturn("demo.bpmn20.xml");
        when(repositoryService.getModel("m1")).thenReturn(model);
        when(repositoryService.getModelEditorSource("m1")).thenReturn(BPMN_XML.getBytes(StandardCharsets.UTF_8));

        DeploymentBuilder builder = org.mockito.Mockito.mock(DeploymentBuilder.class, Answers.RETURNS_SELF);
        Deployment deployment = org.mockito.Mockito.mock(Deployment.class);
        when(deployment.getId()).thenReturn("dep1");
        when(builder.deploy()).thenReturn(deployment);
        when(repositoryService.createDeployment()).thenReturn(builder);

        ProcessDefinitionQuery pdQuery = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        ProcessDefinition pd = org.mockito.Mockito.mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn("pd1");
        when(pdQuery.list()).thenReturn(List.of(pd));
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);

        assertThat(modelService.deploy("m1")).isTrue();

        verify(builder).addBpmnModel(eq("demo.bpmn20.xml"), any());
        verify(builder).tenantId("9527");
        verify(repositoryService).setProcessDefinitionCategory(eq("pd1"), any());
    }

    @Test
    @DisplayName("deploy：名称无后缀时自动追加 .bpmn20.xml")
    void deployWithoutSuffix() {
        Model model = org.mockito.Mockito.mock(Model.class);
        when(model.getId()).thenReturn("m1");
        when(model.getName()).thenReturn("demo");
        when(repositoryService.getModel("m1")).thenReturn(model);
        when(repositoryService.getModelEditorSource("m1")).thenReturn(BPMN_XML.getBytes(StandardCharsets.UTF_8));

        DeploymentBuilder builder = org.mockito.Mockito.mock(DeploymentBuilder.class, Answers.RETURNS_SELF);
        Deployment deployment = org.mockito.Mockito.mock(Deployment.class);
        when(deployment.getId()).thenReturn("dep1");
        when(builder.deploy()).thenReturn(deployment);
        when(repositoryService.createDeployment()).thenReturn(builder);

        ProcessDefinitionQuery pdQuery = org.mockito.Mockito.mock(ProcessDefinitionQuery.class, Answers.RETURNS_SELF);
        when(pdQuery.list()).thenReturn(List.of());
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(pdQuery);

        modelService.deploy("m1");

        verify(builder).addBpmnModel(eq("demo.bpmn20.xml"), any());
        verify(repositoryService, never()).setProcessDefinitionCategory(anyString(), anyString());
    }

    @Test
    @DisplayName("getSvgXmlByModelId：模型不存在抛出业务异常")
    void getSvgXmlByModelIdNotFound() {
        when(repositoryService.getModel("m1")).thenReturn(null);

        assertThatThrownBy(() -> modelService.getSvgXmlByModelId("m1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程信息不存在1!");
    }

    @Test
    @DisplayName("getSvgXmlByModelId：返回编辑器资源")
    void getSvgXmlByModelId() {
        Model model = org.mockito.Mockito.mock(Model.class);
        when(model.getId()).thenReturn("m1");
        when(repositoryService.getModel("m1")).thenReturn(model);
        when(repositoryService.getModelEditorSource("m1")).thenReturn("xml".getBytes(StandardCharsets.UTF_8));

        assertThat(modelService.getSvgXmlByModelId("m1")).isEqualTo("xml".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("getSvgXmlByKey：模型不存在抛出业务异常")
    void getSvgXmlByKeyNotFound() {
        ModelQuery query = mockModelQuery();
        when(query.singleResult()).thenReturn(null);

        assertThatThrownBy(() -> modelService.getSvgXmlByKey("key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("流程信息不存在!");
    }

    @Test
    @DisplayName("getSvgXmlByKey：返回最新版本模型资源")
    void getSvgXmlByKey() {
        ModelQuery query = mockModelQuery();
        Model model = org.mockito.Mockito.mock(Model.class);
        when(model.getId()).thenReturn("m1");
        when(query.singleResult()).thenReturn(model);
        when(repositoryService.getModelEditorSource("m1")).thenReturn("xml".getBytes(StandardCharsets.UTF_8));

        assertThat(modelService.getSvgXmlByKey("key")).isEqualTo("xml".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("convertInputStreamToModel：正常 BPMN 返回模型 id")
    void convertInputStreamToModel() {
        ModelQuery query = mockModelQuery();
        when(query.count()).thenReturn(2L);
        Model modelData = org.mockito.Mockito.mock(Model.class);
        when(modelData.getId()).thenReturn("newId");
        when(repositoryService.newModel()).thenReturn(modelData);

        InputStream is = new ByteArrayInputStream(BPMN_XML.getBytes(StandardCharsets.UTF_8));

        assertThat(modelService.convertInputStreamToModel(is)).isEqualTo("newId");

        verify(modelData).setKey("myProcess");
        verify(modelData).setName("My Process");
        verify(modelData).setVersion(3);
        verify(modelData).setTenantId("9527");
        verify(repositoryService).saveModel(modelData);
        verify(repositoryService).addModelEditorSource(eq("newId"), any(byte[].class));
    }

    @Test
    @DisplayName("convertInputStreamToModel：BPMN 不含主流程时抛出异常")
    void convertInputStreamToModelWithoutMainProcess() {
        InputStream is = new ByteArrayInputStream(EMPTY_DEFINITIONS_XML.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> modelService.convertInputStreamToModel(is))
                .hasMessageContaining("模板文件可能存在问题");
    }

    @Test
    @DisplayName("convertInputStreamToModel：主流程缺少 id 时抛出异常")
    void convertInputStreamToModelWithoutProcessId() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n"
                + "             targetNamespace=\"http://flowable.org/processdef\">\n"
                + "  <process name=\"NoIdProcess\">\n"
                + "    <startEvent id=\"start\"/>\n"
                + "  </process>\n"
                + "</definitions>";
        InputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> modelService.convertInputStreamToModel(is))
                .hasMessageContaining("模板文件可能存在问题");
    }
}
