# 工作流微服务模块优化 — 设计与实现文档

> **模块**: dusk-module-workflow  
> **版本**: v2.0  
> **作者**: kefuming  
> **日期**: 2026-02-28

---

## 一、概述

本次优化围绕工作流微服务模块，新增 **5大核心功能**：MQ 事件通信机制、通用审批流程接口（含前/后置处理器）、流程撤回、节点跳转、抄送审批人。设计遵循 **开闭原则** ——工作流核心引擎不因业务变更而修改，业务模块仅需实现对应接口即可扩展。

---

## 二、架构总览

```
┌──────────────────────────────────────────────────────────────┐
│                     业务模块 (其他微服务)                       │
│  ┌────────────────┐ ┌──────────────────┐ ┌────────────────┐  │
│  │IWorkflowListener│ │IWorkflowSubmit   │ │IWorkflowRecall │  │
│  │  (事件监听)      │ │  Processor       │ │  Handler       │  │
│  │                │ │IWorkflowApproval │ │  (撤回回调)     │  │
│  │                │ │  Processor       │ │                │  │
│  └───────▲────────┘ └───────▲──────────┘ └───────▲────────┘  │
│          │                  │                    │            │
└──────────┼──────────────────┼────────────────────┼────────────┘
           │ (MQ/Spring事件)   │ (Spring Bean)      │ (Spring Bean)
┌──────────┼──────────────────┼────────────────────┼────────────┐
│          │     dusk-module-workflow (工作流微服务)   │            │
│  ┌───────┴────────┐ ┌───────┴──────────┐ ┌───────┴────────┐  │
│  │WorkflowEvent   │ │WorkflowProcessor │ │WorkflowService │  │
│  │  Publisher      │ │  Registry        │ │  Impl          │  │
│  │WorkflowEvent   │ │                  │ │                │  │
│  │  Consumer       │ │                  │ │                │  │
│  └───────┬────────┘ └──────────────────┘ └───────┬────────┘  │
│          │                                       │            │
│          ▼                                       ▼            │
│  ┌────────────────┐                      ┌────────────────┐  │
│  │   RabbitMQ     │                      │CarbonCopy      │  │
│  │   Exchange     │                      │  Service        │  │
│  └────────────────┘                      └───────┬────────┘  │
│                                                  ▼            │
│                                          ┌────────────────┐  │
│                                          │INotification   │  │
│                                          │  RpcService    │  │
│                                          │  (站内信)       │  │
│                                          └────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

---

## 三、模块结构

### 3.1 shared 模块（dusk-module-workflow-shared）

shared 模块定义了所有 **接口契约** 和 **DTO**，供其他微服务引用：

```
com.dusk.workflow
├── dto
│   ├── WorkflowEventDto.java         # 工作流事件DTO
│   ├── GenericSubmitInput.java        # 通用提交入参
│   ├── GenericApprovalInput.java      # 通用审批入参
│   ├── RecallProcessInput.java        # 撤回入参
│   ├── JumpToNodeInput.java           # 节点跳转入参
│   ├── CarbonCopyInput.java           # 抄送入参
│   └── WorkflowRecallDto.java         # 撤回上下文（回调参数）
├── enums
│   └── WorkflowEventType.java         # 事件类型枚举
└── service
    ├── IWorkFlowRpcService.java        # RPC接口（新增5个方法）
    ├── IWorkflowListener.java          # 事件监听器接口
    ├── IWorkflowSubmitProcessor.java   # 提交前/后置处理器接口
    ├── IWorkflowApprovalProcessor.java # 审批前/后置处理器接口
    ├── IWorkflowRecallHandler.java     # 撤回业务处理器接口
    └── INotificationRpcService.java    # 站内信RPC接口
```

### 3.2 impl 模块（dusk-module-workflow）

impl 模块实现核心引擎逻辑：

```
com.dusk.module.workflow
├── core/config
│   └── WorkflowMqConfig.java          # MQ Exchange/Queue 声明
├── event
│   ├── WorkflowEventPublisher.java    # 事件发布器（Spring + MQ双通道）
│   ├── WorkflowEventConsumer.java     # 事件消费 & 分发
│   └── WorkflowSpringEvent.java       # Spring事件包装
├── service
│   ├── WorkflowProcessorRegistry.java # 处理器注册中心
│   └── impl
│       ├── WorkflowServiceImpl.java   # 核心实现（已增强）
│       ├── WorkflowRpcServiceImpl.java# RPC委托层（已增强）
│       └── WorkflowCarbonCopyService.java # 抄送服务
├── controller
│   └── WorkflowController.java        # REST接口（新增5个端点）
└── dto
    └── TaskFormKey.java               # formKey配置（新增抄送配置）
