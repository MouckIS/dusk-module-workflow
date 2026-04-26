# 业务微服务接入工作流回调指南

## 概述

工作流微服务已升级为 **v2.0 回调架构**，提供了动态可扩展的接口设计，业务微服务可以通过实现 `IWorkflowCallbackRpcService` 接口来接入各种工作流业务逻辑。

## 核心特性

1. **跨服务回调** - 通过 Dubbo RPC 调用业务服务的回调方法
2. **分布式追踪** - 全链路 TraceId 传递，MDC 日志集成
3. **事务一致性** - 本地消息表 + 定时补偿，保证事件最终一致性
4. **操作审计** - 自动记录所有工作流操作日志
5. **灵活扩展** - 前置/后置回调，支持拦截、修改、观察等多种模式

## 架构图

```
┌──────────────┐                    ┌──────────────┐
│  业务微服务  │                    │  工作流微服务 │
│              │                    │              │
│  实现接口    │◄───── RPC ────────┤  调用回调    │
│  IWorkflow   │       (Dubbo)      │  Registry    │
│  Callback    │                    │              │
│  RpcService  │                    │              │
└──────────────┘                    └──────────────┘
       │                                    │
       │                                    │
       ▼                                    ▼
  业务数据库                          工作流数据库
  (本地事务)                          (事务消息表)
```

## 快速开始

### 1. 添加依赖

在业务微服务的 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.dusk</groupId>
    <artifactId>dusk-module-workflow-shared</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 实现回调接口

创建回调服务实现类：

```java
package com.dusk.module.business.workflow;

import com.dusk.workflow.dto.callback.*;
import com.dusk.workflow.service.callback.IWorkflowCallbackRpcService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 请假流程回调服务
 */
@Slf4j
@DubboService(version = "1.0.0")
public class LeaveWorkflowCallbackService implements IWorkflowCallbackRpcService {

    @Autowired
    private LeaveApplicationService leaveService;

    @Override
    public String getProcessKey() {
        return "leave_process";  // 流程定义Key
    }

    /**
     * 提交前置回调 - 业务校验
     */
    @Override
    public WorkflowCallbackResult onBeforeSubmit(WorkflowCallbackContext context) {
        String businessKey = context.getBusinessKey();
        
        // 1. 业务数据校验
        LeaveApplication leave = leaveService.getById(businessKey);
        if (leave == null) {
            return WorkflowCallbackResult.reject("请假单不存在");
        }
        if (leave.getDays() > 30) {
            return WorkflowCallbackResult.reject("请假天数不能超过30天");
        }

        // 2. 可以修改流程变量
        WorkflowCallbackResult result = WorkflowCallbackResult.proceed();
        result.addVariable("leaveDays", leave.getDays());
        result.addVariable("leaveType", leave.getType());
        
        log.info("请假单 {} 通过业务校验，准备提交流程", businessKey);
        return result;
    }

    /**
     * 提交后置回调 - 更新业务状态
     */
    @Override
    public WorkflowCallbackResult onAfterSubmit(WorkflowCallbackContext context) {
        String businessKey = context.getBusinessKey();
        String processInstanceId = context.getProcessInstanceId();
        
        // 更新业务单据状态
        leaveService.updateStatus(businessKey, "审批中", processInstanceId);
        
        log.info("请假单 {} 已提交审批，流程实例：{}", businessKey, processInstanceId);
        return WorkflowCallbackResult.proceed();
    }

    /**
     * 审批前置回调 - 权限校验
     */
    @Override
    public WorkflowCallbackResult onBeforeApproval(WorkflowCallbackContext context) {
        String businessKey = context.getBusinessKey();
        Long userId = LoginUserIdContextHolder.getUserId();
        
        // 检查审批人权限
        if (!leaveService.canApprove(businessKey, userId)) {
            return WorkflowCallbackResult.reject("您没有审批权限");
        }
        
        return WorkflowCallbackResult.proceed();
    }

    /**
     * 审批后置回调 - 根据流程结束状态更新业务
     */
    @Override
    public WorkflowCallbackResult onAfterApproval(WorkflowCallbackContext context) {
        String businessKey = context.getBusinessKey();
        boolean processEnded = context.isProcessEnded();
        
        if (processEnded) {
            // 流程结束，根据最终结果更新业务状态
            String result = (String) context.getVariable("approvalResult");
            if ("approved".equals(result)) {
                leaveService.updateStatus(businessKey, "已批准", null);
                log.info("请假单 {} 审批通过", businessKey);
            } else {
                leaveService.updateStatus(businessKey, "已拒绝", null);
                log.info("请假单 {} 审批拒绝", businessKey);
            }
        } else {
            log.info("请假单 {} 审批节点通过，继续流转", businessKey);
        }
        
        return WorkflowCallbackResult.proceed();
    }

    /**
     * 撤回后置回调 - 恢复业务状态
     */
    @Override
    public WorkflowCallbackResult onAfterRecall(WorkflowCallbackContext context) {
        String businessKey = context.getBusinessKey();
        
        // 撤回后恢复为草稿状态
        leaveService.updateStatus(businessKey, "草稿", null);
        
        log.info("请假单 {} 已撤回", businessKey);
        return WorkflowCallbackResult.proceed();
    }
}
```

