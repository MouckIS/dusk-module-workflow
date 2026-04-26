package com.dusk.module.workflow.log;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流操作日志实体
 *
 * @author kefuming
 */
@Data
@Entity
@Table(name = "workflow_operation_log")
public class WorkflowOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 分布式追踪ID
     */
    private String traceId;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 流程定义Key
     */
    private String processDefinitionKey;

    /**
     * 业务Key
     */
    private String businessKey;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务定义Key
     */
    private String taskDefinitionKey;

    /**
     * 操作类型：SUBMIT/APPROVAL/RECALL/JUMP/CARBON_COPY
     */
    private String operationType;

    /**
     * 操作人ID
     */
    private String operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;

    /**
     * 请求参数JSON
     */
    private String requestJson;

    /**
     * 响应结果JSON
     */
    private String responseJson;

    /**
     * 回调执行结果：SUCCESS/FAILED/TIMEOUT/SKIPPED
     */
    private String callbackResult;

    /**
     * 回调耗时(毫秒)
     */
    private Integer callbackDuration;

    /**
     * 总耗时(毫秒)
     */
    private Integer totalDuration;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 租户ID
     */
    private Long tenantId;
}
