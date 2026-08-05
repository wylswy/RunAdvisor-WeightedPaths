# 贡献与维护指南（CONTRIBUTING）

> 本工程的唯一维护规则权威。**改代码前先读本文件 + 相关规则。**
> 知识库维护规则见 `H:\MyKnowledgeBase\游戏\杀戮尖塔1\CONTRIBUTING.md`。

---

## 一、环境

| 项 | 路径 |
|----|------|
| 工程 | `C:\STS-Modding\RunAdvisorMod\WeightedPaths` |
| Maven | `C:\STS-Modding\apache-maven-3.9.6`（本机无全局 mvn，用 `run_mvn.bat`）|
| Java | JDK 1.8（`C:\Program Files\Eclipse Adoptium\jdk-8.0.492.9-hotspot`）|
| 游戏 jar | `G:\sljt_101046\desktop-1.0.jar` |
| 部署 | `G:\sljt_101046\mods\WeightedPaths-dev.jar` |

本机构建（maven 走 cmd 包装）：

```bat
run_mvn.bat test        # 跑测试
run_mvn.bat package -DskipTests   # 打包
```

## 二、开发流程

1. **改代码前先 Read**：知识库 `猎手/端口化.md` + `设计原则.md`（+ 涉及时 `路线.md`）。改评分/路线尤其必须。
2. 改代码 → `run_mvn.bat test` 验证（保持测试全绿）。
3. `run_mvn.bat package -DskipTests` → 复制 `target/WeightedPaths.jar` 到 `G:\sljt_101046\mods\WeightedPaths-dev.jar`。
4. 完全重启 ModTheSpire 实测。
5. **回写**：改分结论同步到知识库；更新 `docs/CHANGELOG.md`。

## 三、测试纪律

- 项目用 JUnit 4。新逻辑尽量写成**可注入 state 的纯逻辑**（便于测试）。
- **游戏类**（`AbstractRoom` 等）静态初始化依赖运行时，无法在纯测试实例化——测试只覆盖纯逻辑层，不要硬拗 mock。
- 跑测试：`run_mvn.bat test`（surefire 已把游戏 jar 加入 test classpath）。

## 四、补丁（MTS）安全

见 `.cursor/rules/mts-patch-safety.mdc`。核心禁忌：
- 不 patch `AbstractPlayer.upgradeCard` / `obtainCard`（不存在）
- 不 `@SpirePostfixPatch` 在 abstract 方法 `AbstractCard.upgrade()`
- 静态方法 postfix 不写 `__instance`；无参或只写原方法参数名

## 五、同步与提交

- 源码 → GitHub `wylswy/RunAdvisor-WeightedPaths`（main）。改完 commit + push。
- **不要 commit**：`target/`、`com/`、临时 scratch、密钥。
- 版本号 bump 时同步更新 `ModTheSpire.json` + `docs/CHANGELOG.md`。
- 知识库改完跑 `sync-md-to-txt.ps1` + 推私有备份。

## 六、完成后自查

- [ ] 测试全绿（`run_mvn.bat test`）
- [ ] 已打包并部署到 mods
- [ ] `docs/CHANGELOG.md` 已记录
- [ ] 改分/路线结论已回写知识库
- [ ] （如需）已 commit + push GitHub