### 3. 配置 Dubbo

在 `application.yml` 中配置 Dubbo：

```yaml
dubbo:
  application:
    name: business-service
  registry:
    address: nacos://localhost:8848
  protocol:
    name: dubbo
    port: -1  # 自动分配端口
  scan:
    base-packages: com.dusk.module.business.workflow
```

### 4. 调用工作流服务

```java
@Service
public class LeaveApplicationService {
    
    @DubboReference
    private IWorkFlowRpcService workflowRpcService;
    
    /**
     * 提交请假申请
     */
    @Transactional
    public void submitLeaveApplication(Long leaveId) {
        LeaveApplication leave = getById(leaveId);
        
        // 构建工作流提交参数
        GenericSubmitInput input = new GenericSubmitInput();
        input.setProcessDefinitionKey("leave_process");
        input.setBusinessKey(leaveId.toString());
        input.setTitle("请假申请-" + leave.getUserName());
        input.setStarter(leave.getUserName());
        
        // 设置流程变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("leaveDays", leave.getDays());
        variables.put("leaveType", leave.getType());
        input.setVariables(variables);
        
        // 调用工作流服务（会自动触发 onBeforeSubmit 和 onAfterSubmit 回调）
        StartProcessOutDto result = workflowRpcService.genericSubmit(input);
        
        log.info("流程提交成功，实例ID: {}", result.getProcessInstanceId());
    }
}
```

## 回调方法详解

### 前置回调 (Before Callbacks)

**特点：**
- 在事务内同步执行
- 可以拦截操作（返回 `reject()`）
- 可以修改流程变量
- 异常会导致事务回滚

**使用场景：**
- 业务数据校验
- 权限检查
- 动态修改流程变量
- 阻止不合规操作

### 后置回调 (After Callbacks)

**特点：**
- 在事务内异步执行（但在事务提交前）
- 不能阻止操作（proceed/reject 无效）
- 异常会被记录但不回滚
- 用于观察和同步状态

**使用场景：**
- 更新业务单据状态
- 记录审批历史
- 发送通知消息
- 同步到其他系统

## 回调上下文 (WorkflowCallbackContext)

回调方法接收的上下文对象包含：

```java
public class WorkflowCallbackContext {
    private String processKey;              // 流程定义Key
    private String businessKey;             // 业务主键
    private String processInstanceId;       // 流程实例ID (after回调有值)
    private String taskId;                  // 任务ID
    private WorkflowPhase phase;            // 当前阶段: SUBMIT/APPROVAL/RECALL/JUMP
    private Map<String, Object> variables;  // 流程变量
    private Map<String, Object> businessData; // 业务扩展数据
    private boolean processEnded;           // 流程是否结束 (after回调有效)
    private String traceId;                 // 分布式追踪ID
    
    // 便捷方法
    public <T> T getAttribute(String key, Class<T> type);
    public void setVariable(String key, Object value);
    public <T> T getBusinessData(String key, Class<T> type);
}
```

## 回调结果 (WorkflowCallbackResult)

回调方法返回的结果对象：

