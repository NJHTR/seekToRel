# Open Agent Desktop：2026 开源产品策略

日期：2026-08-07  
状态：产品提案，进入架构评审前的探索阶段

## 结论

不要把项目继续做成“上传 Word 的 AI 工具集合”。更有机会形成长期开源影响力的方向，是一个
**本地优先、可审计、可复现、可扩展的 Agent Desktop**：用户在 `Mac for Web` 中安装应用，
Agent 通过受控工具完成任务，所有关键步骤都能追踪、回放、评测和撤销。

`Nexus` 是第一个垂直应用（实验报告）；`Mac for Web` 是桌面壳；新的平台价值是开放的
Agent 应用协议、权限模型和证据闭环，而不是某一个模型或某一种文档格式。

## 调研信号

以下公开资料显示，2026 年的开源竞争重点正在从“聊天 UI”转向 Agent 的连接、安全和生产闭环：

1. [Model Context Protocol specification](https://modelcontextprotocol.io/specification/2025-06-18)
   将资源、提示、工具、进度、取消和用户同意纳入标准，并明确要求工具调用前的授权和隐私控制。
2. [OpenTelemetry Generative AI semantic conventions](https://github.com/open-telemetry/semantic-conventions-genai)
   正在把 Agent、LLM、工具调用和 MCP 事件纳入可互操作的观测语义。
3. [Future AGI](https://github.com/future-agi/future-agi) 的开源平台把模拟、评测、护栏、追踪和优化
   组成一个反馈回路，说明“Agent 的可证明质量”正在成为独立产品类别。
4. [GitHub build-2026 topic](https://github.com/topics/build-2026) 的项目和搜索结果集中出现
   MCP、Playwright、OpenTelemetry、A2A 与 Agent harness，说明生态层和运行时层比单一 Prompt
   更容易形成复用价值。

这些信号不是市场成功保证；它们支持一个可验证的产品假设：开源社区缺的不是又一个聊天窗口，
而是能在真实数据和真实工具上安全运行、复现失败并量化改进的 Agent 工作台。

## 旗舰产品：Open Agent Desktop（暂定名）

### 用户体验

```text
Mac for Web
├── Nexus                 实验报告与文档 Agent
├── Observatory           任务时间线、Trace、失败解释、重放
├── Evidence Lab          数据集、评测、模拟、回归和质量门禁
├── Tool/Connector Hub    MCP 工具、权限、审批、连接器健康状态
├── Local Vault           本地文件、密钥、PII 脱敏、数据流向和同意记录
├── Automation Center     工作流模板、定时任务、人工审批和回滚
├── Notes / Knowledge     知识库、引用、来源和变更历史
└── Developer Studio      沙箱代码、依赖、制品和可复现实验环境
```

### 不可替代的核心

- **证据闭环**：每个 Agent 任务产生事件、输入摘要、工具调用、输出制品、成本和质量评分。
- **可回放**：保留版本化 Prompt、模型、工具版本、容器 digest 和输入快照，支持脱敏重放。
- **可评测**：同一任务可在真实运行、离线数据集和故障模拟中比较成功率、引用正确率、成本和延迟。
- **安全默认**：MCP 工具按应用和租户授权；高风险工具先展示参数和数据流，再请求用户批准。
- **本地优先**：文件、凭据和敏感数据默认留在用户环境；云端 LLM 只收到明确授权的数据。
- **开放协议**：使用 MCP 连接工具，使用 OpenTelemetry 语义输出观测，使用标准 JSON/事件协议导入导出。

## 把现有代码变成平台资产

| 已有能力 | 产品化方向 |
| --- | --- |
| Nexus durable event history / SSE | Observatory 的权威事件账本和断线恢复 |
| Planner/Coder/Runtime/Reviewer | Evidence Lab 的可插拔 Agent harness |
| Docker sandbox / digest pinning | Developer Studio 的可复现实验运行时 |
| DOCX parser / Word export | Nexus 的第一个垂直工作流 |
| Prompt repository / memory snapshots | 版本化评测和回放输入 |
| Tenant / auth / permission boundary | App manifest 和工具授权基础 |
| Mac for Web shell | 应用市场、窗口隔离和统一权限体验 |

## 研发优先级

### Wave 1：把“能运行”变成“能证明”（当前最高优先级）

- 完成真实 LLM 的 DOCX 上传到导出验收。
- 将 Agent 事件升级为 OTel GenAI/MCP 对齐的 trace schema。
- 新增 Evidence Lab 的最小闭环：保存任务快照、重放一次、比较两次结果。
- 为工具增加用户可读的风险级别、参数预览和审批状态。

### Wave 2：开放桌面应用协议

- 定义 `app-manifest.json`：名称、版本、入口、权限、健康检查、数据范围和升级策略。
- 定义 App SDK：事件总线、窗口能力、认证会话、任务 API 和审计 API。
- 将 Nexus、Store、Reddit、Weather 迁移到 manifest 驱动，而不是写死在壳子里。

### Wave 3：隐私和连接器生态

- Local Vault：密钥不进入浏览器 localStorage；PII 脱敏和数据流向可视化。
- MCP Connector Hub：目录、签名、权限包、沙箱、速率限制和撤销。
- 支持本地模型和云端模型的可比较评测，不绑定单一供应商。

### Wave 4：社区可复用层

- 发布工作流模板、评测数据集格式、工具权限规范和事件 schema。
- 提供 CLI、Docker Compose 和最小 SDK，让外部开发者能写一个应用并在 Mac for Web 中运行。
- 建立安全响应、贡献者协议、兼容性矩阵和可复现实验报告。

## 当前不做

- 不复制用户脚本中的学校登录、验证码识别或查寝逻辑。
- 不把 QQ 邮箱授权码、学生账号、密码或任何第三方 Token 写入代码、配置样例或日志。
- 不先做“大而全”的浏览器 Agent；浏览器和搜索工具必须先通过 SSRF、权限、审计和沙箱门禁。
- 不把“模型回答得像人”作为主要指标；先以可复现、可解释、可回滚和安全为发布标准。

## 成功指标

- 新应用从 manifest 到在桌面启动：不修改核心壳代码即可完成。
- 一个任务可在刷新、重启和网络抖动后恢复，并保留完整事件顺序。
- 任意一次 Agent 运行可以在脱敏后重放；重放结果有可比较的评测报告。
- 高风险工具调用有明确授权记录，租户之间没有越权数据流。
- 贡献者可以只启动本地依赖，在 30 分钟内运行一个示例 Agent 和一组回归评测。

## 决策

建议把“实验报告自动化”保留为第一个可见产品，把平台主叙事升级为：

> **Open Agent Desktop：让 Agent 在真实世界里可观察、可评测、可复现、可安全扩展。**

该方向复用现有 ReportAI 投资，同时给 Mac for Web 一个能持续容纳新应用的产品主线。
