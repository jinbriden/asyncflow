# 前后端协作约定

本文定义 AsyncFlow 后端与独立前端仓库之间的协作边界。前端项目建议使用独立仓库 `asyncflow-web`，后端项目继续由本仓库维护。

## 1. 契约来源

前端只依赖公开 HTTP 契约，不依赖 Java 实体、数据库表、Redis 或 RabbitMQ。

后端启动后提供：

- 前端 OpenAPI JSON：`http://localhost:8080/v3/api-docs/frontend`
- Swagger UI：`http://localhost:8080/swagger-ui.html`

`/v3/api-docs/frontend` 是前端代码生成、Mock 和联调的机器可读来源。README 示例用于人工阅读，不能替代 OpenAPI 契约。

前端可以保存一份契约快照用于本地 Mock 和类型生成：

```powershell
New-Item -ItemType Directory -Force openapi | Out-Null
Invoke-WebRequest `
  'http://localhost:8080/v3/api-docs/frontend' `
  -OutFile 'openapi/asyncflow-frontend.json'
```

使用 `openapi-typescript` 的前端项目可以生成 TypeScript 类型：

```powershell
npx openapi-typescript openapi/asyncflow-frontend.json -o src/api/generated/schema.d.ts
```

生成目录只存放生成代码，禁止手工修改；业务封装应放在 `src/api/services`。

## 2. 前端可用的业务接口

前端契约包含：

- `POST /api/tasks`：提交报表任务
- `GET /api/tasks`：分页查询报表任务
- `GET /api/tasks/{taskId}`：查询任务详情
- `GET /api/tasks/{taskId}/events`：查询处理记录
- `GET /api/tasks/{taskId}/result`：下载成功生成的 CSV
- `POST /api/tasks/{taskId}/cancel`：取消允许取消的任务

以下能力不属于普通业务前端契约：

- `POST /internal/tasks/{taskId}/retry`
- `POST /internal/tasks/{taskId}/compensate`
- `simulateFailures` 故障注入字段
- RabbitMQ、Redis、MySQL 和 Grafana 管理接口

普通前端不得保存或发送 `X-Internal-Token`。

## 3. REPORT 请求约定

当前前端只提交 `REPORT` 类型。请求示例：

```json
{
  "type": "REPORT",
  "payload": {
    "reportName": "regional-sales",
    "requestedBy": "qa@example.com",
    "records": [
      {
        "orderId": "SO-1001",
        "region": "East",
        "product": "Keyboard",
        "quantity": 2,
        "unitPrice": 199.50
      }
    ]
  },
  "maxAttempts": 3
}
```

约束以 OpenAPI Schema 为准，主要规则包括：

- `reportName`：必填，最多 100 个字符
- `requestedBy`：必填，最多 100 个字符
- `records`：至少 1 条，最多 10,000 条
- `orderId`、`region`、`product`：非空并有长度限制
- `quantity`：大于等于 1
- `unitPrice`：大于 0
- `maxAttempts`：1 到 10，省略时后端使用 3

## 4. 幂等键约定

`POST /api/tasks` 必须发送 `Idempotency-Key`。

前端负责：

1. 用户开始一次新的逻辑提交时生成 UUID。
2. 提交按钮立即进入不可重复点击状态。
3. 同一次提交因超时或网络中断而重试时复用原 Key。
4. 用户修改业务数据并主动创建新任务时生成新 Key。
5. 不要在 HTTP 客户端的每次自动重试中生成新 Key。

后端响应语义：

- `202 Accepted`：创建了新任务，只代表已经受理，不代表报表已经生成。
- `200 OK` 且 `deduplicated=true`：该 Key 已存在，返回已有任务。

Redis 是加速层，数据库唯一约束是最终幂等保障。前端不能根据 Redis 状态推断结果。

## 5. 异步状态约定

状态分组：

- 处理中：`CREATED`、`QUEUED`、`RUNNING`、`RETRYING`、`COMPENSATING`
- 成功终态：`SUCCEEDED`
- 其他终态：`DEAD`、`CANCELLED`、`COMPENSATED`

前端收到 `202` 后应跳转任务详情并轮询 `GET /api/tasks/{taskId}`。建议前台页面每 2 到 3 秒轮询一次；页面不可见时降低频率或暂停；进入终态后停止。

只有 `SUCCEEDED` 可以下载结果。下载接口在任务未成功时返回 `409 REPORT_NOT_READY`。

取消仅允许状态机接受的状态：`CREATED`、`QUEUED`、`RETRYING`。`RUNNING` 和终态任务取消会返回 `409 INVALID_TASK_STATE`，因此前端按钮状态只是体验优化，仍须处理服务端冲突响应。

## 6. 错误处理约定

标准 JSON 错误结构：

```json
{
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "timestamp": "2026-09-26T10:00:00Z",
  "details": {
    "type": "must not be blank"
  }
}
```

前端判断逻辑应依赖稳定的 `code`，不要解析英文 `message`。`message` 用于兜底展示和排查，`details` 用于映射表单字段错误。

每个响应都会返回 `X-Trace-Id`。前端遇到无法解释的服务端错误时，应在错误提示或诊断信息中保留该值。

## 7. 本地开发与跨域策略

推荐本地拓扑：

```text
Browser -> Vite :5173 -> /api proxy -> Spring Boot :8080
```

前端业务代码始终请求相对地址 `/api`，不要写死 `http://localhost:8080`。Vite 开发服务器负责代理；正式环境由 Nginx 或同类网关代理 `/api`。这种同源策略优先于在后端开放宽泛 CORS。

建议环境变量：

- 本地 Mock：`VITE_API_MODE=mock`
- 本地联调：`VITE_API_MODE=real`，由 Vite 代理到 `localhost:8080`
- 集成环境：使用相对路径 `/api`
- 生产环境：使用相对路径 `/api`

## 8. 独立开发流程

1. 后端和前端共同评审需求与 API 变更。
2. 后端先更新 OpenAPI 契约和契约测试。
3. 前端更新契约快照并重新生成 TypeScript 类型。
4. 前端依据契约更新 Mock，在不依赖后端进程的情况下开发页面。
5. 后端实现通过后，双方在本地真实接口上联调。
6. 集成环境执行完整业务闭环：提交、轮询、成功、查看详情、下载 CSV。
7. 契约和实现同时通过评审后才能合并破坏性变更。

任何字段删除、重命名、类型改变、状态值删除、状态语义改变和 HTTP 状态码改变都属于破坏性变更。必须先通知前端并提供迁移窗口；新增可选字段通常属于兼容变更。

## 9. 当前业务边界

当前系统没有登录和用户资源隔离：

- `requestedBy` 是报表请求数据，不是经过认证的用户身份。
- `GET /api/tasks` 返回系统任务，不是严格意义上的“我的报表”。
- 已知 taskId 的调用者目前可能查询任务或下载结果。

因此第一版前端应使用“报表任务”或“报表列表”等文案。正式公网部署或实现“我的报表”之前，必须增加认证、按用户过滤和资源权限校验。

## 10. 前端第一阶段验收标准

前端仓库至少应做到：

- 可以使用 OpenAPI 快照生成类型。
- Mock 和真实 API 使用同一组 TypeScript 类型。
- 能创建合法 REPORT 任务并正确管理 `Idempotency-Key`。
- 能分页查询、查看详情和处理记录。
- 能正确轮询到终态且不会无限轮询。
- 只有成功任务可以下载 CSV。
- 能处理标准 `ApiError` 并保留 `X-Trace-Id`。
- 普通前端不调用内部重试、补偿或故障注入能力。
