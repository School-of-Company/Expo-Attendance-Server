#!/usr/bin/env python3
"""JMeter 결과(JTL, CSV)를 라벨별 건수·오류율·처리량·지연 분위수로 요약한다. 표준 라이브러리만 쓴다."""
import csv
import sys
from collections import defaultdict


def percentile(sorted_values, p):
    if not sorted_values:
        return 0
    index = min(len(sorted_values) - 1, int(round(p / 100 * (len(sorted_values) - 1))))
    return sorted_values[index]


def main(path):
    elapsed = defaultdict(list)
    failed = defaultdict(int)
    codes = defaultdict(lambda: defaultdict(int))
    first, last = None, None
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            label = row["label"]
            elapsed[label].append(int(row["elapsed"]))
            codes[label][row["responseCode"]] += 1
            if row["success"] != "true":
                failed[label] += 1
            ts = int(row["timeStamp"])
            end = ts + int(row["elapsed"])
            first = ts if first is None else min(first, ts)
            last = end if last is None else max(last, end)
    seconds = max((last - first) / 1000, 0.001) if first is not None else 0.001
    print(f"{'label':<24}{'count':>8}{'err%':>7}{'rps':>8}{'avg':>7}{'p50':>7}{'p90':>7}{'p95':>7}{'p99':>7}{'max':>7}  codes")
    for label in sorted(elapsed):
        values = sorted(elapsed[label])
        count = len(values)
        print(
            f"{label:<24}{count:>8}{failed[label] / count * 100:>7.2f}{count / seconds:>8.1f}"
            f"{sum(values) / count:>7.0f}{percentile(values, 50):>7}{percentile(values, 90):>7}"
            f"{percentile(values, 95):>7}{percentile(values, 99):>7}{values[-1]:>7}  {dict(codes[label])}"
        )


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: summarize-jtl.py <result.jtl>")
    main(sys.argv[1])
