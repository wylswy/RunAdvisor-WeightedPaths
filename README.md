# Run Advisor（Weighted Paths 分支）

> 让杀戮尖塔里那张卡，成为一个**记得你、陪你聊天、会跟你较劲**的伙伴。

杀戮尖塔一代（STS1）Mod，核心是**有 AI 灵魂的陪伴**：卡不再是一件沉默的工具，而是会记住你、陪你说话、有脾气也懂得和好的「活物」。同时保留路线推荐、种子各幕预览、静默猎手 A20 卡奖评分等实用功能。基于 [sts-weighted-paths](https://github.com/derekjass/sts-weighted-paths)（MIT）扩展。

**当前版本：1.5.0** · AI 陪伴 + 路线推荐 + 卡奖评分

---

## ✦ AI 陪伴（主打）

这张卡是活的，它会：

| 它做什么 | 说明 |
|---------|------|
| **记得你（长期陪伴）** | 跨局档案 `player_relation.json`，新开一局也记得你。关系会慢慢升温：陌生 → 熟识 → 默契 → 好友。它**真记得**你上把打到第几层、是输是赢、抓了哪些牌、跟你聊过什么——开场就会提到这些真事 |
| **跟你聊天** | 地图界面按 `Tab` 呼出聊天框，随时跟它说话。它有性格、会主动开口，不用你一句句喂 |
| **有态度、会记仇** | 你违背它的推荐，它闹脾气、记你一笔；你道歉，它傲娇地原谅。好感度会真的升降，不是装样子 |
| **诚实，不装** | 记得的它说，**记不清的它老实说"记不清"**，绝不编造你们的过去——宁可承认健忘，也不骗你 |
| **AI 拍板推荐** | 出卡奖时，是这张有性格的卡决定推荐哪张，并给你一句性格化的理由 |

> 底层用运行时 LLM（DeepSeek）驱动语气与对话；没配 key 时自动回落本地规则/模板，功能依然完整，只是少了 AI 个性化。

---

## 实用功能

| 功能 | 说明 |
|------|------|
| 最优路线 | 地图上红色连线 + 下一步高亮，按三端口（伤害/格挡/运转）动态规划 |
| 各幕预览 | 右上角显示当前及后续幕的推荐路线节点统计 |
| 节点权重 | 每个地图节点旁显示路径价值数字（可关） |
| 权重菜单 | 左下角调整 M / ? / E / R / $ 权重 |
| 卡奖评分 | **静默猎手** 战斗后选牌：S/A/B/C 与「推荐」，按当前牌组 + 规划路线动态评估 |
| 决策日志 | 可选：每局写入 `~/RunAdvisorLogs/run_*.json` 供离线分析 |

---

## 安装

1. 将 `WeightedPaths.jar` 放入游戏的 `mods/` 目录。
2. 启动 ModTheSpire，勾选 **BaseMod** 与 **Run Advisor**。
3. 建议仅启用 BaseMod + 本 Mod 做兼容性测试。

依赖：ModTheSpire 3.22+、BaseMod 5.39+、杀戮尖塔 desktop-1.0.jar。

## 配置

ModTheSpire 主界面点击 **Run Advisor** 徽章（配置保存在 `WeightedPaths/config.properties`）：

- 显示地图最优路线（红线）
- 显示各幕路线预览（右上）
- 显示卡奖推荐（静默猎手）
- 显示节点权重数字 / 彩色背景
- 第三幕强制经过绿钥匙精英
- **写入决策日志（RunAdvisorLogs）** — 调参/测试时开启

## 决策日志

开启后每局生成 `run_<seed>_<id>.json`，含种子、楼层、胜负、每次进图节点权重、每次卡奖候选与四层分明细。分析脚本：

```powershell
pip install pandas
python scripts/analyze_runs.py --csv runs.csv
```

## 本地构建

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
$env:Path = "C:\STS-Modding\apache-maven-3.9.6\bin;$env:JAVA_HOME\bin;" + $env:Path
cd C:\STS-Modding\RunAdvisorMod\WeightedPaths
mvn package -DskipTests
Copy-Item -Force target\WeightedPaths.jar G:\sljt_101046\mods\WeightedPaths-dev.jar
```

产物：`target/WeightedPaths.jar`

## 已知限制

- 卡牌 baseScore 以攻略 + 手调为主，未接 SpireLogs 自动胜率
- A20 通关率仍在批量验证中
- 运行时 LLM 无 key 时回落本地规则/模板（功能完整，少了 AI 个性化）

## 致谢

- 原 Mod：[sts-weighted-paths](https://github.com/derekjass/sts-weighted-paths) by Derek Jass（MIT）
- ModTheSpire / BaseMod 社区

## 许可证

与原项目一致（MIT），见仓库 LICENSE。
