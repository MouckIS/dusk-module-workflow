package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 抄送输入参数
 *
 * @author kefuming
 * @date 2026-02-28
 */
@Getter
@Setter
public class CarbonCopyInput implements Serializable {
    /**
     * 流程实例ID
     */
    @Schema(description = "流程实例ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String processInstanceId;
    /**
     * 任务ID
     */
    @Schema(description = "任务ID")
    private String taskId;
    /**
     * 抄送的用户ID列表
     */
    @Schema(description = "抄送的用户ID列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> ccUserIds;
    /**
     * 抄送标题
     */
    @Schema(description = "抄送标题")
    private String title;
    /**
     * 抄送内容
     */
    @Schema(description = "抄送内容")
    private String content;
    /**
     * 业务主键
     */
    @Schema(description = "业务主键")
    private String businessKey;
    /**
     * 业务类型
     */
    @Schema(description = "业务类型")
    private String businessType;
}