```java
// 允许继续（前置回调）
return WorkflowCallbackResult.proceed();

// 拒绝操作（前置回调）
return WorkflowCallbackResult.reject("拒绝原因");

// 允许继续并修改变量（前置回调）
WorkflowCallbackResult result = WorkflowCallbackResult.proceed();
result.addVariable("approver", "manager");
result.addVariable("priority", "high");
return result;

// 后置回调只需返回 proceed()
return WorkflowCallbackResult.proceed();
```

## 分布式追踪

所有回调都会自动传递 TraceId，支持全链路追踪：

```java
@Override
public WorkflowCallbackResult onAfterSubmit(WorkflowCallbackContext context) {
    String traceId = context.getTraceId();
    log.info("[TraceId: {}] 处理提交回调", traceId);
    
    // TraceId 已自动设置到 MDC，日志会自动输出
    // 也可以传递给下游服务
    downstreamService.process(businessKey, traceId);
    
    return WorkflowCallbackResult.proceed();
}
```

日志输出示例：
```
2026-03-31 15:30:25.123 [TraceId: a1b2c3d4e5f6...] INFO  处理提交回调
```

## 事务管理

### 业务服务事务

- 回调方法运行在**业务服务自己的事务**中
- 可以访问业务数据库
- 前置回调抛异常会回滚**业务事务**和**工作流事务**
- 后置回调抛异常只回滚**业务事务**

### 工作流服务事务

- 工作流操作运行在**工作流服务的事务**中
- 前置回调在工作流操作**之前**执行
- 后置回调在工作流操作**之后**、事务提交**之前**执行

### 事件发送

- MQ 事件通过**本地消息表**保证最终一致性
- 消息先保存到数据库（与工作流操作同事务）
- 事务提交后由定时任务发送
- 失败自动重试（指数退避：5s, 10s, 20s, 40s, 80s）

## 最佳实践

### 1. 前置回调做校验，后置回调做同步

```java
@Override
public WorkflowCallbackResult onBeforeSubmit(WorkflowCallbackContext context) {
    // ✅ 在前置回调做业务校验
    if (!validateBusinessRule(context.getBusinessKey())) {
        return WorkflowCallbackResult.reject("业务规则校验失败");
    }
    return WorkflowCallbackResult.proceed();
}

@Override
public WorkflowCallbackResult onAfterSubmit(WorkflowCallbackContext context) {
    // ✅ 在后置回调做状态同步
    updateBusinessStatus(context.getBusinessKey(), "审批中");
    return WorkflowCallbackResult.proceed();
}
```

### 2. 捕获后置回调异常

```java
@Override
public WorkflowCallbackResult onAfterApproval(WorkflowCallbackContext context) {
    try {
        // 后置回调的异常不应影响主流程
        updateBusinessStatus(context.getBusinessKey(), "已完成");
    } catch (Exception e) {
        log.error("更新业务状态失败，但不影响流程", e);
        // 可以记录到补偿队列，稍后重试
        compensationQueue.add(context.getBusinessKey());
    }
    return WorkflowCallbackResult.proceed();
}
```

### 3. 使用流程变量传递数据

```java
@Override
public WorkflowCallbackResult onBeforeApproval(WorkflowCallbackContext context) {
    // ✅ 从流程变量读取数据
    Integer leaveDays = context.getAttribute("leaveDays", Integer.class);
    
    // 根据请假天数决定审批人
    String approver = leaveDays > 3 ? "manager" : "leader";
    
    // ✅ 通过变量修改传递给工作流引擎
    WorkflowCallbackResult result = WorkflowCallbackResult.proceed();
    result.addVariable("nextApprover", approver);
    return result;
}
```

### 4. 利用 TraceId 做问题排查

```java
@Override
public WorkflowCallbackResult onAfterSubmit(WorkflowCallbackContext context) {
    String traceId = context.getTraceId();
    
    try {
        processBusinessLogic(context.getBusinessKey());
    } catch (Exception e) {
        // TraceId 自动记录到日志，方便关联查询
        log.error("业务处理失败, businessKey={}", context.getBusinessKey(), e);
        // 可以将 TraceId 存储到数据库用于问题追踪
        saveErrorLog(context.getBusinessKey(), traceId, e.getMessage());
    }
    
    return WorkflowCallbackResult.proceed();
}
```

