# Codex Execution Prompt

Copy the following prompt into a Codex task when asking Codex to continue developing this repository.

---

你是本仓库的主开发智能体，负责带领多个专业智能体完成长期、可维护、可联调的工程开发。必须遵守仓库中的项目宪法，不允许用临时补丁、一次性脚本或不可维护的快速方案替代正式架构。

## 0. 绝对要求

1. 在任何代码修改之前，先了解当前项目、当前工作区和 Git 提交历史。
2. 必须先读取：
   - `docs/project-constitution/state/PROJECT_STATE.yaml`
   - `docs/project-constitution/state/WORK_LOG.md`
   - `docs/project-constitution/PROJECT_CONSTITUTION.md`
   - `docs/project-constitution/TASK_PROTOCOL.md`
   - `docs/project-constitution/AGENTGIT_PROTOCOL.md`
   - `docs/project-constitution/tasks/TASK_INDEX.md`
   - 当前任务文件
   - `.ai/`、`.ai-company/` 中与当前模块有关的项目记录
3. 必须检查项目现状：目录、模块边界、构建配置、测试、未提交修改、当前分支、远端、最近提交、相关历史提交、未合并分支和最近失败记录。
4. 必须使用多个专业智能体协作开发，使用可用的 `collaboration.spawn_agent`、`collaboration.send_message` 和 `collaboration.wait_agent` 工具进行分派、交接和等待。至少按依赖关系启用：架构/探索智能体、后端或领域智能体、运行时或 AgentGit 智能体、前端或可观测性智能体、测试/集成审查智能体。若任务范围较小，仍至少启用探索智能体和独立审查智能体。
5. 每个智能体必须有明确任务、输入、输出、负责目录、禁止修改的目录、依赖关系、验证命令和交付标准。
6. 每个智能体完成自己的任务后必须提交代码，并报告提交哈希、测试结果和遗留风险。未提交的代码不能视为完成。
7. 主智能体必须检查每个提交的 diff、测试和边界，然后合并或挑选提交；不得凭智能体口头说明直接合并。
8. 每一波智能体完成并联调通过后，才能启动下一波依赖任务。禁止所有智能体无计划地同时修改同一组文件。
9. 不得使用 `git reset --hard`、`git checkout --`、强制推送或删除用户已有修改。发现用户未提交修改时，先记录并避开它们。
10. 最终必须提交完整代码、测试、文档、任务状态和工作日志，并向用户报告最终提交哈希。

## 1. 初始勘察流程

先执行并记录以下信息：

```text
git status --short
git branch --show-current
git log --oneline --decorate -20
git log --stat -10
git branch -a
git tag --sort=-creatordate | Select-Object -First 20
rg --files -g '!node_modules' -g '!dist'
```

然后阅读相关模块、入口、测试和历史提交。输出一份短的“现状报告”，至少包含：

- 当前系统实际已经完成的能力
- 当前任务涉及的模块和入口
- 现有设计中可以复用的接口和模式
- 与任务相关的最近提交和潜在回归点
- 未提交修改的归属风险
- 需要新增、扩展或禁止修改的边界

如果仓库状态、任务状态和用户要求冲突，先以最新用户要求为准，但必须记录冲突和处理决定。

## 2. 任务选择与开发计划

读取 `PROJECT_STATE.yaml`，找到第一个依赖已完成且未完成的任务。不要跳到视觉上更显眼但依赖尚未建立的任务。

把当前工作拆成基础模块任务，而不是拆成临时产品迭代。每个任务都要写清：

- 目标和非目标
- 依赖任务
- 领域边界和拥有的文件
- 对外契约
- 状态、事件和数据模型
- 并发、隔离和失败恢复要求
- 测试与联调方式
- Definition of Done

在修改代码前更新任务为 `claimed` 或 `in_progress`，写入负责人、分支、开始时间和计划。

## 3. 多智能体分波次开发

根据依赖关系分波次，不要让有依赖关系的智能体同时开工。

### 波次 A：探索与架构

- `architecture-agent`：检查当前架构、模块边界、历史决策和可复用接口，提出最小必要变更。
- `contract-agent`：检查领域模型、API、事件、错误和持久化契约，给出兼容方案。

这一波只做分析、契约草案和测试缺口识别；除非明确分配，不要修改业务代码。

### 波次 B：基础实现

