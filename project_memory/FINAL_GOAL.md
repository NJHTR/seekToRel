# seekToRel 最终产品目标

## 一句话目标

把 `seekToRel` 交付为一个本地优先、可开源、可审计、可恢复的 **Open Agent Desktop**：用户从 `Mac for Web` 桌面打开 `Nexus Report Forge`，上传真实实验报告 DOCX，系统在受控的 Agent 工作流中完成文档解析、实验规划、代码生成、依赖安装、沙箱执行、证据收集、质量审查和 DOCX 导出；整个过程即使浏览器关闭、SSE 断线或后端重启，也能从持久化检查点继续，并且绝不伪造实验结果或绕过安全边界。

## 用户最终拿到的产品

用户最终拿到的不是一次模型对话，而是一组可下载、可追溯的交付物：

1. 经过 Reviewer 放行的最终 DOCX。
2. 执行时间线、阶段状态、失败原因和重试记录。
3. 生成代码、依赖锁定清单、镜像 digest、输入文件 hash、随机种子和运行配置。
4. 真实产生的 stdout/stderr、日志、图表、图片、模型检查点和其他产物。
5. 可复现 manifest：其他开发者可以据此在本地重新运行，或明确知道缺少什么输入。

## 端到端闭环

```text
Mac for Web
  -> Nexus
  -> 上传 DOCX
  -> 安全解析 canonical document
  -> Planner 生成结构化计划
  -> Coder 生成受限项目代码
  -> 确定性依赖识别与签名 Catalog Resolver
  -> Preflight 检查输入、依赖、hash 和资源额度
  -> Installer 在临时隔离环境安装精确依赖
  -> Runtime 在非 root、无网络、受限资源的 Docker 沙箱执行
  -> 收集代码、日志、图表、图片和运行指标
  -> Cleanup 删除临时容器、卷、依赖和文件
  -> Reviewer 检查完整性、真实性和证据链
  -> Exporter 生成最终 DOCX
```

## 必须满足的产品性质

### 1. 可靠性

- 任务是持久化后台任务，不依赖浏览器连接保持。
- 浏览器刷新、SSE 断开、IDEA 重启或后端重启后，任务可从最后一个持久化 checkpoint 恢复。
- 每个阶段都有稳定状态、错误码、traceId、provider requestId（若有）和可解释的恢复建议。
- 长时间 LLM 请求使用 streaming、心跳、idle timeout、total timeout、取消、有限重试和低推理回退；不能靠一次无限等待的 HTTP 请求。

### 2. 安全性

- LLM 只能理解文档、制定计划和生成代码，不能决定镜像、版本、hash、网络、权限或宿主机路径。
- 镜像必须 digest 锁定；依赖必须来自签名 Catalog，并以 hash 锁定安装。
- Runtime 默认非 root、无额外 capability、`no-new-privileges`、只读 rootfs、`network none`，并具备 CPU、内存、PID、磁盘和输出限制。
- 租户、任务、文件和产物严格隔离；凭据不得进入提示词、日志或沙箱环境。
- 任务结束、失败、取消、超时和崩溃恢复后都必须清理临时资源。

### 3. 真实性与可复现性

- 没有真实数据集、输入文件或必要资源时，任务必须以稳定错误码失败（例如 `PYTHON_INPUT_MISSING`），不得复制模板图片、生成假曲线或声称实验完成。
- 报告中的每个结论都必须能关联到真实输入、代码、日志、图表或运行产物。
- 交付物记录 prompt 版本、模型、随机种子、工具版本、镜像 digest、依赖版本与 hash、输入 hash、命令和产物索引。
- 相同环境、输入和配置至少连续三次能够完成同一状态闭环；非确定性必须明确标注。

### 4. 可扩展性

- `Mac for Web` 是统一桌面壳，不把业务逻辑硬编码进桌面；应用通过 manifest、权限、窗口能力和事件协议接入。
- Nexus 是首个垂直应用；Observatory、Evidence Lab、Notes、Developer Studio 以及后续 Reddit、E-commerce 等应用可独立开发、测试和集成。
- 后端以领域端口隔离 LLM、Docker、对象存储、PostgreSQL、Redis 和邮件等适配器，便于本地运行和替换基础设施。
- 开源仓库必须提供脱敏配置、`.env.example`、一键启动、迁移、最小 Catalog、测试、故障排查和安全模型文档，不包含真实密钥和个人账号。

## 最终验收标准（Definition of Done）

只有全部满足以下条件，才宣布“最终目标完成”：

1. 新用户能够注册/登录，在 Mac for Web 中打开 Nexus 并上传目标 DOCX。
2. Planner、Coder、Catalog Resolver、Preflight、Installer、Runtime、Cleanup、Reviewer、Exporter 全部真实执行，并在 UI 显示持久化事件。
3. 有真实数据集时，系统完成沙箱执行、产物收集、审查和最终 DOCX 下载；最终 DOCX 可打开且章节、图表和引用一致。
4. 缺少数据集、依赖、镜像或资源时，系统安全失败并给出明确修复建议，绝不伪造成功。
5. 浏览器关闭、SSE 断线、刷新和后端重启后，任务状态、事件历史和产物索引仍正确，且不会重复执行已完成阶段。
6. 失败、取消、超时、重试和崩溃恢复均完成临时资源清理；无孤儿容器、卷或依赖目录。
7. 自动化测试覆盖领域逻辑、权限隔离、依赖闭包、沙箱约束、LLM 超时/流式响应、恢复、导出和安全回归；端到端验收有可归档证据。
8. 文档能让外部开发者在 30 分钟内启动本地依赖、后端、前端和示例任务，并能理解哪些能力仍需要真实数据或外部服务。

## 明确不纳入“完成”的内容

- 只返回一段代码、只显示 100% 进度或只生成一个模板 DOCX。
- 用 `image1.png` 或模型文本冒充训练数据、实验曲线、模型结果或审查通过。
- 让 LLM 直接执行宿主机命令、安装任意包、访问任意网络或选择未批准的镜像。
- 用 SSE、浏览器内存、Redis 短期状态或 IDEA 进程内变量代替持久化事实源。
- 为了首个版本牺牲安全边界、租户隔离、可恢复性或证据链。

## 发布顺序

1. **R1 Nexus 闭环**：目标 DOCX 从上传到最终导出，完成持久化任务、Catalog、Preflight、Installer、Runtime、Cleanup、Reviewer 和证据链。
2. **R2 Mac for Web 平台化**：manifest、App SDK、窗口/权限/事件协议，Nexus 与其他应用解耦接入。
3. **R3 Evidence Lab**：任务快照、脱敏重放、基准数据集、失败注入、结果对比和评测报告。
4. **R4 开源生态**：CLI、Docker Compose、SDK、应用市场、MCP/Connector Hub 和第三方应用安全审核。

## 最终判定

`seekToRel` 的最终成功，不是“模型回答得出来”，而是：一个真实 DOCX 在可控、可恢复、可审计、可复现的执行链中得到真实证据支持的最终报告；同时，外部开发者可以在不接触任何私密凭据的前提下运行、测试和扩展这套系统。
