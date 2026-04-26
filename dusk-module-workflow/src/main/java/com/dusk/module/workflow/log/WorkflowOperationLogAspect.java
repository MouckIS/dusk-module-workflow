package com.dusk.module.workflow.log;

import com.dusk.common.core.auth.authentication.LoginUserIdContextHolder;
import com.dusk.common.core.tenant.TenantContextHolder;
import com.dusk.workflow.trace.WorkflowTraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 工作流操作日志切面
 * <p>
 * 自动记录所有工作流核心操作（提交、审批、撤回、跳转等），
 * 包括请求参数、响应结果、执行时间、错误信息等。
 * </p>
 *
 * @author kefuming
 */
@Slf4j
@Aspect
@Component
public class WorkflowOperationLogAspect {

    @Autowired
    private WorkflowOperationLogMapper logMapper;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 匹配 WorkflowServiceImpl 的核心方法
     */
    @Pointcut("execution(* com.dusk.module.workflow.service.impl.WorkflowServiceImpl.genericSubmit(..)) || " +
              "execution(* com.dusk.module.workflow.service.impl.WorkflowServiceImpl.genericApproval(..)) || " +
              "execution(* com.dusk.module.workflow.service.impl.WorkflowServiceImpl.recallProcess(..)) || " +
              "execution(* com.dusk.module.workflow.service.impl.WorkflowServiceImpl.recallPre(..)) || " +
              "execution(* com.dusk.module.workflow.service.impl.WorkflowServiceImpl.jumpToNode(..)) || " +
              "execution(* com.dusk.module.workflow.service.impl.WorkflowServiceImpl.sendCarbonCopy(..))")
    public void workflowOperationPointcut() {
    }

    @Around("workflowOperationPointcut()")
    public Object logOperation(ProceedingJoinPoint pjp) throws Throwable {
        String methodName = pjp.getSignature().getName();
        Object[] args = pjp.getArgs();

        // 构建日志对象
        WorkflowOperationLog opLog = new WorkflowOperationLog();
        opLog.setTraceId(WorkflowTraceContext.getTraceId());
        opLog.setOperationType(mapOperationType(methodName));
        opLog.setRequestJson(safeToJson(args));
        opLog.setCreatedAt(LocalDateTime.now());
        opLog.setTenantId(TenantContextHolder.getTenantId());

        // 获取操作人
        Long userId = LoginUserIdContextHolder.getUserId();
        if (userId != null) {
            opLog.setOperatorId(userId.toString());
        }

        // 从参数中提取流程信息
        extractProcessInfo(args, opLog);

        long startTime = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            opLog.setResponseJson(safeToJson(result));
            opLog.setCallbackResult("SUCCESS");
            return result;
        } catch (Exception e) {
            opLog.setErrorMessage(truncate(e.getMessage(), 500));
            opLog.setCallbackResult("FAILED");
            throw e;
        } finally {
            opLog.setTotalDuration((int) (System.currentTimeMillis() - startTime));
            asyncSaveLog(opLog);
        }
    }

    /**
     * 异步保存日志，不影响主流程性能
     */
    @Async
    public void asyncSaveLog(WorkflowOperationLog opLog) {
        try {
            logMapper.save(opLog);
        } catch (Exception e) {
            log.error("保存工作流操作日志失败", e);
        }
    }

    /**
     * 映射方法名到操作类型
     */
    private String mapOperationType(String methodName) {
        switch (methodName) {
            case "genericSubmit":
                return "SUBMIT";
            case "genericApproval":
                return "APPROVAL";
            case "recallProcess":
            case "recallPre":
                return "RECALL";
            case "jumpToNode":
                return "JUMP";
            case "sendCarbonCopy":
                return "CARBON_COPY";
            default:
                return methodName.toUpperCase();
        }
    }

    /**
     * 从参数中提取流程信息
     */
    private void extractProcessInfo(Object[] args, WorkflowOperationLog opLog) {
        if (args == null || args.length == 0) {
            return;
        }

        Object firstArg = args[0];
        if (firstArg == null) {
            return;
        }

        try {
            // 通过反射获取常见字段
            Class<?> clazz = firstArg.getClass();

            // processInstanceId
            try {
                var method = clazz.getMethod("getProcessInstanceId");
                Object value = method.invoke(firstArg);
                if (value != null) {
                    opLog.setProcessInstanceId(value.toString());
                }
            } catch (NoSuchMethodException ignored) {
            }

            // processDefinitionKey
            try {
                var method = clazz.getMethod("getProcessDefinitionKey");
                Object value = method.invoke(firstArg);
                if (value != null) {
                    opLog.setProcessDefinitionKey(value.toString());
                }
            } catch (NoSuchMethodException ignored) {
            }

            // businessKey
            try {
                var method = clazz.getMethod("getBusinessKey");
                Object value = method.invoke(firstArg);
                if (value != null) {
                    opLog.setBusinessKey(value.toString());
                }
            } catch (NoSuchMethodException ignored) {
            }

            // taskId
            try {
                var method = clazz.getMethod("getTaskId");
                Object value = method.invoke(firstArg);
                if (value != null) {
                    opLog.setTaskId(value.toString());
                }
            } catch (NoSuchMethodException ignored) {
            }
        } catch (Exception e) {
            log.debug("提取流程信息失败", e);
        }
    }

    /**
     * 安全的 JSON 序列化
     */
    private String safeToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            // 限制长度，避免超大日志
            return truncate(json, 10000);
        } catch (Exception e) {
            return "[序列化失败: " + e.getMessage() + "]";
        }
    }

    /**
     * 截断字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}
