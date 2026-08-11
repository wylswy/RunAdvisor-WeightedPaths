# 变更日志（Changelog）

> Run Advisor / WeightedPaths 工程的变更历史。基于 git 提交记录整理。
> 每次版本改动在此顶部追加一行：**日期 + 版本 + 改了什么 + 为什么**。

---

## 1.5.0（未发布 · 2026-08-10）8.11 交付版

| 提交 | 变更 | 原因 |
|------|------|------|
| （本次收尾 · 2026-08-11） | 收尾接线闭环：修 3 处残留（PactManager 重复声明/游离行语法错误、CardRewardRenderPatch 缺 AiExecutor import、RunPersistenceTest 旧签名）；契约状态跨 SL 持久化（PactEngine/PactManager 导出-恢复 + run_state.json 落 pact 字段 + 新局重置/SL 恢复）；RunLifecyclePatch 胜负结算契约；AiExecutor 共享单线程 daemon 池替换 3 处 `new Thread`；agent_log.json 超 5MB 轮转保留一份历史；ModTheSpire.json 版本改由 pom 过滤注入（单一事实源）；CI-SETUP 文档测试数不写死；补 13 个测试（导出/恢复往返、非法数据降级、否定式拒绝、旧文件兼容） | 8.11 最终交付前收尾：编译/测试全绿，契约与 agent 功能完整；246 测试全过 |
| e4a7f1e | 契约/赌约接线完成：PactManager 接线层（提案消息/聊天接受拒绝/好感度应用/奖励发放+保守建议标志），挂 4 个游戏钩子——地图打开幕切换结算+新幕提案、进精英房结算 REACH_ELITE、抓攻击牌违背检测、聊天框接受/拒绝关键词；CardMoodEngine 新增 adjustFavor（夹紧 -10..10）；奖励真实生效：REVEAL_NEXT_ELITE 报精英距离+前方路线、CONSERVATIVE_ADVICE 提高卡奖跳过阈值 5 分 | 契约从纯引擎接入真实游戏流程；233 测试全过 |
| ebd6165 | 机制层第二块：契约/赌约引擎 PactEngine——状态机 OFFERED→ACCEPTED→COMPLETED/VIOLATED；两类条件（不抓攻击牌/50% 血到精英）与两类奖励（揭精英/保守建议）；事件驱动（抓牌/到精英/幕结束），完成 +2 好感/违背 -2，奖励只发一次；纯逻辑确定性，与 CardMoodEngine 联动由调用方执行 | 卡提出有后果的新决策（原版不存在的选择空间）；219 测试全过 |
| 70ba414 | CI 配置辅助：工作流支持两种取游戏 jar 方式（GitHub 私有 Release 附件 GH_ASSETS_TOKEN+ASSETS_REPO / 任意私有直链 STS_GAME_JAR_URL），未配置跳过测试不红；新增 docs/CI-SETUP.md 图文配置指南，README 挂入口 | 让 CI 在私有游戏 jar 前提下可复现 |
| 70f658d | agent 工具循环（ReAct 式）：AgentCore 升级为真 agent——AI 可先调只读查询工具（EVALUATE_CARD/QUERY_DECK/QUERY_ROUTE）查证再给最终动作，最多 3 轮有界循环；工具执行器由 patches 层注入真实引擎；无 key/AI 失败/输出无效/超轮数/执行器异常→规则兜底；agent_log.json 新增 tools 字段 | 推荐先查证再决策，闭环更可靠；204 测试全过 |
| 193d2fd | 数据闭环第一块：离线回放评估脚本 eval_card_recommendations.py——统计遵循/违背推荐的行为差异、评级分布、违背分差、信任调整/使坏触发率 + agent_log.json 可观测性；兼容旧日志缺字段；支持 --csv 导出 | 用真实对局数据验证推荐有效性 |
| 47a3fd4 | 工程收口：ModTheSpire/BaseMod jar 进仓（lib/），游戏本体 jar 由 scripts/setup-libs.ps1 引导获取；pom 依赖改项目内路径；.github/workflows/ci.yml 跑 mvn test（游戏 jar 经 secret 提供，未配置跳过）；.gitattributes 统一行尾；LICENSE 补 MIT；README 补依赖准备步骤 | 依赖可复现 + CI + LICENSE；196 测试全过 |
| 93c9e3e | 机制层第一块：信任调整策略 RecommendationPolicy——关系真正改变推荐结果（纯函数确定性可回放）：favor≤-6→0.55/≤-2→0.80/友好→1.0；接入卡奖渲染链路（先调整后记录，日志/违背检测/显示三者一致）；run_*.json 新增 trustAdjusted/trustFactor；修推荐记录与显示脱节 bug | 让关系影响推荐而非只影响台词；196 测试全过 |
| d2a6f69 | agent 闭环 WIP 收口落库：AgentCore 最小 agent 闭环（感知→结构化 JSON 工具协议→参数校验→规则兜底→agent_log.json 全落盘）+ AgentBridge 桥接 UI + 卡奖链路接入 AgentCore.run；线程安全：ThreadDispatcher 抽象 + Gdx.app::postRunnable 回投主线程 + volatile；DeepSeekClient 结构化失败分级 + log4j 告警 | 8.11 核心「AI 拍板推荐」闭环落库；185 测试全过 |
| 304b4f7 | README 重写：AI 陪伴提为主打 | 让交付文档先讲「卡会记得你」 |
| 19e67a6 | 长期陪伴跨局记忆（温暖向·真记得不装）：player_relation.json 跨局档案 + 关系阶段（陌生→熟识→默契→好友）+ 卡开场按阶段/真记得上把（楼层/胜负/抓牌中文名/聊过的话）+ 聊天提示词注入记忆 + 诚实护栏（记不清就说绝不编造）；收编 Claude 聊天探针 | 跨局真记忆，陪伴感落地；164 测试全过 |
| （debug 批 · 2026-08-11） | ①GlobalRunPlanner 修状态共享：每条路线用 `state.copy()` 估值（此前共用实例，后路线继承前路线血量/死亡，各幕预览推荐失真）；②GlobalRunPlan 修前方路线恒空：MapPath 不含当前节点，改为直接全量收集（此前首房后精英压力/剩余火堆/AOE 判断全失效，一层精英误吃"无火堆"×0.30 惩罚）；③推荐状态机与决策日志开关解耦（关日志后态度/记仇/违背检测仍工作）+ 卡奖去重缓存新局重置；④使坏"没上当"好感不动（明着坏下不中计是正常反应，不再自愈也不死循环，解锁记仇靠道歉）；⑤聊天探针计数新局清零（per-run 口径）；⑥日志会话按 seed+timestamp 识别（同 seed 弃局重开不再串数据）；⑦卡奖日志评级用显示口径（B 档提升对齐） | 修 1.5.0 交付后 debug 审查发现的逻辑/集成问题；166 测试全过 |
| （本次提交） | 版本统一 1.4.9→1.5.0（pom/README/ModTheSpire.json/RunAdvisorLogger/WeightedPaths 五处）；AI 推荐完善（记仇理由缩短防压边、记仇只在无高分卡时触发防坑玩家、使坏标签色）；README/描述更新为「AI 拍板推荐+卡会陪伴」 | 8.11 交付定稿版本；修使坏坑高分卡风险 |
| 6c67f86 | 记仇使坏改「明着坏」护栏 + README 修正过时描述 | 使坏理由明说「在逗你」不装，保住推荐可信度 |
| 8075770 | 聊天框翻页（超一屏分页浏览） | 聊天记录变长后可回看历史 |
| 7fef063 | SL 检测+记忆落盘：run_state.json 持久化聊天/好感度，读档恢复 vs 新局清空 | 存档重进保持卡的记忆 |
| 617a5f8 | 记仇使坏反馈闭环：上当→得意+好感回升，没上当→嘴硬+好感再降 | 使坏形成完整反馈 |
| db241f8 | 记仇使坏：好感度记仇时 AI 故意推候选最差卡逗玩家 + 调皮理由 | 卡会跟你较劲（陪伴感） |
| b64a36c | AI 拍板推荐：有性格的卡决定推荐哪张+性格化理由+规则兜底；温柔陪伴版聊天框收尾 | 8.11 核心「创造性AI+陪伴」 |

