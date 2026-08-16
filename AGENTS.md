# AGENTS.md

本文件为 AI 编码助手与本仓库协作者提供项目上下文与开发指引,是 `dusk-module-workflow` 仓库的权威说明。工作区级约定见 DuskMS 根目录的 `AGENTS.md`。

## 项目概述

`dusk-module-workflow` 是 DuskMS 平台的工作流(BPM)微服务,基于 **Flowable 7.2.0** 引擎,为业务系统提供统一的流程能力:

- **流程模型管理**:BPMN 2.0 模型的新建、SVG 导入/导出、部署、版本回退、按 key 删除。
- **流程发布**:模型部署为流程定义,流程定义分页查询、资源(XML/图)获取。
- **流程发起与审批**:发起流程(可"提交并完成首节点")、任务完成/审批、驳回/撤回上一节点、更新任务处理人、动态更新流程变量并联动重派待办。
- **任务查询**:按流程实例查询任务、任务历史、关联节点计算(支持条件表达式动态解析)。
- **流程图渲染**:基于流程定义或运行实例生成 PNG 跟踪图(自定义中文渲染,支持多节点高亮)。
- **多租户隔离**:所有引擎查询按 `tenantId` 过滤,模型/流程按租户隔离。
- **接口鉴权**:基于 `@Authorize` + 权限树(`Pages.Activiti.*`)保护模型/流程/任务管理接口。
- **跨模块集成**:通过 Dubbo RPC 对外暴露工作流能力;发起/审批时联动生成待办(`ITodoRpcService`)。

## 技术栈

| 项 | 选型 |
| --- | --- |
| 语言 | Java 21 |
| 框架 | Spring Boot 3 |
| 工作流引擎 | Flowable 7.2.0(由 Activiti 迁移而来,`*ServiceImpl` 中残留的 Activiti import 已注释) |
| 构建 | Maven 3.9+(多模块,无 Maven Wrapper) |
| 数据库 | PostgreSQL(Druid 连接池) |
| 注册/配置中心 | Nacos(Dubbo 注册中心 + 配置中心) |
| RPC | Dubbo |
| 其他 | Lombok、MapStruct、dusk-common-core / dusk-common-rpc / dusk-common-mqs |

## 仓库结构

Maven 多模块仓库(聚合 artifact:`dusk-module-workflow-group`,packaging `pom`):

```
dusk-module-workflow/
├── dusk-module-workflow/            # 可运行的 Spring Boot 服务
│   └── src/main/java/com/dusk/module/workflow/
│       ├── controller/              # WorkflowController /workflow、ModelController /model、ProcessController /process、TaskController /task
│       ├── service/                 # IWorkflowService、IModelService、IProcessService、IActTaskService
│       ├── service/impl/            # WorkflowServiceImpl、WorkflowRpcServiceImpl、ModelServiceImpl、ProcessServiceImpl、ActTaskServiceImpl
│       ├── core/config/             # ActivitiConfig(引擎定制)、SnowFlakeGenerator(雪花 ID)
│       ├── authorization/           # ActivitiAuthProvider(权限树 Pages.Activiti.*)
│       ├── dto/                     # 模块内 DTO 与入参对象(GetModelsInput、RelatedNodeInfo、AppPushDto 等)
│       ├── mapper/                  # WorkflowMapper(MapStruct)
│       ├── constant/                # FlowableConstants(流程变量 key)、ModelDataJsonConstants
│       └── utils/                   # FlowUtils(setProcessDes 等)
│   └── src/main/java/org/activiti/image/impl/   # 内嵌的流程图渲染器(Activiti 的 DefaultProcessDiagramGenerator/Canvas 副本,覆盖 Flowable 默认实现)
└── dusk-module-workflow-shared/     # 可发布的公共 API 契约
    └── src/main/java/com/dusk/workflow/
        ├── service/                 # IWorkFlowRpcService、IBusinessWorkFlowService
        ├── dto/                     # WorkflowProcessDto、StartProcessInputDto、CompleteTaskInputDto、WorkflowTaskDto、WorkflowTaskDetailDto、WorkflowTaskHistoryDto、UpdateFlowVariablesInput 等
        ├── enums/                   # AssigneeTypeEnum
        └── IProcessDesHolder.java   # 业务 DTO 实现该接口,配合 FlowUtils 填充流程描述
```

## 架构与请求流

