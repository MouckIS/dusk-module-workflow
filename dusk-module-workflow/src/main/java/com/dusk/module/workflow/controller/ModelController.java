package com.dusk.module.workflow.controller;

import cn.hutool.core.io.IoUtil;
import com.dusk.common.core.annotation.Authorize;
import com.dusk.common.core.controller.CruxBaseController;
import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.module.workflow.authorization.ActivitiAuthProvider;
import com.dusk.module.workflow.dto.GetModelsInput;
import com.dusk.module.workflow.dto.ModelDto;
import com.dusk.module.workflow.service.IModelService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;

/**
 * @author kefuming
 * @date 2020-07-22 10:49
 */
@RestController
@RequestMapping("/model")
@Tag(name = "模型管理", description = "Model")
public class ModelController extends CruxBaseController {
    @Autowired
    IModelService modelService;


    @Operation(summary = "分页获取模型数据")
    @GetMapping
    public PagedResultDto<ModelDto> getModels(GetModelsInput input) {
        return modelService.getModels(input);
    }

//    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_DELETE)
//    @Operation(summary = "删除指定模型")
//    @DeleteMapping("/removeModelById/{id}")
//    public void removeModelById(@PathVariable("id") String id) {
//        modelService.removeModelById(id);
//    }

    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_DELETE)
    @Operation(summary = "回退指定模型的版本")
    @DeleteMapping("/rollBackByKey/{key}/{version}")
    public boolean rollBackByKey(@PathVariable("key") String key, @PathVariable("version") Integer version) {
        return modelService.rollBackByKey(key, version);
    }


    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_DELETE)
    @Operation(summary = "根据key删除指定模型，删除所有版本")
    @DeleteMapping("/removeModelByKey/{key}")
    public boolean removeModelByKey(@PathVariable("key") String key) {
        return modelService.removeModelByKey(key);
    }

    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_DEPLOY)
    @Operation(summary = "发布流程")
    @PostMapping("/deploy/{id}")
    public void deploy(@PathVariable("id") String id) {
        modelService.deploy(id);
    }


    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_SAVE)
    @GetMapping(value = "/getModelSvg/{modelId}")
    @Operation(summary = "获取指定模型svg")
    @SneakyThrows
    public void getModelSvg(@PathVariable(value = "modelId") String modelId, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        ServletOutputStream os = response.getOutputStream();
        byte[] data = modelService.getSvgXmlByModelId(modelId);
        os.write(data);
        os.flush();
        os.close();
    }

    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_SAVE)
    @GetMapping(value = "/getModelSvgByKey/{key}")
    @Operation(summary = "根据key获取指定模型svg")
    @SneakyThrows
    public void getModelSvgByKey(@PathVariable(value = "key") String key, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_XML_VALUE);
        ServletOutputStream os = response.getOutputStream();
        byte[] data = modelService.getSvgXmlByKey(key);
        os.write(data);
        os.flush();
        os.close();
    }

    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_SAVE)
    @Operation(summary = "导入模型")
    @PostMapping("/importModelBySvg")
    public void importModelBySvg(@RequestParam("file") MultipartFile file) throws Exception {
        modelService.convertInputStreamToModel(file.getInputStream());
    }

    @Authorize(ActivitiAuthProvider.PAGES_ACTIVITI_MODEL_SAVE)
    @PostMapping("/save")
    @Operation(summary = "保存模型")
    public String save(@RequestBody String svgXml) throws Exception {
        ByteArrayInputStream is = IoUtil.toStream(svgXml.getBytes());
        return modelService.convertInputStreamToModel(is);
    }
}
