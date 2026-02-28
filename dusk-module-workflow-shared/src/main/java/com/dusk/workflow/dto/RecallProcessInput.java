package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 撤回流程输入参数
 * <p>
 * 用于 {@code recallProcess()} RPC/REST 接口。
 * 撤回操作会将流程回退到上一个已完成的节点，同时：
 * <ul>
 *   <li>同步待办到待办中心</li>
 *   <li>触发 {@link com.dusk.workflow.service.IWorkflowRecallHandler#onRecall} 业务回调</li>
 *   <li>发布 {@code PROCESS_RECALLED} MQ 事件</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 * @see com.dusk.workflow.service.IWorkflowRecallHandler
 */
@Getter
@Setter
public class RecallProcessInput implements Serializable {
    /**
     * 流程实例ID
     */
    @Schema(description = "流程实例ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String processInstanceId;

    /**
     * 撤回备注
     */
    @Schema(description = "撤回备注")
    private String comment;

    /**
     * 业务数据
     */
    @Schema(description = "业务数据")
    private Map<String, Object> businessData;
}

