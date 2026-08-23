# 测试策略与证据口径

## 已实现分层

| 层级 | 实现位置 | 主要证据 |
| --- | --- | --- |
| 状态机/领域单测 | `src/test/.../domain` | 全部合法迁移、代表性非法迁移、重试边界、终态 |
| Worker 单测 | `TaskProcessorTest` | 正常、短暂失败、永久失败、重试耗尽、重复消息 |
| REST 接口测试 | `TaskApiIntegrationTest` | REST Assured 校验状态码、字段、异常码、事件轨迹 |
| 报表业务闭环 | `ReportBusinessFlowIntegrationTest` | 提交销售明细、区域汇总、结果查询、CSV下载、幂等和非法数据 |
| 测试框架组件 | `src/test/.../framework` | API Client 与业务测试数据工厂，隔离协议调用和场景数据 |
| 并发测试 | `ConcurrentIdempotencyIntegrationTest` | 100 个同 requestId 并发提交只形成一个 task/outbox |
| 基础设施可靠性 | `InfrastructureReliabilityTest` | Testcontainers 隔离 MySQL/Redis/RabbitMQ，Toxiproxy 断网，WireMock 超时 |
| 性能测试 | `performance/` | 基线、阶梯、稳定性三种 JMeter 模型 |

## 套件

```powershell
mvn test -Psmoke
mvn test -Pregression
mvn test -Preliability
mvn verify
```

`mvn verify` 生成：

- `target/surefire-reports`：JUnit 原始结果；
- `target/allure-results`：Allure 原始证据；
- `target/site/jacoco`：JaCoCo 覆盖报告。

Testcontainers 用例使用 `disabledWithoutDocker=true`。没有 Docker 时它们显示为 skipped，不能计入已执行的可靠性用例。CI 或安装 Docker 的机器会真正拉起隔离依赖并执行。

带 Docker 的一次完整 `mvn verify`（2026-08-17）通过 91 条测试，0 失败、0 错误、0 跳过，JaCoCo 行覆盖率 84.76%。当前数字以当次 Surefire / JaCoCo 报告为准。

## 可靠性场景映射

1. 100 并发同 requestId：`ConcurrentIdempotencyIntegrationTest`；
2. 重复 MQ 消息：`TaskProcessorTest#duplicateMessageAfterSuccessIsIdempotent`；
3. 下游两次超时、第三次恢复：`InfrastructureReliabilityTest#downstreamTimesOutTwiceThenThirdAttemptSucceeds`；
4. 持续失败进入死信：`persistentBusinessFailureEndsInDeadState`；
5. MQ 断连后 Outbox 恢复：`rabbitOutageLeavesOutboxPendingThenRecovers`；
6. Redis 断连降级：`redisOutageFallsBackToDatabaseAuthority`；
7. MySQL 网络故障与连接恢复：`mysqlNetworkCutIsVisibleAndConnectionRecovers`；
8. 超时 RUNNING 扫描：`StaleTaskScanner` 及其指标；
9. 并发取消/完成：由状态机 + `@Version` 乐观锁约束，尚未单独做成竞争测试；
10. 容量拐点：`performance/step.properties` 提供阶梯模型，结论需实测后记录。

## 报表业务场景

1. 三条跨区域销售订单生成两条区域汇总和一条总计，金额为 `1967.70`；
2. 任务成功后查询接口返回文件名、记录数、总销量、总金额、大小和 SHA-256；
3. 下载接口返回真实 CSV 内容，任务未完成时返回 `REPORT_NOT_READY`；
4. 相同幂等键重复提交只形成一个任务和一个 `report_result`；
5. 空订单列表、非法数量或价格在任务创建前返回 `INVALID_REPORT_PAYLOAD`。

## 失败重试原则

业务断言失败不自动重跑。只有容器启动、短暂网络中断等明确基础设施异常才允许在编排层重试，并必须保留第一次失败证据。异步等待使用 Awaitility 的条件轮询，不用固定 `Thread.sleep()` 猜测完成时间。
