package com.dusk.module.workflow.transaction;

import lombok.Data;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 工作流事务消息实体
 * <p>
 * 用于实现本地消息表模式，保证事件消息的最终一致性。
 * 消息与业务操作在同一事务中保存，由定时任务异步发送。
 * </p>
 *
 * @author kefuming
 */
@Data
@Entity
@Table(name = "workflow_transactional_message")
public class TransactionalMessage {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 消息唯一ID（UUID）
     */
    private String messageId;

    /**
     * 事件类型
     */
    private String eventType;

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
     * 消息体JSON
     */
    private String payload;

    /**
     * 消息状态：PENDING/SENT/FAILED
     */
    private String status;

    /**
     * 已重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetry;

    /**
     * 下次重试时间
     */
    private LocalDateTime nextRetryTime;

    /**
     * 最后一次错误信息
     */
    private String errorMessage;

    /**
     * 分布式追踪ID
     */
    private String traceId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 租户ID
     */
    private Long tenantId;

    // 状态常量
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
}
