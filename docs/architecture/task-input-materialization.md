# Task input materialization

实验数据不通过模型提示词或宿主机路径传递。它们属于租户任务的不可变对象，执行前由应用层 materialize 到沙箱工作区。

## 生命周期

```text
multipart upload
  -> ObjectStorage: tasks/<tenantHash>/<taskId>/inputs/<relativePath>
  -> ObjectIntegrity(TASK_INPUT)
  -> workspace/<workspaceId>/input
  -> preflight size + sha256 validation
  -> Docker /workspace/input (read-only)
```

对象路径、大小和 SHA-256 都由服务端计算或校验。相对路径禁止绝对路径、`.`、`..`、反斜杠和符号链接；materialize 使用临时文件加原子移动，并在每次重试前清理旧 input 目录。

## API

推荐对带首个数据文件的任务使用原子入口，确保 worker 只会看到已经冻结的输入集合：

```text
POST /api/v1/tasks/with-input
Content-Type: multipart/form-data
Fields: documentId, path, file
Header: Idempotency-Key
```

追加输入使用：

```text
POST /api/v1/tasks/{taskId}/inputs
Content-Type: multipart/form-data
Fields: path, file
```

任务执行前可通过 `GET /api/v1/tasks/{taskId}/inputs` 查看服务端记录。所有接口执行租户、权限和任务状态校验。

## 运行契约

`/workspace/input` 以只读方式挂载，生成代码只能把结果写入 `/workspace`（宿主机的 `output` 目录）。输入清单会生成稳定的 `taskInputDigest`，并写入最终报告的复现信息。上传失败或输入校验失败时，任务明确停止，不使用替代数据。
