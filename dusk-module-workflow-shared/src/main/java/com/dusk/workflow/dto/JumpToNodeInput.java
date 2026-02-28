package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 节点跳转输入参数
 * <p>
 * 用于 {@code jumpToNode()} RPC/REST 接口。
 * 允许将流程从当前活动节点直接跳转到流程定义中的任意目标节点。
 * </p>
 * <p>
 * 跳转原理：通过动态替换 Activiti ActivityImpl 的出线实现，
 * 复用 {@code gotoAssignActivity()} 方法，支持多任务并行场景。
 * 跳转完成后会自动同步待办并发布 {@code TASK_JUMPED} 事件。
 * </p>
 * <p>
 * 注意：此为管理级功能，不做审批权限校验。
 * </p>
 *
 * @author kefuming
 */
@Getter
@Setter
public class JumpToNodeInput implements Serializable {
    /**
     * 流程实例ID
     */
    @Schema(description = "流程实例ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String processInstanceId;

    /**
     * 目标任务节点定义Key
     */
    @Schema(description = "目标任务节点定义Key", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetTaskDefinitionKey;

    /**
     * 跳转备注
     */
    @Schema(description = "跳转备注")
    private String comment;

    /**
     * 流程变量
     */
    @Schema(description = "流程变量")
    private Map<String, Object> variables;

    /**
     * 业务数据
     */
    @Schema(description = "业务数据")
    private Map<String, Object> businessData;
}

