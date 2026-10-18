# AGENTS.md — dusk-module-workflow

> 工作流服务。本地 `IWorkflowService` 与 shared 的 `IWorkFlowRpcService` 双契约合一，改任一处必须同步。
> 用户文档见 [`README.md`](README.md)，工作空间约束见 [`../AGENTS.md`](../AGENTS.md)。

## 1. 项目定位

基于 **Flowable 8.0.0** 的 BPM 微服务：流程模型、部署、发起、审批、任务查询、流程图、多租户与权限。由 Activiti 迁移而来，部分
`org.activiti` 包是内嵌渲染器副本。

## 2. 技术栈

| 项          | 选型                                            |
|-------------|-------------------------------------------------|
| 语言 / 框架 | Java 25、Spring Boot 4.0.8                      |
| 工作流      | Flowable 8.0.0（BOM 管理）                      |
| 持久化      | PostgreSQL（Druid）、JPA                        |
| 注册 / 配置 | Nacos                                           |
| RPC         | Dubbo 3.3.6                                     |
| 其他        | Lombok、MapStruct、dusk-common-core / rpc / mqs |

## 3. 仓库结构

```text
dusk-module-workflow/
├── dusk-module-workflow/
│   └── src/main/java/com/dusk/module/workflow/
│       ├── controller/       WorkflowController(/workflow)、ModelController(/model)、ProcessController(/process)、TaskController(/task)
│       ├── service/          IWorkflowService、IModelService、IProcessService、IActTaskService
│       ├── service/impl/     WorkflowServiceImpl、WorkflowRpcServiceImpl、ModelServiceImpl、ProcessServiceImpl、ActTaskServiceImpl
│       ├── core/config/      ActivitiConfig、SnowFlakeGenerator
│       ├── authorization/    ActivitiAuthProvider（Pages.Activiti.*）
│       ├── dto/ mapper/ constant/ utils/
│       └── DuskWorkflowApplication.java
│   └── src/main/java/org/activiti/image/impl/   流程图渲染器副本
└── dusk-module-workflow-shared/
    └── com/dusk/workflow/
        ├── service/          IWorkFlowRpcService、IBusinessWorkFlowService
        ├── dto/              WorkflowProcessDto、StartProcessInputDto、CompleteTaskInputDto、WorkflowTaskDto…
        ├── enums/            AssigneeTypeEnum
        └── IProcessDesHolder.java
```

## 4. 架构与请求流

`Controller(REST) → Service → ServiceImpl → Flowable 引擎服务（RepositoryService / RuntimeService / TaskService / HistoryService / FormService）`

- 引擎服务使用 `@Autowired(required = false)` 注入；Controller 继承 `CruxBaseController`。
- **双契约合一**：`IWorkflowService` 继承 `IWorkFlowRpcService`，`WorkflowServiceImpl` 同时实现 REST 与 RPC 契约。
- `WorkflowRpcServiceImpl`（`@DubboService`）暴露 RPC，内部通过 `@DubboReference` 代理到本地 `WorkflowServiceImpl`。
- 下游依赖：`IUserRpcService`（用户信息）、`ITodoRpcService`（生成待办）。

## 5. 多租户与鉴权

- `TenantContextHolder`：所有新增 Flowable 查询必须按 `tenantId` 过滤；模型/流程创建时写入当前租户。
- `LoginUserIdContextHolder` / `SecurityUtils` / `UserContext`：取当前用户。
- REST 端点用 `@Authorize(ActivitiAuthProvider.PAGES_...)` 保护。

## 6. Flowable 定制

- `core/config/ActivitiConfig`：中文字体渲染、`SnowFlakeGenerator` ID 生成器、共享 Spring DataSource/事务、
  `databaseSchemaUpdate=true`。
- `core/config/SnowFlakeGenerator`：Flowable 实体 ID。
- `org/activiti/image/impl/DefaultProcessDiagramGenerator` / `DefaultProcessDiagramCanvas`：覆盖 Flowable 默认图渲染。

## 7. 流程变量约定

新增变量先在 `FlowableConstants` 定义：

| 常量             | 值                    | 用途         |
|------------------|-----------------------|--------------|
| `TYPE`           | `activiti`            | 待办类型     |
| `TITLE`          | `activiTitle`         | 待办标题     |
| `TYPE_NAME`      | `activiTypeName`      | 待办类型名   |
| `BUSINESS_TYPE`  | `activiBusinessType`  | 业务类型     |
| `FILTER_STATION` | `activiFilterStation` | 是否过滤场站 |
| `STARTER`        | `activiStarter`       | 发起人       |

节点类型：`StartEvent` / `UserTask` / `EndEvent`；审批人占位符：`{{directLeader}}`。

## 8. 构建 / 测试 / 运行

```bash
export GH_PAT=<read:packages PAT>

mvn -B -ntp -U -s settings.xml -DskipTests clean package
mvn -s settings.xml spring-boot:run -Dspring-boot.run.profiles=dev
mvn -s settings.xml test

# 单个测试类
mvn -s settings.xml test -Dtest=WorkflowServiceImplTest
```

- 19 个测试类；`DuskWorkflowApplicationTests` 是 `@SpringBootTest`，需要可访问的 DB/Nacos。
- JaCoCo：`verify` 阶段 BUNDLE 分支覆盖率 ≥ 98%，排除 `org/activiti/image/impl/**`、启动类、`*MapperImpl`。
- 端口 53199；dev 关闭 Flyway，Flowable 自动建表。

## 9. 常见陷阱

1. **契约同步**：改 `IWorkFlowRpcService` 必须同时改 `IWorkflowService` / `WorkflowServiceImpl` /
   `WorkflowRpcServiceImpl`。
2. **多租户遗漏**：新增 Flowable 查询若不带 `tenantId`，会串租户数据。
3. **流程变量 key 不一致**：待办与前端跳转依赖 `FlowableConstants`，不要手写字符串。
4. **`databaseSchemaUpdate=true`**：Flowable 自动管理 `ACT_*` 表；不要手工改表结构。
5. **序列化白名单未完成**：`serialize-check-status: DISABLE` 是临时配置。
6. **`removeModelById` 未启用**：调用前确认。
7. **撤回规则**：依赖 `checkProcessCanRecallPre`，修改撤回逻辑必须保持三项校验。
8. **AWT 渲染器**：`org.activiti` 包是第三方副本，修改前确认不会与 Flowable 8 冲突。
9. **测试覆盖率门槛高**：新增分支必须补测试，否则 `mvn verify` 失败。
