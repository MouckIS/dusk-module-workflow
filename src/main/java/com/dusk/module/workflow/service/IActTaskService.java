package com.dusk.module.workflow.service;

import com.dusk.common.framework.dto.PagedAndSortedInputDto;
import com.dusk.common.framework.dto.PagedResultDto;
import com.dusk.common.module.activiti.dto.WorkflowTaskDto;

/**
 * @author kefuming
 * @date 2020-07-22 14:11
 */
public interface IActTaskService {
    /**
     * 分页获取任务
     *
     * @param input
     * @return
     */
    PagedResultDto<WorkflowTaskDto> getTasks(PagedAndSortedInputDto input);

    /**
     * 追踪图片节点
     *
     * @param id
     * @return
     */
    byte[] viewByTaskId(String id);
}
