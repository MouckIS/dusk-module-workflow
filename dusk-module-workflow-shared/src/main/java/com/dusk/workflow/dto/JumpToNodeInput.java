package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 节点跳转输入参数
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Getter
@Setter
public class JumpToNodeInput implements Serializable {
    /**
     * 流程实例ID
     */
    @Schema(description = "流程实例ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String processInstanceId;

    /**
     * 目标任务节点定义Key
     */
    @Schema(description = "目标任务节点定义Key", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetTaskDefinitionKey;

    /**
     * 跳转备注
     */
    @Schema(description = "跳转备注")
    private String comment;

    /**
     * 流程变量
     */
    @Schema(description = "流程变量")
    private Map<String, Object> variables;

    /**
     * 业务数据
     */
    @Schema(description = "业务数据")
    private Map<String, Object> businessData;
}

