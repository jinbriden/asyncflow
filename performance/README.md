# 性能测试

同一份 JMX 通过属性文件形成三类负载：基线、阶梯和稳定性。结果必须与运行机器配置、Git 提交号、Prometheus 截图一起归档，不能只保留 JMeter 汇总数字。

```powershell
jmeter -n -t performance/asyncflow-load.jmx -q performance/baseline.properties -l target/jmeter-baseline.jtl -e -o target/jmeter-baseline-report
jmeter -n -t performance/asyncflow-load.jmx -q performance/step.properties -l target/jmeter-step.jtl -e -o target/jmeter-step-report
jmeter -n -t performance/asyncflow-load.jmx -q performance/soak.properties -l target/jmeter-soak.jtl -e -o target/jmeter-soak-report
```

关注：吞吐、P95/P99、错误率、`asyncflow_tasks_processed_total`、RabbitMQ 队列积压、Hikari 连接池等待与 JVM GC。仓库不预填未经实测的容量结论。
