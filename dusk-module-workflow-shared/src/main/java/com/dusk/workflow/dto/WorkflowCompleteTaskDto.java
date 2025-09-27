package com.dusk.workflow.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * @author kefuming
 * @date 2020-07-22 15:38
 */
@Getter
@Setter
public class WorkflowCompleteTaskDto implements Serializable {
    /**
     * 审批备注
     */
    private String comment;
    /**
     * task局部变量(基本没用)
     */
    private Map<String, Object> localVariables;
    /**
     * 流程变量
     */
    private Map<String, Object> variables;

    /**
     * 瞬时变量，仅作用于下一节点，不会保存
     */
    private Map<String, Object> transientVariables;

    /**
     * 业务数据
     */
    private Map<String, Object> businessData;
}
