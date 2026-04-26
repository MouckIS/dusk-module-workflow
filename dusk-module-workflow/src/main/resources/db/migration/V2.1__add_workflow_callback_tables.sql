-- 工作流事务消息表
-- 用于保证工作流事件消息的最终一致性
CREATE TABLE IF NOT EXISTS workflow_transactional_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL UNIQUE COMMENT '消息唯一ID',
    event_type VARCHAR(32) NOT NULL COMMENT '事件类型：PROCESS_STARTED/TASK_CREATED/TASK_COMPLETED等',
    process_instance_id VARCHAR(64) COMMENT '流程实例ID',
    process_definition_key VARCHAR(64) COMMENT '流程定义Key',
    business_key VARCHAR(128) COMMENT '业务Key',
    payload TEXT NOT NULL COMMENT '消息体JSON',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '消息状态：PENDING-待发送/SENT-已发送/FAILED-发送失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry INT NOT NULL DEFAULT 5 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    error_message VARCHAR(500) COMMENT '最后一次错误信息',
    trace_id VARCHAR(64) COMMENT '分布式追踪ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT COMMENT '租户ID',
    INDEX idx_status_retry (status, next_retry_time) COMMENT '状态+重试时间索引，用于定时任务查询',
    INDEX idx_process_instance (process_instance_id) COMMENT '流程实例索引',
    INDEX idx_created_at (created_at) COMMENT '创建时间索引，用于历史消息清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流事务消息表';

-- 工作流回调重试表
-- 用于记录失败的回调调用，支持异步重试
CREATE TABLE IF NOT EXISTS workflow_callback_retry (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    callback_id VARCHAR(64) NOT NULL UNIQUE COMMENT '回调唯一ID',
    process_definition_key VARCHAR(64) NOT NULL COMMENT '流程定义Key',
    callback_type VARCHAR(32) NOT NULL COMMENT '回调类型：BEFORE_SUBMIT/AFTER_SUBMIT/BEFORE_APPROVAL/AFTER_APPROVAL等',
    context_json TEXT NOT NULL COMMENT '回调上下文JSON',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待重试/SUCCESS-成功/FAILED-失败/CANCELLED-已取消',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME COMMENT '下次重试时间',
    error_message VARCHAR(500) COMMENT '最后一次错误信息',
    trace_id VARCHAR(64) COMMENT '分布式追踪ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    tenant_id BIGINT COMMENT '租户ID',
    INDEX idx_status_retry (status, next_retry_time) COMMENT '状态+重试时间索引',
    INDEX idx_process_key (process_definition_key) COMMENT '流程Key索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流回调重试表';

-- 工作流操作日志表
-- 记录所有工作流操作，用于审计和问题排查
CREATE TABLE IF NOT EXISTS workflow_operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    trace_id VARCHAR(64) COMMENT '分布式追踪ID',
    process_instance_id VARCHAR(64) COMMENT '流程实例ID',
    process_definition_key VARCHAR(64) COMMENT '流程定义Key',
    business_key VARCHAR(128) COMMENT '业务Key',
    task_id VARCHAR(64) COMMENT '任务ID',
    task_definition_key VARCHAR(64) COMMENT '任务定义Key',
    operation_type VARCHAR(32) NOT NULL COMMENT '操作类型：SUBMIT/APPROVAL/RECALL/JUMP/CARBON_COPY',
    operator_id VARCHAR(64) COMMENT '操作人ID',
    operator_name VARCHAR(128) COMMENT '操作人名称',
    request_json TEXT COMMENT '请求参数JSON',
    response_json TEXT COMMENT '响应结果JSON',
    callback_result VARCHAR(32) COMMENT '回调执行结果：SUCCESS/FAILED/TIMEOUT/SKIPPED',
    callback_duration INT COMMENT '回调耗时(毫秒)',
    total_duration INT COMMENT '总耗时(毫秒)',
    error_message VARCHAR(500) COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    tenant_id BIGINT COMMENT '租户ID',
    INDEX idx_trace_id (trace_id) COMMENT '追踪ID索引',
    INDEX idx_process_instance (process_instance_id) COMMENT '流程实例索引',
    INDEX idx_business_key (business_key) COMMENT '业务Key索引',
    INDEX idx_operator (operator_id) COMMENT '操作人索引',
    INDEX idx_created_at (created_at) COMMENT '创建时间索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作流操作日志表';
