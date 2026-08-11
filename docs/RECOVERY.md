# 换机恢复指南（恢复闭环 · 待演练）

> 目标：把「备份存在」变成「换机可用」。步骤在本机可预演，最终需在另一台机器完整走一遍（见 KNOWN-ISSUES K4）。

## 前提资产
- Mod 源码：`git clone https://github.com/wylswy/RunAdvisor-WeightedPaths`
- 知识库：`git clone` 私有 `wylswy/-sts1-knowledge-base`（或直接拷贝本机目录）
- 游戏本体 `desktop-1.0.jar`：版权原因**不进仓库**（`.gitignore` 显式排除），是恢复的唯一缺口

## 恢复步骤（新机器）
1. 安装 JDK 8 + Maven 3.6+；或在环境变量里设置 `JAVA_HOME` / `M2_HOME`（`run_mvn.bat` 已支持覆盖，不再依赖写死的本机路径）。
2. 获取游戏 jar（二选一）：
   - `scripts/setup-libs.ps1 -GameDir "<SlayTheSpire 安装目录>"`（或 `-GameJar <路径>` / `$env:STS_GAME_JAR`）
   - 或从 CI `game-test` 的 GitHub 私有 Release 附件下载 `desktop-1.0.jar` 放进 `lib/`
3. `run_mvn.bat test` 全量验证（应 247 测试全绿）。
4. 打包部署：`run_mvn.bat package` → `target/WeightedPaths.jar` → 复制进游戏 `mods/` 目录。

## 本机已知硬编码（换机需检查）
- `run_mvn.bat` 的 `JAVA_HOME` / `M2_HOME` 默认值（已支持环境变量覆盖；未设环境变量时回退到本机路径）。
- 知识库文档中的 `H:\MyKnowledgeBase\...`、`C:\STS-Modding\...`、`G:\sljt_101046\...`（KNOWN-ISSUES K5）。
- 自动化脚本（KB 自动同步任务等）为 Windows 计划任务，换机需重建。

## 演练验收标准
- [ ] 新机器 clone 后能 `run_mvn.bat test` 全绿
- [ ] 能产出可部署 jar 并进游戏加载
- [ ] 知识库索引可用（资料索引.md 链接可达）
- [ ] 本清单勾完后把 K4 改为「已闭环」