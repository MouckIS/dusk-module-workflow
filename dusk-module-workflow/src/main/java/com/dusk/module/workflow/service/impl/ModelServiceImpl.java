package com.dusk.module.workflow.service.impl;

import cn.hutool.core.util.StrUtil;
import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.common.core.exception.BusinessException;
import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.common.core.utils.MapperUtil;
import com.dusk.module.workflow.dto.GetModelsInput;
import com.dusk.module.workflow.dto.ModelDto;
import com.dusk.module.workflow.mapper.WorkflowMapper;
import com.dusk.module.workflow.service.IModelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.editor.constants.ModelDataJsonConstants;
import org.activiti.editor.language.json.converter.BpmnJsonConverter;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ModelQuery;
import org.activiti.engine.repository.ProcessDefinition;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

/**
 * @author kefuming
 * @date 2020-07-22 10:20
 */
@Service
@Slf4j
@Transactional
public class ModelServiceImpl implements IModelService {

    private final WorkflowMapper mapper = WorkflowMapper.INSTANCE;
    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private ObjectMapper objectMapper;

    @SneakyThrows
    @Override
    public void create(String name, String key, String desc, String category) {
        ObjectNode editorNode = objectMapper.createObjectNode();
        editorNode.put("id", "canvas");
        editorNode.put("resourceId", "canvas");

        ObjectNode properties = objectMapper.createObjectNode();
        properties.put("process_author", "orion");
        properties.put("process_id", key);
        properties.put("name", name);
        properties.put("documentation", desc == null ? "" : desc);
        editorNode.set("properties", properties);

        ObjectNode stencilset = objectMapper.createObjectNode();
        stencilset.put("namespace", "http://b3mn.org/stencilset/bpmn2.0#");
        editorNode.set("stencilset", stencilset);

        Model model = repositoryService.newModel();
        model.setKey(key);
        model.setName(name);
        model.setCategory(category);
        model.setVersion(Integer.parseInt(
                String.valueOf(repositoryService.createModelQuery().modelTenantId(String.valueOf(TenantContextHolder.getTenantId()))
                        .modelKey(model.getKey()).count() + 1)));

        ObjectNode modelObjectNode = objectMapper.createObjectNode();
        modelObjectNode.put(ModelDataJsonConstants.MODEL_NAME, name);
        modelObjectNode.put(ModelDataJsonConstants.MODEL_REVISION, model.getVersion());
        modelObjectNode.put(ModelDataJsonConstants.MODEL_DESCRIPTION, desc);
        model.setMetaInfo(modelObjectNode.toString());
        model.setTenantId(TenantContextHolder.getTenantId().toString());

        repositoryService.saveModel(model);
        repositoryService.addModelEditorSource(model.getId(), editorNode.toString().getBytes("utf-8"));
    }

    @Override
    public PagedResultDto<ModelDto> getModels(GetModelsInput input) {

        ModelQuery modelQuery = repositoryService.createModelQuery()
                .modelTenantId(String.valueOf(TenantContextHolder.getTenantId()))
                .latestVersion().orderByLastUpdateTime().desc();
        if (StringUtils.isNotEmpty(input.getName())) {
            modelQuery.modelNameLike(input.getName());
        }
        long total = modelQuery.count();
        List<Model> models = modelQuery.listPage((input.getPageNumber() - 1) * input.getPageSize(), input.getPageSize());
        return new PagedResultDto<>(total, MapperUtil.mapList(models, mapper::toDto));
    }

    @Override
    public boolean removeModelById(String id) {
        repositoryService.deleteModel(id);
        return true;
    }

    @Override
    public boolean rollBackByKey(String key, Integer version) {
        List<Model> list = repositoryService.createModelQuery()
                .modelKey(key)
                .modelTenantId(String.valueOf(TenantContextHolder.getTenantId()))
                .modelVersion(version)
                .latestVersion().list();
        if (list.size() == 0) {
            throw new BusinessException("数据已被他人修改，请刷新后重试！");
        } else {
            repositoryService.deleteModel(list.get(0).getId());
        }
        return true;
    }

    @Override
    public boolean removeModelByKey(String key) {
        List<Model> list = repositoryService.createModelQuery().modelTenantId(String.valueOf(TenantContextHolder.getTenantId())).modelKey(key).list();
        for (Model item : list) {
            repositoryService.deleteModel(item.getId());
        }
        return true;
    }