```

---

## 四、功能详细设计

### 4.1 Feature 1: MQ 事件通信机制

#### 设计目标

流程在关键节点（启动、任务创建/完成、流程完成、撤回、跳转、抄送）自动发布事件，业务模块仅需实现 `IWorkflowListener` 接口即可接收事件通知。

#### 事件类型

| 枚举值 | 触发时机 | 说明 |
|--------|---------|------|
| `PROCESS_STARTED` | 流程启动后 | `startProcess()` 成功后发布 |
| `TASK_CREATED` | 新任务产生时 | 流程启动或审批流转产生新任务时 |
| `TASK_COMPLETED` | 任务完成时 | `completeTask()` 完成后发布 |
| `PROCESS_COMPLETED` | 流程结束时 | 审批完最后一个节点后检测 |
| `PROCESS_RECALLED` | 流程撤回时 | `recallPre()` 完成后发布 |
| `TASK_JUMPED` | 节点跳转时 | `jumpToNode()` 完成后发布 |
| `TASK_CC` | 抄送发送时 | 抄送站内信发送后发布 |

#### 双通道发布

```
WorkflowEventPublisher
    ├── Spring ApplicationEvent → WorkflowEventConsumer (进程内，异步)
    └── RabbitMQ Message → workflow.event.exchange (跨服务)
```

- **进程内**: 通过 `ApplicationEventPublisher` 发布 `WorkflowSpringEvent`，消费端用 `@Async @EventListener` 异步处理
- **跨服务**: 通过 `RabbitTemplate` 发布到 `workflow.event.exchange`，routing key 格式为 `workflow.event.{event_type}`

#### 业务接入方式

```java
@Component
public class MyOrderWorkflowListener implements IWorkflowListener {

    @Override
    public String getProcessKey() {
        return "order_approval"; // 只监听订单审批流程，返回null则监听全部
    }

    @Override
    public void onWorkflowEvent(WorkflowEventDto event) {
        switch (event.getEventType()) {
            case TASK_COMPLETED -> handleTaskCompleted(event);
            case PROCESS_COMPLETED -> handleProcessCompleted(event);
        }
    }
}
```

---

### 4.2 Feature 2: 通用审批流程接口

#### 设计目标

提供统一的 `genericSubmit` 和 `genericApproval` 接口，内置前/后置处理器扩展点，业务模块无需直接调用底层 `startProcess`/`completeTask`。

#### 处理流程

```
genericSubmit 流程:
  ┌──────────────┐    ┌─────────────┐    ┌──────────────┐    ┌──────────┐
  │ preSubmit()  │ -> │ startProcess│ -> │ postSubmit() │ -> │ 抄送通知  │
  │ (前置处理器)  │    │ /completeFirst│    │ (后置处理器) │    │ (可选)   │
  └──────────────┘    └─────────────┘    └──────────────┘    └──────────┘

genericApproval 流程:
  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐
  │ preApproval()│ -> │ completeTask │ -> │ postApproval()│ -> │ 抄送通知 │
  │ (前置处理器)  │    │              │    │ (后置处理器)  │    │ (可选)   │
  └──────────────┘    └──────────────┘    └──────────────┘    └──────────┘
```

#### 处理器注册

`WorkflowProcessorRegistry` 在容器启动时自动收集所有 `IWorkflowSubmitProcessor` 和 `IWorkflowApprovalProcessor` 的 Spring Bean，按 `processKey` 建立索引。

#### 业务接入方式

```java
@Component
public class ContractSubmitProcessor implements IWorkflowSubmitProcessor {

    @Override
    public String getProcessKey() {
        return "contract_approval";
    }

