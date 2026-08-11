# 快速实测（30 分钟版）

> 发版前只验证最易崩/最关键的五条链路；完整版见 [PLAYTEST-CHECKLIST.md](PLAYTEST-CHECKLIST.md)。
> 实测前准备已完成：jar 已打包（`target/WeightedPaths.jar`，247 测试全绿）；`~/RunAdvisorLogs` 已清空为全新起点（旧日志已备份至 `RunAdvisorLogs.baseline-*`）。

## 准备（2 分钟）
1. 把 `target/WeightedPaths.jar` 拷进游戏 mods 目录（覆盖前先备份旧 jar）。
2. 可选：配置 DeepSeek key（配了测 B2，不配测 B1——建议两条各来一次）。
3. 游戏内只启用 BaseMod + 本 Mod。

## 30 分钟流程

| 时间 | 步骤 | 预期 | 记录 |
|------|------|------|------|
| 0–3min | A1 启动游戏进主菜单 → 开新局 | mod 装载无报错；卡开场白出现 | |
| 3–8min | B1/B2 第一次卡奖：无 key 一次 + 配 key 一次 | 推荐都显示（有 key 带理由 / 无 key 走兜底）；游戏不卡 | |
| 8–15min | C6 Act3 契约结算：打到第三幕，接受契约后打 boss（胜或负） | 结算发生：完成 +2 或违背 -2，不悬空 | |
| 15–20min | D1 SL 契约保留：任一幕接受契约后强杀 SL | 契约还在、好感/聊天恢复、卡调侃重开 | |
| 20–25min | G1 稳定性：连续 SL 3 次 + 快速进出卡奖 5 次 | 不闪退、不卡死 | |
| 25–30min | H1/H2 数据：退出游戏 → 检查 `~/RunAdvisorLogs` | 有 `run_state.json` / `agent_log.json`，JSON 可解析 | |

## 通过标准
- 全部通过 → 把 `~/RunAdvisorLogs` 发给 Codex 分析（eval + 节流数据 + 聊天探针）。
- 任何失败 → 记录现象 + 时间点，整个日志目录打包发我定位。

## 失败定位提示
- 闪退：看游戏 `sendToDevs/logs/`（或 Steam 日志）最新的 hs_err / 报错文件。
- 契约不结算：查 `run_state.json` 的 `pact` 字段是否随状态变化。
- 推荐不显示：查 `agent_log.json` 该条是否 `fellback=true`（无 key 属正常兜底）。