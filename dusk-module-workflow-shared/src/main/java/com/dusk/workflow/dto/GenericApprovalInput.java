package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * 通用流程审批入参
 *
 * @author kefuming
 * @date 2026-02-28
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