    @Override
    public void preSubmit(WorkflowProcessDto input) {
        // 提交前：校验合同状态、补充流程变量
        input.getVariables().put("contractAmount", getContractAmount(input.getBusinessKey()));
    }

    @Override
    public void postSubmit(StartProcessOutDto output, WorkflowProcessDto input) {
        // 提交后：更新合同状态为"审批中"
        contractService.updateStatus(input.getBusinessKey(), "APPROVING");
    }
}

@Component
public class ContractApprovalProcessor implements IWorkflowApprovalProcessor {

    @Override
    public String getProcessKey() {
        return "contract_approval";
    }

    @Override
    public void preApproval(CompleteTaskInputDto input) {
        // 审批前：记录审批日志
    }

    @Override
    public void postApproval(List<WorkflowTaskDto> result, CompleteTaskInputDto input) {
        // 审批后：如果流程结束，更新合同状态
        if (result.isEmpty()) {
            contractService.updateStatus(input.getBusinessKey(), "APPROVED");
        }
    }
}
```

---

### 4.3 Feature 3: 流程撤回上一节点

#### 设计目标

在原有 `recallPre` 基础上增强：增加待办同步、业务回调处理、MQ 事件发布。

#### 撤回条件（沿用原逻辑）

1. 上一节点不是会签节点
2. 上一节点是当前用户审批的
3. 当前节点 formKey 配置了 `callBackPre: true`
4. 当前节点与上一节点不是同一个节点

#### 撤回流程

```
recallPre() 增强后:
  ┌────────────┐    ┌──────────────┐    ┌──────────────┐    ┌────────────┐
  │ 校验撤回   │ -> │ 跳转到上一   │ -> │ 同步待办     │ -> │ 调用业务   │
  │ 条件       │    │ 节点         │    │              │    │ 回调处理器 │
  └────────────┘    └──────────────┘    └──────────────┘    └────────────┘
                                                                  │
                                                                  ▼
                                                           ┌────────────┐
                                                           │ 发布撤回   │
                                                           │ MQ事件     │
                                                           └────────────┘
```

#### 业务接入方式

```java
@Component
public class ContractRecallHandler implements IWorkflowRecallHandler {

    @Override
    public String getProcessKey() {
        return "contract_approval";
    }

    @Override
    public void onRecall(WorkflowRecallDto context) {
        // 撤回时回滚业务状态
        contractService.updateStatus(context.getBusinessKey(), "DRAFT");
    }
}
```

---

### 4.4 Feature 4: 节点跳转

#### 设计目标

支持管理员或特殊业务场景下，将流程从当前节点直接跳转到目标任意节点。

#### 跳转原理

利用 Activiti 的 `ActivityImpl` 动态替换出线机制（复用已有的 `gotoAssignActivity` 私有方法）：

1. 清除当前节点的所有出线
2. 创建一条临时出线指向目标节点
3. 完成当前任务（引擎自动沿新出线走到目标节点）
4. 恢复原始出线
5. 同步待办、发布跳转事件

#### REST 接口

```
POST /workflow/jumpToNode
{
    "processInstanceId": "12345",
    "targetTaskDefinitionKey": "managerApproval",
    "comment": "管理员强制跳转",
    "variables": { "skipReason": "紧急处理" }
}
```

---

### 4.5 Feature 5: 抄送审批人

#### 设计目标

支持流程提交/审批时将信息抄送给指定用户，通过站内信 `INotificationRpcService` 实现通知。

#### 抄送触发方式

| 方式 | 说明 |
|------|------|
| **接口主动抄送** | 调用 `POST /workflow/carbonCopy` 或 RPC `sendCarbonCopy()` |
| **提交时抄送** | `genericSubmit` 入参设置 `ccUserIds` |
| **审批时抄送** | `genericApproval` 入参设置 `ccUserIds` |
| **formKey 静态配置** | 在流程设计器的 formKey 中配置 `carbonCopy` 段（预留扩展） |

#### formKey 抄送配置示例

```json
{
    "activiti": {
        "callBackPre": true,
        "notice": { "addTodo": true, "appPush": true },
        "carbonCopy": {
            "enabled": true,
            "ccUserIds": "1001,1002",
            "ccRoles": "部门经理",
            "template": "您收到一条审批抄送"
        }
    }
}
```

---

## 五、API 接口清单

### 5.1 REST 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/workflow/genericSubmit` | 通用流程提交 |
| `POST` | `/workflow/genericApproval` | 通用流程审批 |
| `POST` | `/workflow/recallProcess` | 撤回至上一节点 |
| `POST` | `/workflow/jumpToNode` | 节点跳转 |
| `POST` | `/workflow/carbonCopy` | 发送抄送通知 |

