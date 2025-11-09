package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * @author kefuming
 * @date 2021-04-28 9:24
 */
@Getter
@Setter
public class WorkflowCompleteTaskInputDto implements Serializable {
    @Schema(description = "流程变量")
    protected Map<String, Object> variables = new HashMap<>();

    @Schema(description = "流程局部变量")
    protected Map<String, Object> localVariables;

    @Schema(description = "是否通过, 默认true")
    protected boolean pass = true;

    @Schema(description = "审批备注")
    protected String comment;
}
