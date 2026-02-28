package com.dusk.workflow.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * 抄送输入参数
 * <p>
 * 用于 {@code sendCarbonCopy()} RPC/REST 接口，主动向指定用户发送抄送站内信。
 * 抄送通过 {@link com.dusk.workflow.service.INotificationRpcService} 发送站内信通知，
 * 同时发布 {@code TASK_CC} 事件到 MQ。
 * </p>
 * <p>
 * 也可在 {@code genericSubmit()} / {@code genericApproval()} 中通过设置 ccUserIds 字段自动触发抄送。
 * </p>
 *
 * @author kefuming
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