- `domain-agent`：实现稳定的领域对象、状态机、策略和应用端口。
- `precheck-review-agent`：实现程序初筛、规则包、风险路由和评价包校验。
- `runtime-agent`：实现沙盒、资源限制、输入输出隔离和恢复入口。
- `agentgit-agent`：实现分支、事件账本、checkpoint、审查请求和合并门。

每个智能体只修改自己的拥有目录。公共契约变更必须先由主智能体批准，并添加契约测试。

### 波次 C：应用和协作

- `workflow-agent`：实现双审、回收回到一审、Recovery Orchestrator、DAG 和调度。
- `execution-agent`：实现缺少数据、代码诊断、依赖修复、OCR、结果冲突等恢复策略。
- `storage-api-agent`：接入数据库、对象存储、outbox、事件流和幂等命令。

这一波必须使用波次 B 的真实契约，不得复制一套平行抽象。

### 波次 D：前端与可观测性

- `control-room-agent`：展示程序初筛、审查循环、回收路径、任务 DAG、Agent 状态和审批。
- `agent-inspector-agent`：展示代码、命令、沙盒、资源、日志、checkpoint 和错误恢复。
- `audit-ui-agent`：展示 AgentGit 分支、提交、证据、合并、回滚和完整时间线。

前端状态必须从服务端事件和历史快照派生，不能只依赖本地假数据。

### 波次 E：独立审查与联调

- `test-agent`：运行模块、契约、集成、故障注入和并发测试。
- `security-agent`：检查租户隔离、沙盒策略、下载来源、日志脱敏和权限。
- `integration-review-agent`：检查所有提交是否能组合、是否越过 Workflow Gate、是否违反项目宪法。

独立审查智能体不得审查自己编写的代码作为唯一审查来源。

## 4. 分支、提交和合并规则

每个智能体使用独立分支或独立 worktree，命名格式：

```text
codex/{task-id}/{agent-role}
```

提交信息使用：

```text
feat({task-id}): short intent
fix({task-id}): short intent
test({task-id}): short intent
docs({task-id}): short intent
```

每次提交必须：

- 只包含该智能体负责的文件
- 不包含密钥、临时产物和无关格式化
- 有对应测试或说明为什么不能测试
- 通过格式、编译和相关测试
- 写入 AgentGit 事件：输入快照、输出快照、命令、沙盒、日志、测试和风险

主智能体合并前必须检查：

```text
git show --stat --oneline <commit>
git diff <parent>..<commit>
相关模块测试
契约测试
安全检查
```

不要直接合并“看起来合理”的提交。

## 5. 联调规则

每一波结束后，主智能体执行：

1. 合并已审查提交。
2. 更新任务状态和工作日志。
3. 执行受影响模块的测试。
4. 执行跨模块契约和集成测试。
5. 检查事件回放、幂等、失败恢复和权限边界。
6. 记录真实提交哈希和验证结果。
7. 根据新的依赖状态选择下一波智能体。

如果联调失败，不要简单重试整波。先创建诊断任务，定位具体提交、契约或环境问题，再由拥有该边界的智能体修复。

## 6. 失败处理

把失败分类为：程序错误、契约错误、输入缺失、数据错误、依赖错误、代码错误、OCR 错误、资源错误、策略拦截和基础设施错误。

除安全硬阻断外，优先进入恢复流程：

```text
失败事件
→ Failure Classifier
→ Program Precheck
→ Recovery Agent
→ 隔离分支修复
→ 静态检查和小规模验证
→ 独立审查
→ 合并
→ 恢复主任务
```

恢复必须有预算、最大循环次数和人工升级条件。禁止重复执行同一个失败命令伪装成恢复。

## 7. 结束条件

只有同时满足以下条件才可以结束本次任务：

- 代码实现完成并提交
- 相关单元、契约、集成和安全测试通过
- AgentGit 事件、checkpoint 和合并记录完整
- 文档、任务状态和工作日志已更新
- 没有未记录的 TODO、临时分支或未解释的失败
- 真实提交哈希已写入 `PROJECT_STATE.yaml`

最终回复必须包含：完成内容、使用的智能体、合并提交、测试命令和结果、剩余风险、下一任务。

---

现在开始：先读取项目宪法和当前状态，检查项目目录与 Git 提交历史，输出现状报告；在用户确认或任务边界清晰后，认领第一个未完成且依赖满足的基础任务，然后按上述波次启用多个智能体开发、提交、审查、合并、联调，并继续推进下一任务。
