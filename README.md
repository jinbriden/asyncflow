# AsyncFlow

异步销售报表生成服务，并带一套可重复执行的质量保障。

用户提交销售订单明细后，系统异步按区域聚合、生成可下载的 CSV，并提供任务查询。链路覆盖任务落库、Transactional Outbox、RabbitMQ 消费、幂等、重试、死信和短暂故障恢复。

- Java 17 / Spring Boot 3
- MySQL、Redis、RabbitMQ
- JUnit 5、REST Assured、Testcontainers、WireMock、Toxiproxy
- Docker Compose、GitHub Actions、JaCoCo 覆盖率门禁

设计说明见 [架构与一致性](docs/architecture.md)，测试范围见 [测试策略](docs/test-strategy.md)。

## 业务怎么走

```text
POST /api/tasks  →  任务 + 事件 + Outbox 同一事务写入
                 →  Outbox 投递到 RabbitMQ（至少一次）
                 →  Worker 按区域汇总并写出 CSV
                 →  GET /api/tasks/{id}/result 下载报表
```

同一 `Idempotency-Key` 重复提交不会再生成一份报表。Redis 只做短时映射；数据库 `idempotency_key` 唯一约束才是最终权威。消息语义是至少一次投递，不宣称 exactly-once。

## 启动

需要 Docker，建议至少 6 GB 可用内存。

```powershell
docker compose up -d --build
docker compose ps
```

| 服务 | 地址 | 本地凭据 |
| --- | --- | --- |
| API 健康检查 | http://localhost:8080/actuator/health | 无 |
| RabbitMQ 管理台 | http://localhost:15672 | `asyncflow` / `asyncflow` |
| Prometheus | http://localhost:9090 | 无 |
| Grafana | http://localhost:3000 | `admin` / `asyncflow` |

这些凭据只用于本机 Compose。不要把默认 token 暴露到公网。

```powershell
docker compose down          # 保留 MySQL、RabbitMQ 和报表数据卷
docker compose down -v       # 同时删除本项目本地数据
```

从未挂载 `rabbitmq-data` 的旧 Compose 配置升级时，新增命名卷不会自动迁移旧容器可写层里的 Broker 数据。升级前应暂停新任务并排空队列；如需保留积压消息，应在保持 RabbitMQ 节点名和 Erlang Cookie 一致的前提下，把旧 `/var/lib/rabbitmq` 迁移到命名卷后再重建容器。

## 调用示例

提交报表任务：

```powershell
$headers = @{ 'Idempotency-Key' = 'demo-001'; 'X-Trace-Id' = 'trace-demo-001' }
$body = @{
  type='REPORT'
  payload=@{
    reportName='regional-sales'
    requestedBy='qa@example.com'
    records=@(
      @{ orderId='SO-1001'; region='East'; product='Keyboard'; quantity=2; unitPrice=199.50 },
      @{ orderId='SO-1002'; region='West'; product='Mouse'; quantity=3; unitPrice=89.90 },
      @{ orderId='SO-1003'; region='East'; product='Monitor'; quantity=1; unitPrice=1299.00 }
    )
  }
  maxAttempts=3
} | ConvertTo-Json -Depth 8
$task = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/tasks' -Headers $headers -ContentType 'application/json' -Body $body
```

查询状态、事件并下载 CSV：

```powershell
Invoke-RestMethod "http://localhost:8080/api/tasks/$($task.taskId)"
Invoke-RestMethod "http://localhost:8080/api/tasks/$($task.taskId)/events"
Invoke-WebRequest "http://localhost:8080/api/tasks/$($task.taskId)/result" -OutFile regional-sales.csv
```

模拟前两次失败、第三次成功：

```powershell
$headers = @{ 'Idempotency-Key' = 'retry-demo-001' }
$body = @{
  type='REPORT'; maxAttempts=3; simulateFailures=2
  payload=@{
    reportName='retry-sales'; requestedBy='qa@example.com'
    records=@(@{ orderId='SO-R1'; region='East'; product='Keyboard'; quantity=1; unitPrice=199.50 })
  }
} | ConvertTo-Json -Depth 8
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/tasks' -Headers $headers -ContentType 'application/json' -Body $body
```

端到端冒烟：

```powershell
./scripts/smoke.ps1
```

## 测试

```powershell
mvn verify
./scripts/verify.ps1 -Suite smoke
./scripts/verify.ps1 -Suite regression
./scripts/verify.ps1 -Suite reliability
```

`mvn verify` 会跑单元测试、API 测试、业务闭环、并发幂等，以及（Docker 可用时）异步装配冒烟和 Testcontainers 可靠性用例，并生成：

- `target/surefire-reports`：逐类结果和失败栈
- `target/allure-results`：Allure 原始证据（`allure serve target/allure-results`）
- `target/site/jacoco/index.html`：JaCoCo 报告

