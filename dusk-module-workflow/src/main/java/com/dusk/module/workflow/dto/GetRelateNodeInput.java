package com.dusk.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author kefuming
 * @date 2022-12-06 10:57
 */
@Getter
@Setter
public class GetRelateNodeInput {
    @Schema(description = "任务id")
    private String taskId;
    @Schema(description = "流程图的key  当 taskId为空的时候 该值必须赋值并且才会返回 startEvent的formKey")
    private String processKey;
    @Schema(description = "是否自动计算")
    private boolean autoCalculate;
    @Schema(description = "用于计算的动态变量")
    private Map<String, Object> variables;
}
