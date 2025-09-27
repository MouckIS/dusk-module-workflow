package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * @author kefuming
 * @date 2020-11-16 14:27
 */
@Getter
@Setter
public class StartProcessOutDto implements Serializable {
    /**
     * 流程id
     */
    private String processInstanceId;

    /**
     * 审批任务信息
     */
    private List<WorkflowTaskDto> taskInfos;
}