Docker 不可用时，`AsyncPipelineIntegrationTest`、`DatabaseIdempotencyConflictIntegrationTest`、`RedisIdempotencyStoreIntegrationTest` 和 `InfrastructureReliabilityTest` 会显示 skipped，不能算作已执行的装配冒烟或可靠性用例。CI 要求总体行覆盖率不低于 70%；新增业务代码若无对应测试会让构建失败。

一次带 Docker 的完整运行结果（2026-09-15）：109 条通过，0 失败 / 0 错误 / 0 跳过；JaCoCo 行覆盖率 92.02%、分支覆盖率 71.53%。当前数字以当次 `mvn verify` 为准。

| 层级 | 主要位置 |
| --- | --- |
| 状态机与领域 | `TaskStateMachineTest`、`TaskRecordTest` |
| Worker | `TaskProcessorTest` |
| REST API | `TaskApiIntegrationTest`（REST Assured） |
| 报表业务闭环 | `ReportBusinessFlowIntegrationTest` |
| 异步装配冒烟 | `AsyncPipelineIntegrationTest`（Outbox → RabbitMQ → Listener） |
| 超时扫描 | `StaleTaskScannerTest` |
| 并发幂等 | `ConcurrentIdempotencyIntegrationTest`（100 个相同 Key，1 条任务 / 1 条 Outbox） |
| 数据库唯一键竞态 | `DatabaseIdempotencyConflictIntegrationTest`（真实 MySQL，连续 3 次竞争） |
| 幂等失败补偿 | `TaskSubmissionServiceFailureTest`、`TaskSubmissionRollbackIntegrationTest`、`RedisIdempotencyStoreTest`、`RedisIdempotencyStoreIntegrationTest`、`InMemoryIdempotencyStoreTest` |
| 基础设施 | `InfrastructureReliabilityTest`（Testcontainers + Toxiproxy + WireMock） |

可靠性用例覆盖 Redis 断连后的单请求数据库降级、两个请求越过预占后的真实 MySQL 唯一键兜底、Outbox 仓储故障时的数据库回滚与幂等释放、Redis Lua 原子比较删除、RabbitMQ 断连后的 Outbox 补偿投递、下游连续超时后的退避重试，以及持续失败进入 DEAD。协调层并发用例跑在测试 profile 上；数据库唯一键竞态用例改写数据源为临时 MySQL，并使用可控屏障确保两个请求都尝试插入。

## 接口

| 方法与路径 | 说明 | 约束 |
| --- | --- | --- |
| `POST /api/tasks` | 创建任务 | 必须带 `Idempotency-Key`；新建 202，去重 200 |
| `GET /api/tasks/{id}` | 查询任务 | 返回状态、重试次数、失败原因 |
| `GET /api/tasks/{id}/result` | 下载报表 | 仅成功任务可下载 |
| `POST /api/tasks/{id}/cancel` | 取消任务 | 终态取消返回 409 |
| `GET /api/tasks/{id}/events` | 查询事件轨迹 | 追加写入，按时间排序 |
| `POST /internal/tasks/{id}/retry` | 死信重放 | 需要 `X-Internal-Token` |
| `POST /api/tasks/{id}/compensate` | 补偿 | 仅 `DEAD` 任务 |
| `GET /actuator/prometheus` | 指标 | 提交、去重、处理、重试、死信、扫描恢复 |

## 目录

```text
src/main/java/.../api        REST 接口、校验、Trace ID
src/main/java/.../domain     状态机、任务、事件、Outbox
src/main/java/.../messaging  Outbox 发布、RabbitMQ 消费
src/main/java/.../service    执行、重试、补偿、超时扫描
src/main/java/.../report     销售校验、区域汇总、CSV 与结果查询
src/test/java                单元、API、业务闭环、并发、可靠性测试
performance/                 JMeter 基线 / 阶梯 / 稳定性模型
ops/                         Prometheus 与 Grafana
scripts/                     冒烟与套件脚本
docs/                        架构、测试策略、缺陷复盘
.github/workflows/           CI：mvn verify、覆盖率门禁、失败证据归档
```

API 与 Worker 目前打包在同一个 Spring Boot 进程里，方便本机演示；包边界已经按接口、领域、消息、报表和测试拆开。

## 缺陷复盘

- [AF-001：请求校验依赖导致干净构建失败](docs/defects/AF-001-validation-dependency.md)
- [AF-002：并发幂等预占与数据库提交之间的竞态窗口](docs/defects/AF-002-idempotency-race-window.md)

`performance/` 只提供可执行负载模型，不预填吞吐或百分位数字。结论需要在固定机器、固定提交和固定负载下实测后另行记录。
