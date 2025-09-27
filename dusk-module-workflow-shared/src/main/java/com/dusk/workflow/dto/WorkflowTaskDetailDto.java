package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * @author kefuming
 * @date 2021-04-30 14:36
 */
@Getter
@Setter
public class WorkflowTaskDetailDto extends WorkflowTaskDto {
    /**
     * 角色列表
     */
    private String roleNames;

    /**
     * 用户名
     */
    private String userNames;
}
