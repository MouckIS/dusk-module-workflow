package com.dusk.module.workflow.dto;

import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty("任务id")
    private String taskId;
    @ApiModelProperty("是否自动计算")
    private boolean autoCalculate;
    @ApiModelProperty("用于计算的动态变量")
    private Map<String, Object> variables;
}
