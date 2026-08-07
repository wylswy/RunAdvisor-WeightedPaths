# AI 开发知识库（Run Advisor / WeightedPaths）

供 Cursor Agent 与人类开发者查阅。规则摘要见 `.cursor/rules/`；本文档为工程内精简版。

**用户主知识库（更长资料、攻略摘录）**：

`H:\MyKnowledgeBase\游戏\杀戮尖塔1`

对话中可用 `@H:\MyKnowledgeBase\游戏\杀戮尖塔1\README.md` 引用。

**GitHub 只读备份**：`https://github.com/wylswy/-sts1-knowledge-base`（私有，换机 `git clone` 获取；本机改库后推送）。

**用户主知识库（底层逻辑）**：`H:\MyKnowledgeBase\游戏\杀戮尖塔1\猎手\端口化.md`

---

## 1. 架构一览

```
WeightedPaths.java          Mod 入口、refreshPathValues()
├── paths/                  地图路线
│   ├── MapPath, OracleMapPath
│   ├── SilentRouteValuation    A20 猎手路线权重
│   ├── RouteSimState           沿路径模拟 HP/牌组
│   └── PathValuation
├── card/                   卡奖评分
│   ├── CardScorer → FourLayerScorer
│   ├── DeckAnalyzer → DeckSnapshot, PortProfile
│   ├── GlobalRunPlan           整局路线上下文
│   └── RelicAnalyzer
├── seed/                   种子解码与各幕预览
└── patches/                MTS 注入（UI、刷新、配置）
```

**数据流（选牌）**：战斗奖励 → `CardScorer.score(card, masterDeck, plan)` → 读 JSON baseScore → 四层修正 → `CardGrade` → UI 显示。

---

## 2. 猎手评分：端口化（无流派）

### 三端口

| 端口 | 标签 | 职责 |
|------|------|------|
| DAMAGE | attack, aoe, dot | 输出（dot = 持续伤，不单独成「毒包」） |
| BLOCK | block | 格挡 |
| ENGINE | draw, energy, discard, engine, retain | 运转 |

辅助：`transition` / `future` / `terminal` / `weak` / `scaling` / `pollution`

### 动态规则

- `requiresPort` + `requiresMinPoints`：前置端口不足 → 动态降分（约 ×0.78 下限，见 `FourLayerScorer`）
- 补最弱端口（layer2）；路线/血线（layer1）；洁牌（layer4）
- **禁止** poison/shiv/discard 流派计数与 combo 倍增

---

## 3. JSON Schema（silent_cards_a20.json）

```json
"Card Name": {
  "baseScore": 0-100,
  "tags": ["attack", "block", "dot", "transition", ...],
  "requiresPort": "DAMAGE",
  "requiresMinPoints": 2
}
```

---

## 4. FourLayerScorer 要点

| 层 | 职责 |
|----|------|
| 1 生存 | HP、下精英、缺 AOE、一层过渡、战未来路线门控 |
| 2 端口 | 短板 × 阶段；休息少+精英多 → 格挡加成 |
| 3 端口协同 | requiresPort 不足惩罚；scaling 叠加；终端/战未来；重复杂技 |
| 4 污染 | 高费废牌、牌组≥18、打击多、removalUrgency |

`GlobalRunPlan`：`nextRoomIsElite()`, `eliteWithin(n)`, `roomsUntilElite`, `phase` (EARLY/DEVELOPMENT/LATE)。

---

## 5. 路线模拟（RouteSimState）

沿最优路径模拟：HP 变化、商店删 strike、休息升级、**三端口**就绪状态。  
`SilentRouteValuation` 用 `RouteSimState` + 遗物标志调整 M/?/E/R/$ 权重。

刷新触发：`RunStateRefreshPatch`（HP、主牌组增减、升级 Action）。

---

## 6. 补丁备忘（已踩坑）

详见 `.cursor/rules/mts-patch-safety.mdc`。

- `RunStateRefreshPatch`：升级监听在 `UpgradeSpecificCardAction` / `UpgradeRandomCardAction`，不在 `AbstractCard.upgrade()`

---

## 7. 构建与测试

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot"
mvn package -DskipTests
Copy-Item -Force target\WeightedPaths.jar G:\sljt_101046\mods\WeightedPaths-dev.jar
```

**Silent A20  smoke test**

1. 进地图：红线、预览、节点权重
2. 战斗后选牌：有等级/推荐，无理由文本
3. 篝火升级 / 拿牌 / 受伤后：路线权重应刷新
4. 非猎手：无卡奖 UI

---

## 8. 扩展知识库时

1. 新结论 → 先写进 `1_背景与玩法/静默猎手/端口化.md`，再改 JSON/Java
2. 新设计原则 → 更新 `.cursor/rules/silent-card-scoring.mdc`
3. 新补丁模式 → 更新 `mts-patch-safety.mdc`
4. 在 JSON `meta.ascensionNote` 可简短注明校准来源

**未接入**：Heart Rate / SpireLogs 自动胜率（站点不稳定）；运行时 LLM。
