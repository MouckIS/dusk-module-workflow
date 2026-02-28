package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 通用流程提交入参
 *
 * @author kefuming
 * @date 2026-02-28
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

