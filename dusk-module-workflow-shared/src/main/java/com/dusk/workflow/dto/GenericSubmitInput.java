package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 通用流程提交入参
 * <p>
 * 继承自 {@link WorkflowProcessDto}，用于 {@code genericSubmit()} 接口。
 * 相比直接调用 {@code startProcess()}，增加了以下能力：
 * <ul>
 *   <li>{@code completeFirst} —— 是否自动完成第一个节点（发起人节点）</li>
 *   <li>{@code ccUserIds} —— 提交时抄送指定用户（通过站内信通知）</li>
 *   <li>自动触发 {@link com.dusk.workflow.service.IWorkflowSubmitProcessor} 的前/后置处理器</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 * @see com.dusk.workflow.service.IWorkflowSubmitProcessor
 */
@Getter
@Setter
public class GenericSubmitInput extends WorkflowProcessDto {

    /**
     * 是否提交并完成第一个节点（自动审批掉发起节点）
     */
    @Schema(description = "是否提交并完成第一个节点")
    private boolean completeFirst = true;

    /**
     * 审批备注
     */
    @Schema(description = "审批备注")
    private String comment;

    /**
     * task局部变量
     */
    @Schema(description = "task局部变量")
    private Map<String, Object> localVariables;

    /**
     * 抄送用户ID列表，多个用逗号分隔
     */
    @Schema(description = "抄送用户ID列表，多个用逗号分隔")
    private String ccUserIds;
}