### 分层

`Controller(REST) → Service 接口 → ServiceImpl → Flowable 引擎服务(RepositoryService / RuntimeService / TaskService / HistoryService / FormService)`

引擎服务均以 `@Autowired(required = false)` 注入;所有 Controller 继承 `CruxBaseController`(来自 `dusk-common-core`)。

### REST 接口一览

- **WorkflowController**(`/workflow`,主 API):流程图读取 `/resource/{processId}`、任务历史 `/getTaskHistory`、多流程历史 `/getTaskHistories`、撤回校验 `/checkProcessCanRecallPre`、撤回 `/recallPre`、关联任务/节点计算、任务查询等。
- **ModelController**(`/model`):模型 CRUD、SVG 导入、部署、版本回滚、按 key 删除。受 `@Authorize(ActivitiAuthProvider.PAGES_...)` 保护。
- **ProcessController**(`/process`):流程定义分页、资源图片/XML、删除部署。
- **TaskController**(`/task`):任务分页列表、任务当前图。

### RPC / 跨模块集成

- **RPC 契约**:`IWorkFlowRpcService`(`dusk-module-workflow-shared`)定义 Dubbo 接口:发起流程(`startProcess` / `startProcessAndCompleteFirst`)、完成任务(`completeTask` / `completeTaskByProcessId`)、删除实例、检查流程结束、任务查询(`getTask` / `getTaskList` / `getTasksByProcess`)、流程描述(`getProcessDescription`)、关联任务计算(`getRelateTask`)、更新处理人(`updateTaskAssignee`)、更新流程变量并重派待办(`updateFlowVariables`)。
- **双契约合一**:本地 `IWorkflowService` **继承** `IWorkFlowRpcService`,`WorkflowServiceImpl` 一个类同时实现 REST 路径与 RPC 契约。修改任一契约需保持同步。
- **暴露方式**:`WorkflowRpcServiceImpl`(`@DubboService`)暴露 RPC,内部通过 `@DubboReference` 代理到本地 `WorkflowServiceImpl`。Dubbo 扫描包:`com.dusk.module.workflow.service`。
- **业务模块接入**:
  - 实现 `IBusinessWorkFlowService`(shared):声明 `getProcessKey()`,可选实现 `getWorkflowApprovers()` 提供可审批人列表。
  - 业务 DTO 实现 `IProcessDesHolder`,经 RPC 调用 `getProcessDescription` 由 `FlowUtils.setProcessDes(...)` 回填流程描述。
- **下游依赖**:`WorkflowServiceImpl` 通过 `dusk-common-rpc` 的 Dubbo 引用调用 `IUserRpcService`(用户信息)与 `ITodoRpcService`(任务指派时生成待办)。

### 多租户与鉴权

- 多租户:`TenantContextHolder`(来自 `dusk-common-core`);**所有新增 Flowable 查询必须按 tenantId 过滤**,模型/流程创建时写入当前租户 id。
- 用户上下文:`LoginUserIdContextHolder` / `SecurityUtils` / `UserContext`。
- 权限:`ActivitiAuthProvider` 注册 `Pages.Activiti.*` 权限树,REST 端点以 `@Authorize(...)` 保护。

### Flowable 定制

- `core/config/ActivitiConfig`:替换默认引擎配置——中文字体(宋体)用于图渲染、`SnowFlakeGenerator` 作为 ID 生成器、共享 Spring `DataSource` 与事务管理器、`databaseSchemaUpdate=true`(Flowable 自管表结构)。
- `core/config/SnowFlakeGenerator`:Flowable 实体的雪花 ID 生成器。
- `org/activiti/image/impl/DefaultProcessDiagramGenerator` / `DefaultProcessDiagramCanvas`:Activiti 图生成器副本(覆盖 Flowable 默认),用于渲染流程/任务 PNG 跟踪图。

### 数据映射

`WorkflowMapper` 是 MapStruct 映射器(`WorkflowMapper.INSTANCE`),负责 Flowable 实体(`Model`、`ProcessDefinition`、`FormProperty`)与 shared DTO 到视图/RPC DTO 的转换,并生成待办推送所需的 `AppPushDto`。

### 流程变量约定(FlowableConstants)

新增流程变量时先在 `FlowableConstants` 定义 key,保持待办展示一致:

- `TYPE = "activiti"` — 待办类型(前端跳转判断)
- `TITLE = "activiTitle"` — 待办标题
- `TYPE_NAME = "activiTypeName"` — 待办类型名
- `BUSINESS_TYPE = "activiBusinessType"` — 业务类型
- `FILTER_STATION = "activiFilterStation"` — 是否过滤场站
- `STARTER = "activiStarter"` — 发起人
- 节点类型常量:`StartEvent` / `UserTask` / `EndEvent`
- 审批人占位符:`{{directLeader}}`(直属上级)

## 构建、测试与运行

### 前置条件

- JDK 21(编译目标;CI 用 temurin 21,本地默认 JVM 较新时注意切换 toolchain)
- Maven 3.9+
- PostgreSQL、Nacos(Dubbo 注册中心)
- **GitHub Packages 访问凭证**:父 POM(`dusk-module-parent` / `dusk-module-shared-parent`)与 `dusk-common-*` 依赖不在 Maven Central。项目根目录已提供 `settings.xml`,经 `.mvn/maven.config`(`-s ${maven.multiModuleProjectDirectory}/settings.xml`)自动生效。构建前只需:

```bash
export GH_PAT=<含 read:packages 权限的 GitHub PAT>

# 构建整个 reactor(跳过测试)
mvn -U -DskipTests clean package

# 运行服务(Spring Boot)
mvn -pl dusk-module-workflow -am spring-boot:run
# 或: cd dusk-module-workflow && mvn spring-boot:run

# 运行全部测试
mvn test

# 运行单个测试类(在服务模块内)
mvn -pl dusk-module-workflow test -Dtest=DuskWorkflowApplicationTests
```

### 配置与 Profile

- 默认 profile 为 `sit`(`application.yml`);dev profile(`application-dev.yml`)端口 53199,连 `dusk.com:5432` / Nacos `dusk.com:8848`。
- 关键连接参数均可通过环境变量覆盖:`SERVER_PORT`、`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`SPRING_RABBITMQ_ISENABLED`、Nacos 地址等。
- **Flyway 在 dev 环境禁用**(`spring.flyway.enabled: false`),workflow 自身表由 Flowable `databaseSchemaUpdate=true` 自动创建(`dusk-module-workflow/pom.xml` 中定义的 Flyway 属性与表 `flyway_schema_history_workflow` 当前未实际使用;优先用环境变量覆盖,勿直接改 pom 中的库口令)。
- 测试:目前仅 `DuskWorkflowApplicationTests`(`@SpringBootTest`,大部分已注释),会启动完整 Spring 上下文,需可访问的 DB/Nacos,否则 Nacos 配置导入需设为可选。

## 开发约定

- **提交信息**:Conventional Commits(`type(scope): subject`),scope 指模块/领域。
- **代码风格**:4 空格缩进;`com.dusk.<module>` 包名;Lombok + MapStruct;类名 `UpperCamelCase`,方法/字段 `camelCase`;测试类 `*Tests.java` / `*Test.java`(JUnit 5)。
- **契约同步**:本地 `IWorkflowService` 与 shared 的 `IWorkFlowRpcService` 必须保持同步(`WorkflowServiceImpl` 同时实现两者)。
- **多租户**:所有新增查询必须 tenant-scoped。
- **接口文档**:所有接口使用 OpenAPI(Swagger)注解(`@Tag` / `@Schema`);文档 UI 由 `dusk-common` 提供,默认路径一般为 `/swagger-ui.html` 或 `/doc.html`。
- **凭证安全**:禁止提交明文凭据;dev 默认值在 `application-dev.yml`,通过环境变量覆盖。

## 发布

- `mvn deploy` 将 SNAPSHOT 发布到 GitHub Packages(push 到 `main` 时由 CI 触发);打版本标签(带或不带 `v` 前缀)发布 Release。
- 发布需要含 `write:packages` 权限的 PAT。

## 已知限制与待办

- 引擎序列化安全:白名单当前在 `application.yml` 中临时禁用,待补充自定义白名单。
- Flyway 管理 workflow 自身表结构尚未启用(dev 由 Flowable 自动建表)。
- `removeModelById`(模型删除接口)暂被注释,未恢复。
- 流程撤回(`recallPre`)依赖 `checkProcessCanRecallPre` 校验:上一节点必须是普通节点(非并行网关/会签)、且由当前用户审批、当前节点允许撤回。