### 5. 监听 MQ 事件做异步处理

对于不需要在事务内处理的逻辑，可以监听 MQ 事件：

```java
@Component
public class LeaveWorkflowEventListener implements IWorkflowListener {

    @Override
    public String getProcessKey() {
        return "leave_process";
    }

    @Override
    public void onProcessCompleted(WorkflowEventDto event) {
        // 流程结束后异步处理
        String businessKey = event.getBusinessKey();
        
        // 发送邮件通知
        emailService.sendApprovalResultEmail(businessKey);
        
        // 同步到HR系统
        hrSystemClient.syncLeaveRecord(businessKey);
        
        log.info("请假流程 {} 完成后处理", businessKey);
    }
}
```

## 故障排查

### 1. 回调未被调用

检查事项：
- Dubbo 服务是否正常注册？
- `getProcessKey()` 返回值是否与流程定义Key一致？
- 工作流服务是否能发现业务服务？

查看日志：
```
grep "发现回调服务" workflow-service.log
grep "调用前置回调" workflow-service.log
```

### 2. 前置回调返回拒绝但流程仍继续

- 检查是否抛出了 `BusinessException`
- `WorkflowCallbackResult.reject()` 会被转换为异常抛出

### 3. 事件未收到

- 检查 RabbitMQ 是否正常
- 查看事务消息表：`SELECT * FROM workflow_transactional_message WHERE status = 'FAILED'`
- 查看补偿任务日志

### 4. TraceId 丢失

- 确认 Dubbo Filter 已生效：`grep "WorkflowTraceFilter" workflow-service.log`
- 检查 `logback.xml` 是否配置了 MDC 输出：`%X{traceId}`

## 附录

### A. 完整回调方法列表

| 方法 | 触发时机 | 类型 | 可拒绝 | 用途 |
|------|---------|------|--------|------|
| onBeforeSubmit | 提交流程前 | Before | ✅ | 业务校验、设置变量 |
| onAfterSubmit | 提交流程后 | After | ❌ | 更新业务状态 |
| onBeforeApproval | 审批任务前 | Before | ✅ | 权限校验、设置变量 |
| onAfterApproval | 审批任务后 | After | ❌ | 根据结果更新业务 |
| onBeforeRecall | 撤回流程前 | Before | ✅ | 撤回前校验 |
| onAfterRecall | 撤回流程后 | After | ❌ | 恢复业务状态 |
| onAfterJump | 节点跳转后 | After | ❌ | 记录跳转日志 |
| resolveApprovers | 动态审批人 | Before | ❌ | 返回审批人列表 |
| resolveVariables | 动态变量 | Before | ❌ | 返回流程变量 |

### B. 数据库表说明

工作流服务新增3张表：

1. `workflow_transactional_message` - 事务消息表（本地消息表模式）
2. `workflow_callback_retry` - 回调重试记录（暂未使用）
3. `workflow_operation_log` - 操作日志（自动记录所有操作）

### C. 配置参数

```yaml
# application.yml (工作流服务)
workflow:
  callback:
    # 回调服务刷新间隔（秒）
    registry-refresh-interval: 60
    # 回调超时时间（毫秒）
    timeout: 5000
    # 是否启用失败快速模式（前置回调失败立即抛异常）
    fail-fast: true
  
  message:
    # 消息发送批次大小
    batch-size: 100
    # 消息最大重试次数
    max-retry: 5
    # 消息保留天数
    retention-days: 7
```

## 总结

通过实现 `IWorkflowCallbackRpcService` 接口，业务微服务可以：

1. ✅ **灵活扩展** - 无需修改工作流服务，动态接入业务逻辑
2. ✅ **事务安全** - 本地事务 + 事务消息表保证数据一致性
3. ✅ **全链路追踪** - TraceId 自动传递，问题快速定位
4. ✅ **操作审计** - 所有操作自动记录，满足合规要求
5. ✅ **高可用** - 回调失败不影响主流程，MQ 事件自动重试

现在开始接入您的业务流程吧！🚀
