# Dusk Module Workflow

DuskMS 平台的工作流（BPM）微服务，基于 Flowable 引擎提供流程模型管理、流程发布、任务审批、流程撤回与跨模块待办联动能力，解决业务系统内重复开发审批流程的问题。

[![Java](https://img.shields.io/badge/Java-21-orange)](https://www.oracle.com/java/technologies/downloads/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-brightgreen)](https://spring.io/projects/spring-boot)
[![Flowable](https://img.shields.io/badge/Flowable-7.2-blue)](https://www.flowable.com/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![CI](https://github.com/MouckIS/dusk-module-workflow/actions/workflows/ci.yml/badge.svg)](https://github.com/MouckIS/dusk-module-workflow/actions/workflows/ci.yml)

## 核心功能

**已实现**

- **流程模型管理**：BPMN 2.0 模型的创建、SVG 导入/导出、版本回退、按 key 删除。
- **流程发布**：模型部署为流程定义，流程定义分页查询、资源（XML / 图）获取。
- **流程发起与审批**：发起流程（可提交并完成首节点）、任务完成/审批、驳回与撤回上一节点。
- **任务查询**：按流程实例查询任务、任务历史、关联节点计算（支持条件表达式动态解析）。
- **流程图渲染**：基于流程定义或运行实例生成 PNG 跟踪图（自定义中文渲染，支持多节点高亮）。
- **多租户隔离**：所有引擎查询按 `tenantId` 过滤，模型/流程按租户隔离。
- **接口鉴权**：基于 `@Authorize` + 权限树（`Pages.Activiti.*`）控制模型/流程/任务管理接口。
- **跨模块集成**：通过 Dubbo RPC 对外暴露工作流能力；发起/审批时联动生成待办（`ITodoRpcService`）。

**计划中（Todo）**

- 引擎序列化安全检查白名单（当前在 `application.yml` 中临时禁用，待补充自定义白名单）。
- Flyway 管理 workflow 自身表结构（当前 dev 环境关闭，由 Flowable `databaseSchemaUpdate=true` 自动建表）。
- 恢复模型删除接口（`removeModelById` 暂被注释）。

### 架构与模块设计

Maven 多模块仓库：

| 模块 | 职责 |
| --- | --- |
| `dusk-module-workflow` | 可运行的工作流服务（Spring Boot 应用），实现 REST API 与引擎逻辑 |
| `dusk-module-workflow-shared` | 共享 API 契约：RPC 接口（`IWorkFlowRpcService`、`IBusinessWorkFlowService`）与请求/响应 DTO，供其他服务依赖 |

分层结构：`Controller` → `Service` → Flowable 引擎服务（`RepositoryService` / `RuntimeService` / `TaskService` / `HistoryService` / `FormService`）。

典型业务流程：

```
业务系统发起流程 ──(Dubbo RPC)──► startProcess ──► 引擎创建流程实例 + 生成首节点待办
                                                    │
                    审批人完成任务 ◄── 生成下一节点待办 ◄── 审批/撤回 ──► 引擎推进流程
                                                    ▼
                                              流程结束，无待办
```

- 引擎定制：`ActivitiConfig` 注入中文渲染字体、雪花 ID 生成器，并共享 Spring 事务与数据源；`org.activiti.image.impl` 下内嵌了流程跟踪图渲染器。
- 关键依赖：`dusk-common-core`（多租户、鉴权、通用 DTO）、`dusk-common-rpc`（用户/待办 RPC）、`dusk-common-mqs`。父 POM 与这些依赖托管在 GitHub Packages，需配置访问凭证。

## 快速入门与部署

### 环境准备

- **JDK 21**（Maven 编译目标）
- **Maven 3.9+**（本仓库未内置 Maven Wrapper）
- **PostgreSQL**（Flowable 引擎数据表）
- **Nacos**（配置中心 + 服务注册/发现，Dubbo 注册中心）
- **GitHub Packages 访问凭证**：父 POM（`dusk-module-parent` / `dusk-module-shared-parent`）与 `dusk-common-*` 依赖不在 Maven Central，项目根目录已提供 `settings.xml`（含 `github-dusk-*` 仓库与认证配置），并通过 `.mvn/maven.config` 自动生效。构建前只需设置环境变量：`export GH_PAT=<含 read:packages 权限的 GitHub PAT>`。

### 配置文件修改

默认激活 profile 为 `sit`（`application.yml`）；`application-dev.yml` 中的连接参数均可通过环境变量覆盖：

| 环境变量 | 默认值（dev） | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `sit` | 激活的 profile |
| `SERVER_PORT` | `53199` | 服务端口 |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://dusk.com:5432/crux-workflow` | 数据库连接 |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `postgres` / 默认口令 | 数据库账号密码 |
| `spring.cloud.nacos.server-addr` | `dusk.com:8848` | Nacos 地址 |
| `SPRING_RABBITMQ_ISENABLED` | `false` | 是否启用 RabbitMQ |

本地开发请通过环境变量或本地 `application-*.yml` 覆盖账号密码，不要修改提交默认口令。

### 启动运行

```bash
# 1. 克隆代码
git clone https://github.com/MouckIS/dusk-module-workflow.git
cd dusk-module-workflow

# 2. 设置 GitHub Packages 访问凭证（解析内部 dusk-* 依赖）
export GH_PAT=<含 read:packages 权限的 GitHub PAT>

# 3. 编译构建（跳过测试）
mvn -U -DskipTests clean package

# 4. 运行服务
cd dusk-module-workflow
mvn spring-boot:run
```

运行测试：

```bash
mvn test                                        # 全部测试
mvn -pl dusk-module-workflow test -Dtest=DuskWorkflowApplicationTests   # 单个测试类
```

## 生产与容器化部署

- 本仓库不包含 `Dockerfile`；中间件栈（Postgres、Redis、Nacos、MinIO、EMQX、RabbitMQ）由 DuskMS 工作区的 `dusk/docker/docker-compose.yaml` 提供，一键启动：`cd dusk/docker && docker compose up -d`。
- 发布流水线：GitHub Actions（`.github/workflows/publish.yml`）在 push 到 `main` 时构建并 `mvn deploy` 发布 SNAPSHOT 到 GitHub Packages；打版本标签（如 `v1.0.0`）时发布 Release 版本。
- 线上演示环境：暂无公开演示地址。

## 开发者指引

- **接口文档**：所有接口均使用 OpenAPI（Swagger）注解（`@Tag` / `@Schema`）标注；文档 UI 由 `dusk-common` 依赖提供，默认路径一般为 `/swagger-ui.html`（或 `/doc.html`），实际以部署环境网关配置为准。
- **贡献指南**：仓库根目录暂无 `CONTRIBUTING.md`，协作规范见工作区根目录 `AGENTS.md`。提交请遵循 Conventional Commits（`type(scope): subject`），通过 GitHub Issues 报告问题、Pull Request 提交代码；CI 会在 PR 上自动执行构建检查。

## License

本项目采用 [Apache License 2.0](LICENSE)。
