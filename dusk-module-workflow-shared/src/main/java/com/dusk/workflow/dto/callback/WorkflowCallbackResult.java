package com.dusk.workflow.dto.callback;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 工作流回调结果
 * <p>
 * 业务服务在前置回调中返回此对象，可以：
 * <ul>
 *   <li>通过 {@code proceed} 控制是否继续执行流程操作</li>
 *   <li>通过 {@code variables} 添加/修改流程变量</li>
 *   <li>通过 {@code rejectReason} 提供拒绝原因</li>
 * </ul>
 * </p>
 *
 * @author kefuming
 */
@Data
public class WorkflowCallbackResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否继续执行流程操作
     * <p>
     * true: 继续执行（默认）
     * false: 中断执行，抛出业务异常
     * </p>
     */
    private boolean proceed = true;

    /**
     * 拒绝原因（proceed=false 时有值）
     */
    private String rejectReason;

    /**
     * 需要更新/添加的流程变量
     */
    private Map<String, Object> variables;

    /**
     * 回调执行耗时（毫秒）—— 由工作流服务填充
     */
    private Long duration;

    /**
     * 创建继续执行的结果
     */
    public static WorkflowCallbackResult proceed() {
        return new WorkflowCallbackResult();
    }

    /**
     * 创建拒绝执行的结果
     *
     * @param reason 拒绝原因
     */
    public static WorkflowCallbackResult reject(String reason) {
        WorkflowCallbackResult result = new WorkflowCallbackResult();
        result.setProceed(false);
        result.setRejectReason(reason);
        return result;
    }

    /**
     * 创建带变量的继续执行结果
     *
     * @param variables 需要添加/更新的流程变量
     */
    public static WorkflowCallbackResult withVariables(Map<String, Object> variables) {
        WorkflowCallbackResult result = new WorkflowCallbackResult();
        result.setVariables(variables);
        return result;
    }

    /**
     * 添加单个变量
     *
     * @param key   变量名
     * @param value 变量值
     * @return this
     */
    public WorkflowCallbackResult addVariable(String key, Object value) {
        if (this.variables == null) {
            this.variables = new HashMap<>();
        }
        this.variables.put(key, value);
        return this;
    }

    /**
     * 添加多个变量
     *
     * @param variables 变量集合
     * @return this
     */
    public WorkflowCallbackResult addVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return this;
        }
        if (this.variables == null) {
            this.variables = new HashMap<>();
        }
        this.variables.putAll(variables);
        return this;
    }

    /**
     * 检查是否需要合并变量
     */
    public boolean hasVariables() {
        return variables != null && !variables.isEmpty();
    }
}
