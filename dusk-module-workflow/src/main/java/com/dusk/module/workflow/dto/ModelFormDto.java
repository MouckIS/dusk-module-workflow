package com.dusk.module.workflow.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author kefuming
 * @date 2020-07-22 10:47
 */
@Data
public class ModelFormDto {
    private String category;
    @NotBlank(message = "流程名称不能为空")
    private String name;
    @NotBlank(message = "流程的key不能为空")
    private String key;
    private String desc;
}
