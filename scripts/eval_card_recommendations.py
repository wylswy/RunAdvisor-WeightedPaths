#!/usr/bin/env python3
"""评估 Run Advisor 卡奖推荐的有效性（离线回放，基于 ~/RunAdvisorLogs）。

纯标准库实现。回答两个问题：
  1) 玩家遵循推荐时，实际表现是否更好（相关性，非因果，小样本警示）；
  2) 推荐机制本身是否按预期工作（评级分布 / 信任调整触发 / 使坏次数 / AI 兜底率）。

用法：
  python scripts/eval_card_recommendations.py [--log-dir <dir>] [--csv <out.csv>]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

GRADES = ["S", "A", "B", "C"]


def mean(xs: list[float]) -> float:
    return sum(xs) / len(xs) if xs else 0.0


def load_json(path: Path) -> dict | None:
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None


def reward_outcome(r: dict) -> dict:
    """把一次卡奖归类为 遵循/违背/无法评估，并提取质量信息。"""
    out = {
        "floor": r.get("floor", 0),
        "act": r.get("act", 0),
        "recommendedSkipAll": bool(r.get("recommendedSkipAll", False)),
        "playerSkipped": bool(r.get("playerSkipped", False)),
        "playerChosen": r.get("playerChosen", "") or "",
        "trustAdjusted": bool(r.get("trustAdjusted", False)),
        "trustFactor": float(r.get("trustFactor", 1.0) or 1.0),
        "mischief": bool(r.get("mischief", False)),
        "recommendedCardId": "",
        "recommendedGrade": "",
        "recommendedScore": 0.0,
        "follow": None,   # None=无法评估, True=遵循, False=违背
        "scoreGap": None, # 违背时：玩家所选分 - 推荐分（负=选得更差）
    }

    choices = r.get("choices") or []
    rec = next((c for c in choices if c.get("recommended")), None)
    if out["recommendedSkipAll"]:
        out["follow"] = out["playerSkipped"]
    elif rec:
        out["recommendedCardId"] = rec.get("cardId", "")
        out["recommendedGrade"] = rec.get("grade", "")
        out["recommendedScore"] = float(rec.get("finalScore", 0.0) or 0.0)
        if not out["playerSkipped"] and out["playerChosen"]:
            out["follow"] = out["playerChosen"] == out["recommendedCardId"]
            if not out["follow"]:
                chosen = next((c for c in choices if c.get("cardId") == out["playerChosen"]), None)
                chosen_score = float(chosen.get("finalScore", 0.0)) if chosen else 0.0
                out["scoreGap"] = chosen_score - out["recommendedScore"]
    return out


def analyze_card_rewards(runs: list[dict]) -> dict:
    total = 0
    with_rec = 0
    follows = 0
    defies = 0
    grade_count = {g: 0 for g in GRADES}
    trust_adjusted = 0
    mischief = 0
    gaps: list[float] = []
    run_follow_rates: list[tuple[float, dict]] = []  # (follow_rate, run)

    for run in runs:
        evals = [reward_outcome(r) for r in (run.get("cardRewards") or [])]
        decided = [e for e in evals if e["follow"] is not None]
        total += len(evals)
        with_rec += len(decided)
        follows += sum(1 for e in decided if e["follow"])
        defies += sum(1 for e in decided if not e["follow"])
        trust_adjusted += sum(1 for e in evals if e["trustAdjusted"])
        mischief += sum(1 for e in evals if e["mischief"])
        for e in evals:
            if e["recommendedGrade"] in grade_count:
                grade_count[e["recommendedGrade"]] += 1
            if e["scoreGap"] is not None:
                gaps.append(e["scoreGap"])
        if decided:
            run_follow_rates.append((sum(1 for e in decided if e["follow"]) / len(decided), run))

    return {
        "total": total,
        "with_rec": with_rec,
        "follows": follows,
        "defies": defies,
        "grade_count": grade_count,
        "trust_adjusted": trust_adjusted,
        "mischief": mischief,
        "gaps": gaps,
        "run_follow_rates": run_follow_rates,
    }


def analyze_agent_log(log_dir: Path) -> dict | None:
    path = log_dir / "agent_log.json"
    if not path.exists():
        return None
    decisions = []
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line:
            continue
        obj = load_json_path_line(line)
        if obj:
            decisions.append(obj)
    if not decisions:
        return None
    actions: dict[str, int] = {}
    for d in decisions:
        a = d.get("action", "?")
        actions[a] = actions.get(a, 0) + 1
    fallbacks = sum(1 for d in decisions if d.get("fellback"))
    latencies = [float(d.get("latencyMs", 0.0) or 0.0) for d in decisions]
    confs = [float(d.get("conf", 0.0) or 0.0) for d in decisions]
    return {
        "total": len(decisions),
        "fallbacks": fallbacks,
        "actions": actions,
        "avg_latency_ms": mean(latencies),
        "avg_conf": mean(confs),
    }


def load_json_path_line(line: str) -> dict | None:
    try:
        return json.loads(line)
    except json.JSONDecodeError:
        return None


def print_report(stats: dict, runs: list[dict], agent: dict | None) -> None:
    total = stats["total"]
    with_rec = stats["with_rec"]
    print("卡奖推荐评估（离线回放，仅相关性）")
    print("=" * 46)
    print(f"数据源: {len(runs)} 局 / {total} 次卡奖 / {with_rec} 次有推荐可评估")
    if with_rec:
        print(f"\n[推荐行为]")
        print(f"  遵循推荐: {stats['follows']} ({stats['follows'] / with_rec * 100:.1f}%)")
        print(f"  违背推荐: {stats['defies']} ({stats['defies'] / with_rec * 100:.1f}%)")
    g = stats["grade_count"]
    print(f"\n[推荐质量] 推荐卡评级分布: " + ", ".join(f"{k}={g[k]}" for k in GRADES))
    if stats["gaps"]:
        print(f"  违背时玩家选择 vs 推荐的分差: 平均 {mean(stats['gaps']):+.1f} 分"
              f"（负=玩家选得更差）")
    if total:
        print(f"\n[机制生效] 信任调整触发: {stats['trust_adjusted']} 次"
              f" ({stats['trust_adjusted'] / total * 100:.1f}%)；使坏: {stats['mischief']} 次")
    rates = stats["run_follow_rates"]
    if len(rates) >= 2:
        hi = [r for rate, r in rates if rate >= 0.5]
        lo = [r for rate, r in rates if rate < 0.5]
        if hi and lo:
            def win_rate(xs):
                return sum(1 for r in xs if r.get("victory")) / len(xs) * 100

            def avg_floor(xs):
                return mean([float(r.get("floorReached", 0) or 0) for r in xs])

            print(f"\n[对局关联]（小样本，非因果）")
            print(f"  遵循率≥50% 的局: n={len(hi)}, 胜率={win_rate(hi):.1f}%, 平均楼层={avg_floor(hi):.1f}")
            print(f"  遵循率<50% 的局: n={len(lo)}, 胜率={win_rate(lo):.1f}%, 平均楼层={avg_floor(lo):.1f}")
    if agent:
        print(f"\n[AI agent 可观测性] agent_log.json")
        print(f"  决策数: {agent['total']}，兜底率: {agent['fallbacks'] / agent['total'] * 100:.1f}%"
              f"，平均耗时: {agent['avg_latency_ms']:.0f}ms，平均置信度: {agent['avg_conf']:.2f}")
        print(f"  动作分布: " + ", ".join(f"{k}={v}" for k, v in sorted(agent["actions"].items())))
    print("\n注意：本报告是相关性统计，样本小且存在混杂因素（牌组/遗物/运气），"
          "不能证明因果；用于发现趋势和建立验证闭环。")


def to_csv_rows(runs: list[dict]) -> list[dict]:
    rows = []
    for run in runs:
        for r in run.get("cardRewards") or []:
            e = reward_outcome(r)
            rows.append({
                "seed": run.get("seed", ""),
                "victory": bool(run.get("victory", False)),
                "floorReached": run.get("floorReached", 0),
                "rewardFloor": e["floor"],
                "recommendedSkipAll": e["recommendedSkipAll"],
                "recommendedCardId": e["recommendedCardId"],
                "recommendedGrade": e["recommendedGrade"],
                "recommendedScore": e["recommendedScore"],
                "playerChosen": e["playerChosen"],
                "playerSkipped": e["playerSkipped"],
                "follow": "" if e["follow"] is None else ("yes" if e["follow"] else "no"),
                "scoreGap": "" if e["scoreGap"] is None else round(e["scoreGap"], 2),
                "trustAdjusted": e["trustAdjusted"],
                "mischief": e["mischief"],
            })
    return rows


def to_csv(rows: list[dict], path: Path) -> None:
    if not rows:
        print("csv: no rows")
        return
    cols = list(rows[0].keys())
    with open(path, "w", encoding="utf-8-sig", newline="") as f:
        f.write(",".join(cols) + "\n")
        for r in rows:
            f.write(",".join(str(r.get(c, "")) for c in cols) + "\n")
    print(f"csv written: {path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Evaluate card reward recommendations from run logs")
    default_dir = Path.home() / "RunAdvisorLogs"
    parser.add_argument("--log-dir", type=Path, default=default_dir)
    parser.add_argument("--csv", type=Path, default=None, help="Optional card-level CSV output path")
    args = parser.parse_args()

    if not args.log_dir.exists():
        print(f"log dir not found: {args.log_dir}")
        sys.exit(0)

    runs = [r for r in (load_json(p) for p in sorted(args.log_dir.glob("run_*.json"))) if r]
    if not runs:
        print(f"no runs in {args.log_dir}")
        sys.exit(0)

    stats = analyze_card_rewards(runs)
    agent = analyze_agent_log(args.log_dir)
    print_report(stats, runs, agent)

    if args.csv:
        to_csv(to_csv_rows(runs), args.csv)


if __name__ == "__main__":
    main()