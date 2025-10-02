package com.dusk.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * @author kefuming
 * @date 2021-08-05 15:50
 */
@Getter
@Setter
public class WorkflowGetApproverInput implements Serializable {
    @ApiModelProperty("业务id")
    @NotNull(message = "业务id不能为空")
    private Long businessId;

    @ApiModelProperty("流程实例id")
    private String processInstanceId;

    @ApiModelProperty("流程节点的formKey")
    private String formKey;
}