    @SneakyThrows
    @Override
    public boolean deploy(String id) {
        // 获取模型
        Model model = repositoryService.getModel(id);
        ObjectNode objectNode = (ObjectNode) new ObjectMapper().readTree(repositoryService.getModelEditorSource(model.getId()));
        BpmnModel bpmnModel = new BpmnJsonConverter().convertToBpmnModel(objectNode);

        String processName = model.getName();
        if (!StrUtil.endWithIgnoreCase(processName, ".bpmn20.xml")) {
            processName += ".bpmn20.xml";
        }
        // 部署流程
        Deployment deployment = repositoryService
                .createDeployment().name(model.getName())
                .addBpmnModel(processName, bpmnModel)
                .tenantId(String.valueOf(TenantContextHolder.getTenantId()))
                .deploy();

        // 设置流程分类
        List<ProcessDefinition> list = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .list();

        list.stream().forEach(processDefinition ->
                repositoryService.setProcessDefinitionCategory(processDefinition.getId(), model.getCategory()));

        return true;
    }

    @Override
    @SneakyThrows
    public byte[] getSvgXmlByModelId(String modelId) {
        Model model = repositoryService.getModel(modelId);
        if (model == null) {
            throw new BusinessException("流程信息不存在!");
        }
        ObjectNode objectNode = (ObjectNode) new ObjectMapper().readTree(repositoryService.getModelEditorSource(model.getId()));
        BpmnModel bpmnModel = new BpmnJsonConverter().convertToBpmnModel(objectNode);
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(bpmnModel);//
        return bpmnBytes;
    }

    @Override
    @SneakyThrows
    public byte[] getSvgXmlByKey(String key) {
        Model model = repositoryService.createModelQuery().modelTenantId(String.valueOf(TenantContextHolder.getTenantId())).modelKey(key).latestVersion().singleResult();
        if (model == null) {
            throw new BusinessException("流程信息不存在!");
        }
        ObjectNode objectNode = (ObjectNode) new ObjectMapper().readTree(repositoryService.getModelEditorSource(model.getId()));
        BpmnModel bpmnModel = new BpmnJsonConverter().convertToBpmnModel(objectNode);
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(bpmnModel);//
        return bpmnBytes;
    }

    @Override
    @SneakyThrows
    public String convertInputStreamToModel(InputStream is) {
        XMLInputFactory xif = XMLInputFactory.newInstance();
        InputStreamReader isr = null;
        XMLStreamReader xtr = null;
        isr = new InputStreamReader(is, "utf-8");
        xtr = xif.createXMLStreamReader(isr);
        BpmnModel bpmnModel = new BpmnXMLConverter().convertToBpmnModel(xtr);
        // 处理异常
        if (bpmnModel.getMainProcess() == null
                || bpmnModel.getMainProcess().getId() == null) {
            throw new Exception("模板文件可能存在问题，请检查后重试！");
        }

        ObjectNode modelNode = new BpmnJsonConverter().convertToJson(bpmnModel);
        Model modelData = repositoryService.newModel();
        modelData.setKey(bpmnModel.getMainProcess().getId());
        modelData.setName(bpmnModel.getMainProcess().getName());
        modelData.setVersion(Integer.parseInt(
                String.valueOf(repositoryService.createModelQuery().modelTenantId(String.valueOf(TenantContextHolder.getTenantId()))
                        .modelKey(modelData.getKey()).count() + 1)));

        ObjectNode modelObjectNode = new ObjectMapper().createObjectNode();
        modelObjectNode.put(ModelDataJsonConstants.MODEL_NAME, modelData.getName());
        modelObjectNode.put(ModelDataJsonConstants.MODEL_REVISION, modelData.getVersion());
        modelData.setMetaInfo(modelObjectNode.toString());
        modelData.setTenantId(String.valueOf(TenantContextHolder.getTenantId()));

        repositoryService.saveModel(modelData);
        repositoryService.addModelEditorSource(modelData.getId(), modelNode.toString().getBytes("utf-8"));

        xtr.close();
        isr.close();
        is.close();
        return modelData.getId();
    }
}