---

## 1.4.9（未发布 · 2026-08-09）

| 提交 | 变更 | 原因 |
|------|------|------|
| 8075770 | 聊天框翻页：消息超一屏(MAX_LINES=8)时显示「上一页/下一页」按钮 + 当前消息范围条，点击翻页(每次 8 条)，新消息自动回到最新页 | 聊天记录变长后无翻看历史入口，早的对话看不到 |
| 7fef063 | SL 检测 + 记忆落盘：RunPersistence 持久化 run 指纹(seed+seedSourceTimestamp)+聊天记录+好感度到 `~/RunAdvisorLogs/run_state.json`；receiveStartGame 用指纹区分「SL 读档」(恢复对话+好感度+卡调侃你重开) vs 「新局」(清空)；ChatBoxCore onChange 回调落盘 + restoreMessages；CardMoodEngine restoreFavor | SL 是同一局重进(指纹不变)，新局是新指纹——用指纹区分，让卡记得你偷偷重开 |
| 617a5f8 | 记仇使坏反馈闭环：上当→得意台词+好感回升，没上当→嘴硬台词+好感再降(专属台词池)，onClose 区分使坏场景走 evaluateMischiefResult；修复「没有上当」vs「没上当」断言 bug | 让「骗」成完整情感循环：骗成功得意，骗失败嘴硬 |
| db241f8 | 记仇使坏：好感度记仇(RESENTFUL)时 AI 故意推候选最差卡逗玩家(看你还信不信)+调皮理由+4 用例 | 卡记仇时会故意推坏卡试探你的信任——「有灵魂」的关键 |
| b64a36c | AI 拍板推荐：让有性格的卡决定推荐哪张 + 性格化理由 + 规则兜底(AI 失败永不卡)+温柔陪伴版聊天框收尾(Tab 呼出/卡奖界面挂载/新局清空/台词温柔化) | 8.11 交付核心：推荐带性格(AI 拍板)，规则算分器保底可靠 |
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
