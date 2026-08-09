# 变更日志（Changelog）

> Run Advisor / WeightedPaths 工程的变更历史。基于 git 提交记录整理。
> 每次版本改动在此顶部追加一行：**日期 + 版本 + 改了什么 + 为什么**。

---

## 1.4.9（未发布 · 2026-08-09）

| 提交 | 变更 | 原因 |
|------|------|------|
| 4e44f28 | 聊天框 UI：ChatBoxUi 地图悬浮面板渲染对话 + 系统输入框(Gdx.getTextInput 支持中文) + ChatBoxPatch 挂载 DungeonMapScreen render/update；卡态度台词接入聊天框「主动开口」 | 让「卡」从单方面怼人升级为可对话的陪伴——8.11 交付核心「创造性 AI + 陪伴」 |
| dcdc37c | 聊天框核心 ChatBoxCore：对话管理 + 道歉识别触发好感度 + AI 多轮回复 + 聊天提示词构造(AiChat 接口, 6 用例) | 聊天灵魂核心，纯逻辑可测 |
| 4fde8ee | 卡的态度：acquireCard 直接记录实际抓卡(修「抓了却说没抓」) + 卡 ID 转中文名 + 台词生命周期清空(不残留) + AI 提示词强化杀戮尖塔世界观约束 + 黑名单过滤游戏外设定 + 好感度状态机 CardMoodEngine(记仇/闹脾气/道歉/原谅, 9 用例) | 修复实测问题；建立「卡会记仇会原谅」的情感循环 |
| 9037435 | 版本统一 1.4.7→1.4.9（pom/README/ModTheSpire.json/RunAdvisorLogger/WeightedPaths 五处）；CHANGELOG 补 1.4.9 条目 | 根治「版本纪律反复破功」；记录全部 08-09 改动 |
| 36edb48 | 补卡牌数据地基+路径格式化测试 11 用例（CardStatsLoader 验证 75 卡完整/requiresPort/关键锚点、RouteFormatUtil 符号格式化） | 锁住 75 卡数据地基防漏配/拼错；验证端口化关键卡配置 |
| b5b5acd | 补纯逻辑模型测试 ModelLogic 21 用例（CardStatEntry/PortProfile/DeckSnapshot/PathSymbolCounts） | 覆盖评分/路线核心数据结构方法，此前零覆盖 |
| 3341ba8 | 用户定稿调参：幽魂形态任何阶段（含一层）不再早期 ×0.52 砍分、改 blockPoints≥3 保底 ×1.22；一层房间权重改为火堆≈商店>小怪>事件>精英；silent_cards_a20.json 调参；VerificationCasesTest 断言同步 | 用户 08-09 定稿：幽魂不应被杂技压过；一层按风险/收益重排权重 |
| fae5190 | 决策日志链路：analyze_runs.py 去 pandas（纯标准库+CSV 导出）、RunAdvisorLogger flush 改覆盖模式防 JSON 拼接、Config 默认开启决策日志 | 消除 pandas 依赖；修复日志 JSON 拼接导致 Extra data 解析失败；默认记录对局供分析 |
| 36edb48 | 补卡牌数据地基+路径格式化测试 11 用例（CardStatsLoader 验证 75 卡完整/requiresPort/关键锚点、RouteFormatUtil 符号格式化） | 锁住 75 卡数据地基防漏配/拼错；验证端口化关键卡配置 |
| 04595db | .gitignore 完善：忽略 `__pycache__`、研究草稿临时数据、DeepSeek 实验脚本 | 工程整洁，避免散落文件污染仓库 |

> 全量测试 **29 → 80**（+51 用例），覆盖率约翻倍；工作区彻底干净。

---

## 1.4.8（未发布 · Claude 修复批 2026-08-07）

