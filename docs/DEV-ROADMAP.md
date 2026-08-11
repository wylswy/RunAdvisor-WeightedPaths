# 开发路线图（父亲省察建议）

> ⚠️ 本文件是历史路线图：**开放问题清单已移交 [docs/KNOWN-ISSUES.md](KNOWN-ISSUES.md)（唯一真源）**；换机恢复见 [docs/RECOVERY.md](RECOVERY.md)。下方 bug 表为历史快照。

## 1. 安装 CLI

### Claude Code CLI（父亲说的「clade code cli」应指此项）

Windows PowerShell（需 Claude 订阅账号）：

```powershell
winget install Anthropic.ClaudeCode --accept-package-agreements --accept-source-agreements
# 或
irm https://claude.ai/install.ps1 | iex
```

验证：`claude --version`

### Hermes CLI（Nous Research 开源 Agent）

```powershell
iex (irm https://hermes-agent.nousresearch.com/install.ps1)
```

验证：`hermes --version` → 首次运行 `hermes setup`

---

## 2. 接入 DeepSeek API

推荐通过 **Hermes** 配置（Mod 本体仍用 Cursor + 规则，Hermes 可做离线批处理、日志分析）：

```powershell
hermes setup
# 或手动：
hermes config set OPENAI_API_KEY "你的DeepSeek密钥"
hermes config set OPENAI_BASE_URL "https://api.deepseek.com"
hermes config set model "deepseek-chat"
```

DeepSeek 控制台：https://platform.deepseek.com/api_keys

**注意**：密钥只放本机 `~/.hermes/` 或环境变量，**不要** commit 进 GitHub。

可选：用 Hermes 跑 `scripts/analyze_runs.py` 输出摘要、批量 seed 验证说明。

---

## 3. 知识库部署到 GitHub

本地路径：`H:\MyKnowledgeBase\游戏\杀戮尖塔1`

```powershell
cd "H:\MyKnowledgeBase\游戏\杀戮尖塔1"
git init
git add .
git commit -m "Initial commit: STS1 Silent A20 knowledge base"
git branch -M main
git remote add origin https://github.com/wylswy/-sts1-knowledge-base.git
git push -u origin main
```

> 注意：私有仓库名带前导连字符 `-sts1-knowledge-base`（GitHub 自动 301 可用）。

GitHub 上先建**空仓库**（不要勾选 README）。

部署后更新：

- `.cursor/rules/knowledge-base-first.mdc` 增加 GitHub 只读路径
- `docs/AI-KNOWLEDGE.md` 增加 clone 说明

改知识库后：

```powershell
git add . ; git commit -m "..." ; git push
```

> 注：2026-08-07 起**不再维护 .txt 副本**（已删 `sync-md-to-txt.ps1`），只维护 `.md` + `资料索引.md`；自动推送已停，改后手动 push。

---

## 4. 已知 Bug / 待修

| 优先级 | 项 | 状态 |
|--------|-----|------|
| P0 | 卡奖分数体感偏低（压分） | 1.4.5 已调 ×1.40 + 乘子下限，待你实测 |
| P1 | ~~知识库路径写死 `H:\`~~ | 已跟踪于 KNOWN-ISSUES K5；规则文件已支持双路径提示 |
| P1 | `PiercingWail` JSON key | 已与游戏 ID 一致，无需改 |
| P2 | SpireLogs / 胜率未接入 JSON | 里程碑 M5 |
| P2 | ~~卡奖 UI 无分数/理由~~ | 1.5.0 已上「性格化推荐理由」（AI 拍板）；无 AI/失败时回退 S/A/B/C |
| P3 | 私有知识库仓库名前导连字符 `-sts1-knowledge-base` | GitHub 自动 301 可用；可选改名去掉连字符（需网页操作，未做）|

---

## 仓库分工

| 仓库 | 内容 |
|------|------|
| `RunAdvisor-WeightedPaths` | Mod 源码（已有） |
| `sts1-knowledge-base`（建议名） | 攻略 / 端口化 / 设计原则 |
