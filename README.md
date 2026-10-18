# dusk-module-workflow

DuskMS 的 **工作流（BPM）服务**，基于 Flowable 8 引擎，为业务系统提供统一的流程建模、发起、审批与流程图能力。

- 聚合坐标：`dusk-module:dusk-module-workflow-group:1.2.0`
- 服务坐标：`com.dusk:dusk-module-workflow:1.2.0`
- 契约坐标：`com.dusk:dusk-module-workflow-shared:1.2.0`
- 端口：dev/sit `53199`

## 核心功能

- **流程模型**：BPMN 模型 CRUD、SVG 导入/导出、部署、版本回退、按 key 删除。
- **流程定义**：分页查询、XML/图片资源获取、删除部署。
- **流程运行**：发起流程（可提交并完成首节点）、完成任务/审批、驳回/撤回上一节点、更新处理人、动态更新变量并重派待办。
- **任务查询**：按流程实例查任务、任务历史、关联节点计算（支持条件表达式）。
- **流程图**：基于流程定义或运行实例生成 PNG 跟踪图（中文渲染、多节点高亮）。
- **多租户**：所有引擎查询按 `tenantId` 过滤。
- **权限**：`@Authorize` + `Pages.Activiti.*` 权限树。
- **跨模块**：Dubbo RPC 对外暴露流程能力；审批时联动 auth 生成待办（`ITodoRpcService`）。

## 技术栈

Java 25 · Spring Boot 4.0.8 · Flowable 8.0.0（由 Activiti 迁移而来）· Spring Data JPA · Dubbo 3.3.6 · Nacos · PostgreSQL ·
`dusk-common-core` / `dusk-common-rpc` / `dusk-common-mqs` · MapStruct · Lombok。

## 模块结构

```text
dusk-module-workflow/
├── dusk-module-workflow/                可运行服务
│   └── src/main/java/com/dusk/module/workflow/
│       ├── controller/                  WorkflowController / ModelController / ProcessController / TaskController
│       ├── service/ + impl/             IWorkflowService、IModelService、IProcessService、IActTaskService
│       ├── core/config/                 ActivitiConfig（引擎定制）、SnowFlakeGenerator
│       ├── authorization/               ActivitiAuthProvider（Pages.Activiti.*）
│       ├── dto/ mapper/ constant/ utils/
│   └── src/main/java/org/activiti/image/impl/   内嵌流程图渲染器
└── dusk-module-workflow-shared/         跨模块契约（com.dusk.workflow）
    ├── service/                         IWorkFlowRpcService、IBusinessWorkFlowService
    ├── dto/                             StartProcessInputDto、CompleteTaskInputDto、WorkflowTaskDto…
    ├── enums/                           AssigneeTypeEnum
    └── IProcessDesHolder.java           业务 DTO 填充流程描述
```

## REST 接口

| Controller           | 前缀        | 能力                                                |
|----------------------|-------------|-----------------------------------------------------|
| `WorkflowController` | `/workflow` | 流程图、任务历史、撤回校验/撤回、关联节点、任务查询 |
| `ModelController`    | `/model`    | 模型 CRUD、SVG 导入、部署、版本回滚、按 key 删除    |
| `ProcessController`  | `/process`  | 流程定义分页、资源图片/XML、删除部署                |
| `TaskController`     | `/task`     | 任务分页、当前任务图                                |

模型与流程管理接口通过 `@Authorize(ActivitiAuthProvider.PAGES_...)` 保护。

## RPC 契约

`IWorkFlowRpcService`（shared）定义：

- 发起：`startProcess`、`startProcessAndCompleteFirst`
- 审批：`completeTask`、`completeTaskByProcessId`
- 查询：`getTask`、`getTaskList`、`getTasksByProcess`
- 描述与关联：`getProcessDescription`、`getRelateTask`
- 变更：`updateTaskAssignee`、`updateFlowVariables`
- 运维：删除实例、检查流程是否结束

业务模块接入方式：

1. 实现 `IBusinessWorkFlowService`，声明 `getProcessKey()`，可选提供可审批人列表。
2. 业务 DTO 实现 `IProcessDesHolder`，由 `FlowUtils.setProcessDes(...)` 回填流程描述。

## 多租户与权限

- 租户上下文：`TenantContextHolder`；所有新增 Flowable 查询必须带 `tenantId`。
- 用户上下文：`LoginUserIdContextHolder` / `SecurityUtils` / `UserContext`。
- 权限点：`ActivitiAuthProvider` 注册 `Pages.Activiti.*`。

## 快速开始

### 前置条件

- JDK 25、Maven 3.9+
- GitHub Packages PAT
- PostgreSQL、Nacos

### 构建与运行

```bash
export GH_PAT=<read:packages PAT>

# 构建（含 JaCoCo 98% 分支覆盖率门禁）
mvn -B -ntp -U -s settings.xml clean package

# 本地运行
mvn -s settings.xml spring-boot:run -Dspring-boot.run.profiles=dev

# 测试
mvn -s settings.xml test
```

### 配置

- 默认 profile `sit`；dev 端口 53199，数据源默认 `jdbc:postgresql://192.168.44.99:5432/crux-workflow`，Nacos
  `dusk.com:8848`。
- 可覆盖：`SERVER_PORT`、`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、`SPRING_RABBITMQ_ISENABLED`。
- dev 关闭 Flyway；Flowable 自身表由 `databaseSchemaUpdate=true` 自动创建。
- 异步执行器默认关闭（`activiti.async-executor-activate: false`）。

## 数据库

- 业务配置：`flyway_schema_history_workflow`（当前 dev 未启用）。
- Flowable 表：`ACT_*`，由引擎自动管理。
- 流程变量 key 统一定义在 `FlowableConstants`（`activiti`、`activiTitle`、`activiTypeName`、`activiBusinessType`、
  `activiFilterStation`、`activiStarter`）。

## 测试

- 19 个测试类，覆盖 Controller、Service、FlowUtils、引擎配置与 RPC。
- JaCoCo 在 `verify` 阶段要求 BUNDLE 分支覆盖率 ≥ 98%（排除内嵌渲染器、启动类、MapStruct 生成类）。

## 已知限制

- 引擎序列化白名单尚未补齐（`serialize-check-status: DISABLE`）。
- `removeModelById` 接口暂被注释。
- 撤回（`recallPre`）要求上一节点为普通节点、由当前用户审批且当前节点允许撤回。
- `org/activiti/image/impl` 是内嵌的第三方渲染器副本（AWT），不属于业务逻辑，JaCoCo 已排除。

## 许可证

[Apache License 2.0](LICENSE)
