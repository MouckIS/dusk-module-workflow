package com.dusk.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty("流程变量")
    protected Map<String, Object> variables = new HashMap<>();

    @ApiModelProperty("流程局部变量")
    protected Map<String, Object> localVariables;

    @ApiModelProperty(value = "是否通过, 默认true")
    protected boolean pass = true;

    @ApiModelProperty("审批备注")
    protected String comment;
}
