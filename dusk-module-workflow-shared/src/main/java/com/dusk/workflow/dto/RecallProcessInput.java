package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 撤回流程输入参数
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Getter
@Setter
public class RecallProcessInput implements Serializable {
    /**
     * 流程实例ID
     */
    @Schema(description = "流程实例ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String processInstanceId;

    /**
     * 撤回备注
     */
    @Schema(description = "撤回备注")
    private String comment;

    /**
     * 业务数据
     */
    @Schema(description = "业务数据")
    private Map<String, Object> businessData;
}

