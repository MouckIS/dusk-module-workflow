package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author kefuming
 * @date 2020-12-08 11:22
 */
@Getter
@Setter
public class ProcessDesOutPutDto implements Serializable {
    /**
     * 当前节点的描述
     */
    private String description;

    /**
     * 流程id
     */
    private String processInstanceId;

    /**
     * 当前存在有权限审批的节点
     */
    private boolean hasPermission;

    /**
     * 当前userTask的name
     */
    private String taskName;

    /**
     * 当前userTask的formKey
     */
    private String formKey;

    /**
     * 流程变量
     */
    private Map<String, Object> variables = new HashMap<>();

    /**
     * 流程是否结束
     */
    private boolean isFinished;
}
