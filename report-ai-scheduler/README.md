# report-ai-scheduler

持久化友好的任务调度端口，不绑定数据库或消息中间件。

## 设计约束

- `SchedulerTaskStore` 是唯一持久化边界；生产实现必须用事务和条件更新完成领取、租约续期、成功/失败转换。
- `BoundedTaskScheduler` 使用 `ThreadPoolExecutor + ArrayBlockingQueue + AbortPolicy`，线程数和队列长度均有上限，禁止无界队列。
- 任务提交先写入 Store，再尝试投递有限的 worker signal；进程重启后由外部 poller 调用 `recoverExpiredLeases` 和 `dispatchAvailable` 恢复。
- 幂等键至少按 `tenant_id + task_type + idempotency_key` 建立唯一约束，并校验请求指纹；冲突不得静默覆盖。
- 租约过期可重新领取，因此处理器必须具备幂等性；长任务通过 `TaskExecutionContext.renewLease()` 协作续租。
- 超时采用协作式 deadline，真正的 Docker/进程级硬超时由 runtime 模块负责。
- `ProgressEvent` 为 WebSocket/SSE 适配层提供稳定事件模型；事件 Sink 不承担无限缓存，持久化/广播由上层实现。

## 根项目集成

在根 `pom.xml` 的 `<modules>` 中加入：

```xml
<module>report-ai-scheduler</module>
```

调度模块直接依赖 `report-ai-common`，不依赖 Spring、数据库驱动或其他业务模块。
