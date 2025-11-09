package com.dusk.module.workflow.service;

import com.dusk.common.core.dto.PagedResultDto;
import com.dusk.module.workflow.dto.GetProcessesInput;
import com.dusk.module.workflow.dto.ProcessDefDto;

/**
 * @author kefuming
 * @date 2020-07-22 11:23
 */
public interface IProcessService {

    /**
     * 移除指定id的流程实例
     *
     * @param deploymentId
     * @return
     */
    boolean removeProcIns(String deploymentId);

    /**
     * 分页获取流程
     *
     * @param input
     * @return
     */
    PagedResultDto<ProcessDefDto> getProcesses(GetProcessesInput input);


    /**
     * 获取流程的图片定义或者xml定义
     *
     * @param proInsId 流程id
     * @param resType  类型
     * @return
     */
    byte[] getResource(String proInsId, String resType);
}