### 5.2 RPC 接口（IWorkFlowRpcService 新增）

| 方法 | 说明 |
|------|------|
| `genericSubmit(GenericSubmitInput)` | 通用提交 |
| `genericApproval(GenericApprovalInput)` | 通用审批 |
| `recallProcess(RecallProcessInput)` | 撤回 |
| `jumpToNode(JumpToNodeInput)` | 跳转 |
| `sendCarbonCopy(CarbonCopyInput)` | 抄送 |

---

## 六、业务接入指南

业务模块仅需引入 `dusk-module-workflow-shared` 依赖，并实现对应接口：

| 需求 | 实现接口 | 必要性 |
|------|---------|--------|
| 监听流程事件 | `IWorkflowListener` | 按需 |
| 提交时自定义逻辑 | `IWorkflowSubmitProcessor` | 按需 |
| 审批时自定义逻辑 | `IWorkflowApprovalProcessor` | 按需 |
| 撤回时业务回滚 | `IWorkflowRecallHandler` | 按需 |
| 站内信通知能力 | `INotificationRpcService` | 需通知模块实现 |

所有接口均使用 **default method**，业务方只需覆写关心的方法即可。

---

## 七、配置说明

### application-dev.yml 新增配置

```yaml
# 工作流MQ事件配置
workflow:
  event:
    exchange: workflow.event.exchange
    queue: workflow.event.queue
```

### RabbitMQ 开关

```yaml
spring:
  rabbitmq:
    is-enabled: false  # 设为true启用MQ事件推送
```

MQ 未启用时，事件仍通过 Spring ApplicationEvent 在进程内传播，不影响 `IWorkflowListener` 的使用。

---

## 八、变更文件清单

### 新建文件（17个）

| 文件 | 模块 | 说明 |
|------|------|------|
| `WorkflowEventType.java` | shared | 事件类型枚举 |
| `WorkflowEventDto.java` | shared | 事件DTO |
| `IWorkflowListener.java` | shared | 事件监听器接口 |
| `IWorkflowSubmitProcessor.java` | shared | 提交处理器接口 |
| `IWorkflowApprovalProcessor.java` | shared | 审批处理器接口 |
| `IWorkflowRecallHandler.java` | shared | 撤回处理器接口 |
| `INotificationRpcService.java` | shared | 站内信RPC接口 |
| `GenericSubmitInput.java` | shared | 通用提交入参 |
| `GenericApprovalInput.java` | shared | 通用审批入参 |
| `RecallProcessInput.java` | shared | 撤回入参 |
| `JumpToNodeInput.java` | shared | 跳转入参 |
| `CarbonCopyInput.java` | shared | 抄送入参 |
| `WorkflowRecallDto.java` | shared | 撤回上下文 |
| `WorkflowMqConfig.java` | impl | MQ配置 |
| `WorkflowEventPublisher.java` | impl | 事件发布器 |
| `WorkflowEventConsumer.java` | impl | 事件消费器 |
| `WorkflowSpringEvent.java` | impl | Spring事件包装 |
| `WorkflowCarbonCopyService.java` | impl | 抄送服务 |
| `WorkflowProcessorRegistry.java` | impl | 处理器注册中心 |

### 修改文件（6个）

| 文件 | 变更内容 |
|------|---------|
| `IWorkFlowRpcService.java` | 新增5个RPC方法 |
| `IWorkflowService.java` | 新增 `recallPre(id, businessData)` 重载 |
| `WorkflowServiceImpl.java` | 实现所有新方法，注入新依赖，埋点事件发布 |
| `WorkflowRpcServiceImpl.java` | 新增5个RPC委托方法 |
| `WorkflowController.java` | 新增5个REST端点 |
| `TaskFormKey.java` | 新增 `CarbonCopy` 内部类 |
| `application-dev.yml` | 新增 MQ 配置 |


