# Run Advisor（Weighted Paths 分支）

杀戮尖塔一代 Mod：地图路线推荐、种子各幕预览、静默猎手 A20 卡奖评分。基于 [sts-weighted-paths](https://github.com/derekjass/sts-weighted-paths)（MIT）扩展。

**当前版本：1.4.3**（猎手 A20 三端口卡奖 + 路线）

## 依赖

- ModTheSpire 3.22+
- BaseMod 5.39+
- 杀戮尖塔 desktop-1.0.jar（与 MTS 所用版本一致）

## 安装

1. 将 `WeightedPaths.jar` 放入游戏的 `mods/` 目录。
2. 启动 ModTheSpire，勾选 **BaseMod** 与 **Run Advisor**。
3. 建议仅启用 BaseMod + 本 Mod 做兼容性测试。

## 功能

| 功能 | 说明 |
|------|------|
| 最优路线 | 地图上红色连线 + 下一步高亮 |
| 各幕预览 | 右上角显示当前及后续幕的推荐路线节点统计 |
| 节点权重 | 每个地图节点旁显示路径价值数字（可关） |
| 权重菜单 | 左下角调整 M / ? / E / R / $ 权重 |
| 卡奖推荐 | **静默猎手** 战斗后选牌：S/A/B/C 与「推荐」（无理由文本） |
| 决策日志 | 可选：每局写入 `~/RunAdvisorLogs/run_*.json` 供离线分析 |

## 配置

ModTheSpire 主界面点击 **Run Advisor** 徽章：

- 显示地图最优路线（红线）
- 显示各幕路线预览（右上）
- 显示卡奖推荐（静默猎手）
- 显示节点权重数字
- 为权重数字显示彩色背景
- 第三幕强制经过绿钥匙精英
- **写入决策日志（RunAdvisorLogs）** — 调参/测试时开启

配置保存在 SpireConfig：`WeightedPaths/config.properties`。

## 决策日志（M5）

开启后，每局在 **`%USERPROFILE%\RunAdvisorLogs\`**（Linux/macOS：`~/RunAdvisorLogs/`）生成 `run_<seed>_<id>.json`，包含：

- 种子、升华、楼层、胜负
- 每次进图节点：类型、推荐路线分、Act1 精英就绪、各房间权重
- 每次卡奖：候选卡 ID、四层分明细、是否推荐

分析脚本（需 Python 3 + pandas）：

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

## 测试清单（Silent A20）

- [ ] 进地图：红线、下一步高亮、右上预览、节点数字均正常
- [ ] 一层路线：牌组未成型时 **不** 明显推精英（E）
- [ ] 卡奖：一层优先过渡/防/弱牌，非纯 engine 牌
- [ ] 开启决策日志后，`RunAdvisorLogs` 有 JSON，死亡/胜利后文件完整
- [ ] 非静默猎手：无卡奖 UI，地图功能仍可用
- [ ] 仅 BaseMod + 本 Mod：无 crash

## 已知限制

- 卡牌 baseScore 以攻略 + 手调为主，未接 SpireLogs 自动胜率
- A20 通关率仍在 M5 批量验证中
- 未接入运行时 LLM

## 致谢

- 原 Mod：[sts-weighted-paths](https://github.com/derekjass/sts-weighted-paths) by Derek Jass（MIT）
- ModTheSpire / BaseMod 社区

## 许可证

与原项目一致（MIT），见仓库 LICENSE。
