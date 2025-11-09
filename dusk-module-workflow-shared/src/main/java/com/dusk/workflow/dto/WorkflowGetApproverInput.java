package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author kefuming
 * @date 2021-08-05 15:50
 */
@Getter
@Setter
public class WorkflowGetApproverInput implements Serializable {
    @Schema(description = "业务id")
    @NotNull(message = "业务id不能为空")
    private Long businessId;

    @Schema(description = "流程实例id")
    private String processInstanceId;

    @Schema(description = "流程节点的formKey")
    private String formKey;
}
