#!/usr/bin/env python3
"""Analyze Run Advisor decision logs in ~/RunAdvisorLogs/."""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

import pandas as pd


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
        "victory": bool(run.get("victory", False)),
        "floorReached": int(run.get("floorReached", 0) or 0),
        "endHp": int(run.get("endHp", 0) or 0),
        "actReached": int(run.get("actReached", 0) or 0),
        "act1EliteChosen": act1_elite_chosen(run),
        "cardRewardCount": len(run.get("cardRewards", [])),
        "nodeDecisionCount": len(run.get("nodeDecisions", [])),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Analyze RunAdvisorLogs run summaries")
    default_dir = Path.home() / "RunAdvisorLogs"
    parser.add_argument("--log-dir", type=Path, default=default_dir)
    parser.add_argument("--csv", type=Path, default=None, help="Optional CSV output path")
    args = parser.parse_args()

    if not args.log_dir.exists():
        print(f"log dir not found: {args.log_dir}")
        return

    runs = load_runs(args.log_dir)
    if not runs:
        print(f"no runs in {args.log_dir}")
        return

    df = pd.DataFrame([to_row(r) for r in runs])
    total = len(df)
    wins = int(df["victory"].sum())
    early_death = int((df["floorReached"] <= 30).sum())
    act1_elite_rate = float(df["act1EliteChosen"].mean()) if total else 0.0

    print(f"runs={total}")
    print(f"win%={wins / total * 100:.1f}")
    print(f"earlyDeath% (floor<=30)={early_death / total * 100:.1f}")
    print(f"act1EliteChosen%={act1_elite_rate * 100:.1f}")
    print(f"avgFloor={df['floorReached'].mean():.1f}")
    print(f"avgEndHp={df['endHp'].mean():.1f}")

    if args.csv:
        df.to_csv(args.csv, index=False, encoding="utf-8-sig")
        print(f"csv written: {args.csv}")


if __name__ == "__main__":
    main()
