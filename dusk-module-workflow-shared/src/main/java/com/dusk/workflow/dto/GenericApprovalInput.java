package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用流程审批入参
 * <p>
 * 继承自 {@link CompleteTaskInputDto}，用于 {@code genericApproval()} 接口。
 * 相比直接调用 {@code completeTask()}，增加了以下能力：
 * <ul>
 *   <li>{@code ccUserIds} —— 审批时抄送指定用户（通过站内信通知）</li>
 *   <li>自动触发 {@link com.dusk.workflow.service.IWorkflowApprovalProcessor} 的前/后置处理器</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 * @see com.dusk.workflow.service.IWorkflowApprovalProcessor
 */
@Getter
@Setter
public class GenericApprovalInput extends CompleteTaskInputDto {

    /**
     * 抄送用户ID列表，多个用逗号分隔
     */
    @Schema(description = "抄送用户ID列表，多个用逗号分隔")
    private String ccUserIds;
}