| 提交 | 变更 | 原因 |
|------|------|------|
| — | **B1 评分饱和修复**：`SCORE_CALIBRATION` 1.40→0.90；最弱端口乘子 1.35/1.60→1.20/1.35（ENGINE 1.35）；方向强化 1.15→1.10；组合乘子上限 1.35 | 实测 ~2/3 卡被评 S、1/3 顶格 100；移植校准网格验证选项 E 使分布健康（S 10-25/75，顶格 0-7，均衡卡组 skip 恢复 33） |
| — | **R1 死亡建模**：`RouteSimState.dead` 标记 + 模拟血量 ≤0 判死；`PathValuation` 死路返回 -1e6 | 原模拟血量下限 1，无法建模死亡，可能推荐必死路线 |
| — | **S3 未来幕基线**：`RouteSimState.forFutureAct` + `GlobalRunPlanner` 未来幕用满血/按幕估算金币的基线状态 | 原未来幕路线用当前幕状态估值，Act2/3 预览分数随 Act1 血量/金币抖动 |
| — | **P1 刷新节流**：`RunStateRefreshPatch`/`GoldChangedPatches` 战斗内（`!isScreenUp`）不刷 + 500ms 节流 | 原每次掉血/金币触发全量重估（含种子 oracle 重算三幕），一局 348 次 |
| — | U1 权重菜单上下界 [0.1,10]；L1 日志 modVersion 1.3.2→1.4.7；D1 README 1.4.5→1.4.7；R6 删商店后 `hasMaw=false` | 小修 |

---

## 1.4.7（2026-08-07）

| 提交 | 变更 | 原因 |
|------|------|------|
| 167ceaa | 方向识别缺陷修复：DirectionProfile 排除 block（主攻只算 attack/dot/draw）+ DeckAnalyzer 改"一张卡只算一个主方向" | 修复审查#6：block 主线时方向强化死分支、多标签卡重复计数污染画像 |
| e962924 | 完全移除 Sentry 遥测（代码/依赖/配置） | 隐私外泄：DSN 是上游作者 o514923，seed/地图/Mod列表 实时发给第三方 |
| 344adf7 | 组件一致性：DirectionProfile 方向识别 + FourLayerScorer 强化主线(×1.15, BLOCK硬底线) | 遵从白夕"组件一致性"选牌思路 |
| — | 版本统一 1.4.6→1.4.7；mts_version 3.22→3.26（对齐构建依赖） | 审查#3/#9：核心改动应升版本、版本号多处脱节 |

## 1.4.6（2026-08-05）

| 提交 | 变更 | 原因 |
|------|------|------|
| b2b7f9f | 新增低血保命硬规则 + 涅奥祝福白嫖精英加成；引入 JUnit 测试基础设施（浅解耦，路径可注入 state） | 解决「血量低仍推荐打精英、跟路线容易死」；测试支撑自动化验证 |
| 0f589e6 | 一层精英风险折扣（就绪时 0.95→0.75）；版本升至 1.4.6，ModTheSpire 描述与启动日志加中文更新提示 | 猎手一层战力弱，按收益/风险对比避精英；让玩家知晓本次更新 |

## 1.4.5（2026-08-05）

| 提交 | 变更 | 原因 |
|------|------|------|
| 548771d | 卡牌分数解压：×1.40 校准、乘子下限、软化过严惩罚 | 修复「卡奖分数体感偏低（压分）」，见 DEV-ROADMAP P0 |

## 1.4.4（2026-08-05）

| 提交 | 变更 | 原因 |
|------|------|------|
| f226f2d | 回退分数压缩：恢复等级线、软化苛刻乘子 | 压缩导致分级失真 |

## 1.4.3（2026-08-05）

| 提交 | 变更 | 原因 |
|------|------|------|
| e91573c | Initial commit：WeightedPaths 1.4.3 | 项目起点 |
| d3dea60 | 新增 Cursor 规则：更新后同步 GitHub | 建立「改完即同步」纪律 |

---

## 约定

- 版本号与 `src/main/resources/ModTheSpire.json` 的 `version` 一致。
- 部署产物 `target/WeightedPaths.jar` → `G:\sljt_101046\mods\WeightedPaths-dev.jar`。
- 涉及评分/卡牌结论的改动，须同步更新知识库 `H:\MyKnowledgeBase\游戏\杀戮尖塔1`。
