# AF-001：请求校验注解导致干净构建失败

- 发现阶段：首次执行 `mvn -DskipTests compile`
- 现象：`jakarta.validation.constraints` 包不存在，所有 DTO 校验注解无法编译。
- 根因：Spring Boot 3 的 Web Starter 不隐式提供 Bean Validation，实现中使用了 `@NotBlank/@Min/@Max` 却未声明 `spring-boot-starter-validation`。
- 修复：在 `pom.xml` 显式加入 Validation Starter，重新编译通过。
- 回归：REST Assured 覆盖空任务类型、零重试次数、过大故障次数和缺少幂等头。
- 价值：暴露了“IDE 可提示但干净 Maven 环境缺依赖”的工程问题，CI 必须从空缓存构建。
