package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * @author kefuming
 * @date 2020-11-16 14:33
 */
@Getter
@Setter
public class StartProcessInputDto extends WorkflowProcessDto {
    /**
     * 审批备注
     */
    private String comment;
    /**
     * task局部变量
     */
    private Map<String, Object> localVariables;

}
