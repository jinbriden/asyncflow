# 测试策略与证据口径

## 已实现分层

| 层级 | 实现位置 | 主要证据 |
| --- | --- | --- |
| 状态机/领域单测 | `src/test/.../domain` | 全部合法迁移、代表性非法迁移、重试边界、终态 |
| Worker 单测 | `TaskProcessorTest` | 正常、短暂失败、永久失败、重试耗尽、重复消息 |
| REST 接口测试 | `TaskApiIntegrationTest` | REST Assured 校验状态码、字段、异常码、事件轨迹 |
| 报表业务闭环 | `ReportBusinessFlowIntegrationTest` | 提交销售明细、区域汇总、结果查询、CSV下载、幂等和非法数据 |
| 异步装配冒烟 | `AsyncPipelineIntegrationTest` | HTTP 提交后走 Outbox Scheduler、真实 RabbitMQ 和 Listener，等待 SUCCEEDED 并可下载 CSV |
| 超时扫描 | `StaleTaskScannerTest` | 过期 RUNNING 任务重新入队、重试耗尽进入死信、空列表无副作用 |
| 测试框架组件 | `src/test/.../framework` | API Client 与业务测试数据工厂，隔离协议调用和场景数据 |
| 并发幂等 | `ConcurrentIdempotencyIntegrationTest` | 100 个同 requestId 并发提交在协调层只形成一个 task/outbox |
| 数据库唯一键竞态 | `DatabaseIdempotencyConflictIntegrationTest` | 真实 MySQL 中两个请求同时越过预占，连续 3 次验证唯一键赢家恢复、202/200 响应和单条 task/event/outbox |
| 幂等失败补偿 | `TaskSubmissionServiceFailureTest`、`TaskSubmissionRollbackIntegrationTest`、`RedisIdempotencyStoreTest`、`RedisIdempotencyStoreIntegrationTest`、`InMemoryIdempotencyStoreTest` | 唯一键冲突恢复、Outbox 故障事务回滚、数据库失败释放、Lua 原子比较删除、不匹配保护、真实 Redis 行为和异常降级 |
| 基础设施可靠性 | `InfrastructureReliabilityTest` | Testcontainers 隔离 MySQL/Redis/RabbitMQ，覆盖网络中断、Broker 重启持久化与下游超时 |
| 性能测试 | `performance/` | 基线、阶梯、稳定性三种 JMeter 模型 |

## 套件

```powershell
mvn test -Psmoke
mvn test -Passembly-smoke
mvn test -Pregression
mvn test -Preliability
mvn verify
```

`mvn verify` 生成：

- `target/surefire-reports`：JUnit 原始结果；
- `target/allure-results`：Allure 原始证据；
- `target/site/jacoco`：JaCoCo 覆盖报告。

Testcontainers 用例使用 `disabledWithoutDocker=true`。没有 Docker 时它们显示为 skipped，不能计入已执行的可靠性用例。CI 或安装 Docker 的机器会真正拉起隔离依赖并执行。

带 Docker 的最新完整 `mvn verify`（2026-09-15）通过 109 条测试，0 失败、0 错误、0 跳过，JaCoCo 行覆盖率 92.02%、分支覆盖率 71.53%。当前数字以当次 Surefire / JaCoCo 报告为准。

冒烟分两层：

- 快速冒烟（`mvn test -Psmoke`，`test` profile / H2）：任务受理和报表业务处理，Listener 与定时器关闭，Worker 由测试代码直接调用，不启动容器。
- 异步装配冒烟（`mvn test -Passembly-smoke`，`AsyncPipelineIntegrationTest`）：开启 Outbox Scheduler 和 RabbitMQ Listener，用 Testcontainers 跑真实 MySQL/Redis/RabbitMQ。没有 Docker 时该用例 skipped，不能算已执行的装配冒烟；CI 会额外检查该类及其他容器可靠性类的 skipped 数必须为 0。

## 可靠性场景映射

1. 100 并发同 requestId 的协调层去重：`ConcurrentIdempotencyIntegrationTest`；
2. 两个请求同时越过预占后的真实 MySQL 唯一键冲突：`DatabaseIdempotencyConflictIntegrationTest` 连续执行 3 次，断言一个 202、一个 200、同一任务 ID，且 task/event/outbox 各一条；
3. 数据库失败后的幂等补偿：`TaskSubmissionServiceFailureTest` 验证释放、原异常传播和恢复查询失败不覆盖原异常；`TaskSubmissionRollbackIntegrationTest` 验证 Outbox 仓储故障时任务与事件回滚且预占释放；`RedisIdempotencyStoreTest` 验证 Lua 原子比较删除命令，`RedisIdempotencyStoreIntegrationTest` 在真实 Redis 验证不匹配保护与匹配释放；
4. 重复 MQ 消息：`TaskProcessorTest#duplicateMessageAfterSuccessIsIdempotent`；
5. 下游两次超时、第三次恢复：`InfrastructureReliabilityTest#downstreamTimesOutTwiceThenThirdAttemptSucceeds`；
6. 持续失败进入死信：`persistentBusinessFailureEndsInDeadState`；
7. MQ 断连后 Outbox 恢复：`rabbitOutageLeavesOutboxPendingThenRecovers`；
8. 持久化消息在 Broker 重启后仍可消费：`persistentTaskMessageSurvivesBrokerRestart`；
9. Redis 断连降级：`redisOutageFallsBackToDatabaseAuthority`；
10. MySQL 网络故障与连接恢复：`mysqlNetworkCutIsVisibleAndConnectionRecovers`；
11. 超时 RUNNING 扫描：`StaleTaskScannerTest` 覆盖可重试重新入队、耗尽进入死信和空扫描；
12. 并发取消/完成：由状态机 + `@Version` 乐观锁约束，尚未单独做成竞争测试；
13. 容量拐点：`performance/step.properties` 提供阶梯模型，结论需实测后记录。

## 报表业务场景

1. 三条跨区域销售订单生成两条区域汇总和一条总计，金额为 `1967.70`；
2. 任务成功后查询接口返回文件名、记录数、总销量、总金额、大小和 SHA-256；
3. 下载接口返回真实 CSV 内容，任务未完成时返回 `REPORT_NOT_READY`；
4. 相同幂等键重复提交只形成一个任务和一个 `report_result`；
5. 空订单列表、非法数量或价格在任务创建前返回 `INVALID_REPORT_PAYLOAD`。

## 失败重试原则

业务断言失败不自动重跑。只有容器启动、短暂网络中断等明确基础设施异常才允许在编排层重试，并必须保留第一次失败证据。异步等待使用 Awaitility 的条件轮询，不用固定 `Thread.sleep()` 猜测完成时间。
