package com.dusk.module.workflow.service;

import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.module.workflow.dto.GetModelsInput;
import com.dusk.module.workflow.dto.ModelDto;

import java.io.InputStream;

/**
 * @author kefuming
 * @date 2020-07-22 10:00
 */
public interface IModelService {
    /**
     * 创建流程
     *
     * @param name
     * @param key
     * @param desc
     * @param category
     */
    void create(String name, String key, String desc, String category);

    /**
     * 分页获取模型清单
     *
     * @param input
     * @return
     */
    PagedResultDto<ModelDto> getModels(GetModelsInput input);

    /**
     * 删除流程
     *
     * @param id
     * @return
     */
    boolean removeModelById(String id);


    /**
     * 回退指定key的流程的版本
     *
     * @param key
     * @param version
     * @return
     */
    boolean rollBackByKey(String key, Integer version);


    /**
     * 根据流程的key删除所有版本的流程
     *
     * @param key
     * @return
     */
    boolean removeModelByKey(String key);

    /**
     * 部署流程
     *
     * @param id
     * @return
     */
    boolean deploy(String id);

    /**
     * 根据模型id获取流程svg字节
     *
     * @param modelId
     * @return
     */
    byte[] getSvgXmlByModelId(String modelId);

    /**
     * 根据模型key获取流程svg字节
     *
     * @param key
     * @return
     */
    byte[] getSvgXmlByKey(String key);

    /**
     * 基于svg的xml文件导入保存模型
     *
     * @param is 文件流
     */
    String convertInputStreamToModel(InputStream is);
}
