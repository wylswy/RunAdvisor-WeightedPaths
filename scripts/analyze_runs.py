#!/usr/bin/env python3
"""Analyze Run Advisor decision logs in ~/RunAdvisorLogs/.

纯标准库实现（无 pandas 依赖），输出对局统计摘要，验证 Mod 推荐是否有效。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path


def load_runs(log_dir: Path) -> list[dict]:
    runs = []
    for path in sorted(log_dir.glob("run_*.json")):
        try:
            text = path.read_text(encoding="utf-8")
            runs.append(json.loads(text))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"skip {path.name}: {exc}")
    return runs


def act1_elite_chosen(run: dict) -> int:
    for node in run.get("nodeDecisions", []):
        if node.get("act") == 1 and node.get("nodeType") == "E":
            return 1
    return 0


def to_row(run: dict) -> dict:
    return {
        "runId": run.get("runId", ""),
        "seed": run.get("seed", ""),
        "character": run.get("character", ""),
        "victory": bool(run.get("victory", False)),
        "floorReached": int(run.get("floorReached", 0) or 0),
        "endHp": int(run.get("endHp", 0) or 0),
        "actReached": int(run.get("actReached", 0) or 0),
        "act1EliteChosen": act1_elite_chosen(run),
        "cardRewardCount": len(run.get("cardRewards", [])),
        "nodeDecisionCount": len(run.get("nodeDecisions", [])),
    }


def mean(xs: list[float]) -> float:
    return sum(xs) / len(xs) if xs else 0.0


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
    parser = argparse.ArgumentParser(description="Analyze RunAdvisorLogs run summaries (stdlib)")
    default_dir = Path.home() / "RunAdvisorLogs"
    parser.add_argument("--log-dir", type=Path, default=default_dir)
    parser.add_argument("--csv", type=Path, default=None, help="Optional CSV output path")
    args = parser.parse_args()

    if not args.log_dir.exists():
        print(f"log dir not found: {args.log_dir}")
        sys.exit(0)

    runs = load_runs(args.log_dir)
    if not runs:
        print(f"no runs in {args.log_dir}")
        sys.exit(0)

    rows = [to_row(r) for r in runs]
    total = len(rows)
    wins = sum(1 for r in rows if r["victory"])
    early_death = sum(1 for r in rows if r["floorReached"] <= 30)
    act1_elite_rate = mean([r["act1EliteChosen"] for r in rows])
    avg_floor = mean([r["floorReached"] for r in rows])
    avg_end_hp = mean([r["endHp"] for r in rows])

    print(f"runs={total}")
    print(f"win%={wins / total * 100:.1f}")
    print(f"earlyDeath% (floor<=30)={early_death / total * 100:.1f}")
    print(f"act1EliteChosen%={act1_elite_rate * 100:.1f}")
    print(f"avgFloor={avg_floor:.1f}")
    print(f"avgEndHp={avg_end_hp:.1f}")

    if args.csv:
        to_csv(rows, args.csv)


if __name__ == "__main__":
    main()
