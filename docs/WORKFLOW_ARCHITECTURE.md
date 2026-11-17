# AnyWorkflow 集成架构与维护指南

本文档说明 RikkaHub Antigravity 分支中 AnyWorkflow Remote 与文件转换/资料库模块的架构、接口分工、体积优化及自动化验证方式，便于后续同步官方 RikkaHub 更新与日常维护。

## 1. 上游同步与版本对齐

- 当前基线：官方 RikkaHub **3.3.0**（tag `3.3.0`，commit `eb33917e`，`versionCode = 197`）。
- 升级官方新版本时建议流程：
  1. `git fetch upstream --tags && git merge <tag>`
  2. 重点检查 `ai/.../google/InteractionsAPI.kt` 与 `RequestLog.kt` 中的 Antigravity 请求构造及脱敏日志补丁。
  3. 检查 `app/build.gradle.kts` 中 `buildTypes.debug` 的 CI R8 优化开关：
     - 当 `CI=true` 或传入 `-Prikka.minifyDebug` 时自动启用 `optimization { enable = true }`（R8 代码与资源压缩），将 `arm64-v8a` Debug APK 从 ~60 MB 压缩至 ~15 MB，与官方 Release 体积一致；本地日常调试默认关闭以保持增量编译速度。

## 2. 服务端架构与接口分工

AnyWorkflow 模块依赖两类服务端地址，**切勿混用**：

| 常量 | 默认地址 | 用途 | 鉴权方式 |
| :--- | :--- | :--- | :--- |
| `DEFAULT_POCKETBASE_URL` | `https://pb.any1.tech` | PocketBase 数据与认证：`/api/collections/aw_*`、`/api/anyworkflow/*` | `Authorization: <pbToken>` |
| `DEFAULT_REMOTE_API_URL` | `https://anyworkflow-remote.2212148739lbw.workers.dev` | Cloudflare Worker API：`/api/library/*`、`/api/file-conversion/*`、`/api/models/catalog/refresh` | `Authorization: Bearer <pbToken>` + `X-PocketBase-Url` |
| R2 公开 PDF CDN | `https://ocr-files.any1.tech/<uuid>.pdf` | 仅用于访问已上传的公开 PDF 原文件 | 能力链接（所有 `/api/*` 在该域名会返回 `404 FILE_NOT_FOUND`） |

### 会话登出规则 (`WorkflowRepository.executeJson`)
- **仅**当请求目标 `baseUrl == _session.value?.baseUrl` 且返回 HTTP `401` 时自动 `logout()`。
- 辅助 Worker 接口返回 `403` 或非会话域名的错误绝不清空 PocketBase 登录态，避免误退回连接页导致底部导航栏消失。

## 3. 客户端模块结构

### 数据层 (`app/src/main/java/me/rerere/rikkahub/data/workflow/`)
- `WorkflowRepository.kt`：PocketBase 会话管理、Run/Task/Event/Act/Message CRUD、`executeJson` 统一网络层。
- `WorkflowModels.kt` / `WorkflowDsl.kt`：DSL 解析与领域模型。
- `WorkflowLibraryRepository.kt`：收藏/模板（PocketBase）与持久资料库（Worker `/api/library/*`，含 PDF 分片上传与 Markdown 上传）。
- `WorkflowConverterRepository.kt`：PDF 转 Markdown 任务配置、创建、取消、重跑与产物下载。
- `WorkflowSettingsRepository.kt`：起始链接、已保存模型、运行默认配置。

### UI 层 (`app/src/main/java/me/rerere/rikkahub/ui/pages/workflow/`)
- `WorkflowPage.kt`：顶层 `Scaffold`、5 栏 `NavigationBar`（`工作流` / `重复` / `收藏` / `资料库` / `转换`）、`consumeWindowInsets(innerPadding)` 布局。
- 各 Tab 与 ViewModel 按功能拆分：`WorkflowRunsTab`、`WorkflowSchedulesTab`、`WorkflowLibraryTab`、`WorkflowFilesTab`、`WorkflowConverterTab`、`WorkflowSettingsDialog`。

## 4. 本地与 CI 视觉/接口自动化验证

### 本地验证脚本 (`tools/antigravity/verify-workflow-e2e.py`)

```bash
# 1. 仅验证 PocketBase 与 Worker 接口（含 Markdown 上传/读取/删除闭环）
WORKFLOW_TEST_IDENTITY="your@email" WORKFLOW_TEST_PASSWORD="your-password" \
  python3 tools/antigravity/verify-workflow-e2e.py --mode api --write-test

# 2. 连接本地模拟器或真机，安装 APK 并自动采集各页面截图、UI XML 与脱敏 logcat
python3 tools/antigravity/verify-workflow-e2e.py \
  --mode android \
  --apk app/build/outputs/apk/github/debug/app-github-x86_64-debug.apk \
  --out-dir artifacts/verify
```

### GitHub Actions 视觉验证工作流 (`tools/ci/workflows/visual-verify.yml`)
- 由于当前自动化凭据缺少 GitHub `workflow` OAuth scope，工作流模板保存在 `tools/ci/workflows/visual-verify.yml`。
- 在具备 `workflow` 权限的本地环境或 GitHub 网页端执行一次即可启用：
  ```bash
  cp tools/ci/workflows/visual-verify.yml .github/workflows/visual-verify.yml
  git add .github/workflows/visual-verify.yml && git commit -m "ci: enable workflow visual & API verification" && git push
  ```
- 启用后，建议在仓库 Settings → Secrets and variables → Actions 中配置 `WORKFLOW_TEST_IDENTITY` 与 `WORKFLOW_TEST_PASSWORD`。
