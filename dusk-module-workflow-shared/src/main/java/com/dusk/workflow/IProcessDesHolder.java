package com.dusk.workflow;

import com.dusk.workflow.dto.ProcessDesOutPutDto;

/**
 * 工作流业务返回的dto可以实现该接口
 * FlowUtils工具填充流程相关的信息
 *
 * @author kefuming
 * @date 2021-01-12 10:08
 */
public interface IProcessDesHolder {

    /**
     * 获取流程实例id
     *
     * @return
     */
    String getProcessInstanceId();

    ProcessDesOutPutDto getProcessDes();

    void setProcessDes(ProcessDesOutPutDto processDes);
}
