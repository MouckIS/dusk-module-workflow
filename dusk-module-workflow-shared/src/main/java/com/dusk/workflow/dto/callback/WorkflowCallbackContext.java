package com.dusk.workflow.dto.callback;

import com.dusk.workflow.dto.WorkflowTaskDto;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流回调上下文
 * <p>
 * 在工作流服务与业务服务之间传递上下文信息，包含流程信息、任务信息、
 * 变量数据、操作信息等。支持跨服务 RPC 调用时的序列化传输。
 * </p>
 *
 * @author kefuming
 */
@Data
public class WorkflowCallbackContext implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 流程基础信息 ====================

    /**
     * 流程定义Key
     */
    private String processDefinitionKey;

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 业务Key（业务主键，如合同ID、订单ID）
     */
    private String businessKey;

    // ==================== 任务信息（审批时有值）====================

    /**
     * 当前任务ID
     */
    private String taskId;

    /**
     * 任务定义Key（节点标识）
     */
    private String taskDefinitionKey;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 当前任务处理人
     */
    private String assignee;

    /**
     * 审批意见
     */
    private String comment;

    // ==================== 变量数据 ====================

    /**
     * 流程变量
     */
    private Map<String, Object> variables;

    /**
     * 业务扩展数据（业务服务传入的自定义数据）
     */
    private Map<String, Object> businessData;

    /**
     * 上下文属性（用于回调间传递临时数据）
     */
    private Map<String, Object> attributes;

    // ==================== 操作信息 ====================

    /**
     * 当前工作流阶段
     */
    private WorkflowPhase phase;

    /**
     * 操作人ID
     */
    private String operatorId;

    /**
     * 操作人名称
     */
    private String operatorName;

    /**
     * 分布式追踪ID
     */
    private String traceId;

    /**
     * 操作时间戳
     */
    private LocalDateTime timestamp;

    /**
     * 租户ID
     */
    private Long tenantId;

    // ==================== 执行结果（后置回调时有值）====================

    /**
     * 流程是否已结束
     */
    private boolean processEnded;

    /**
     * 新产生的待办任务列表
     */
    private List<WorkflowTaskDto> nextTasks;

    /**
     * 撤回/跳转的目标节点Key
     */
    private String targetTaskDefinitionKey;

    // ==================== 便捷方法 ====================

    /**
     * 获取上下文属性
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key, Class<T> type) {
        if (attributes == null) {
            return null;
        }
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 设置上下文属性
     */
    public void setAttribute(String key, Object value) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(key, value);
    }

    /**
     * 检查是否存在属性
     */
    public boolean hasAttribute(String key) {
        return attributes != null && attributes.containsKey(key);
    }

    /**
     * 获取流程变量
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) {
        if (variables == null) {
            return null;
        }
        Object value = variables.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 设置流程变量
     */
    public void setVariable(String key, Object value) {
        if (variables == null) {
            variables = new HashMap<>();
        }
        variables.put(key, value);
    }

    /**
     * 获取业务数据
     */
    @SuppressWarnings("unchecked")
    public <T> T getBusinessData(String key, Class<T> type) {
        if (businessData == null) {
            return null;
        }
        Object value = businessData.get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器
     */
    public static class Builder {
        private final WorkflowCallbackContext context = new WorkflowCallbackContext();

        public Builder processDefinitionKey(String processDefinitionKey) {
            context.setProcessDefinitionKey(processDefinitionKey);
            return this;
        }

        public Builder processInstanceId(String processInstanceId) {
            context.setProcessInstanceId(processInstanceId);
            return this;
        }

        public Builder businessKey(String businessKey) {
            context.setBusinessKey(businessKey);
            return this;
        }

        public Builder taskId(String taskId) {
            context.setTaskId(taskId);
            return this;
        }

        public Builder taskDefinitionKey(String taskDefinitionKey) {
            context.setTaskDefinitionKey(taskDefinitionKey);
            return this;
        }

        public Builder taskName(String taskName) {
            context.setTaskName(taskName);
            return this;
        }

        public Builder assignee(String assignee) {
            context.setAssignee(assignee);
            return this;
        }

        public Builder comment(String comment) {
            context.setComment(comment);
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            context.setVariables(variables);
            return this;
        }

        public Builder businessData(Map<String, Object> businessData) {
            context.setBusinessData(businessData);
            return this;
        }

        public Builder phase(WorkflowPhase phase) {
            context.setPhase(phase);
            return this;
        }

        public Builder operatorId(String operatorId) {
            context.setOperatorId(operatorId);
            return this;
        }

        public Builder operatorName(String operatorName) {
            context.setOperatorName(operatorName);
            return this;
        }

        public Builder traceId(String traceId) {
            context.setTraceId(traceId);
            return this;
        }

        public Builder tenantId(Long tenantId) {
            context.setTenantId(tenantId);
            return this;
        }

        public Builder timestamp(LocalDateTime timestamp) {
            context.setTimestamp(timestamp);
            return this;
        }

        public Builder processEnded(boolean processEnded) {
            context.setProcessEnded(processEnded);
            return this;
        }

        public Builder nextTasks(List<WorkflowTaskDto> nextTasks) {
            context.setNextTasks(nextTasks);
            return this;
        }

        public WorkflowCallbackContext build() {
            if (context.getTimestamp() == null) {
                context.setTimestamp(LocalDateTime.now());
            }
            return context;
        }
    }
}
