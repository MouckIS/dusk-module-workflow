package com.dusk.module.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author kefuming
 * @date 2020-12-10 8:23
 */
@Getter
@Setter
public class GetRelateTaskInput {
    @Schema(description = "任务id")
    private String taskId;
    @Schema(description = "是否自动计算")
    private boolean autoCalculate;
    @Schema(description = "用于计算的动态变量")
    private Map<String, Object> variables;
}
