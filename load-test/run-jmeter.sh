#!/usr/bin/env bash
# JMeter를 비GUI로 돌리고 결과를 요약한다. 사용법은 load-test/README.md 참고.
#   SCENARIO=entry SCANNERS=20 DURATION=30 INTERNAL_TOKEN=... ./load-test/run-jmeter.sh
set -euo pipefail
cd "$(dirname "$0")"

SCENARIO="${SCENARIO:-entry}"
SCANNERS="${SCANNERS:-20}"
DURATION="${DURATION:-30}"

# 알 수 없는 시나리오는 아무 요청도 하지 않는 빈 반복이 되어 JMeter가 StackOverflowError로 멈추므로 먼저 막는다
case "$SCENARIO" in
  entry | entry-duplicate | program | mixed) ;;
  *)
    echo "알 수 없는 SCENARIO: $SCENARIO (entry | entry-duplicate | program | mixed)" >&2
    exit 1
    ;;
esac
OUT="${OUT:-$(mktemp -d)}"
RESULT="$OUT/result-${SCENARIO}-${SCANNERS}.jtl"

# 시나리오마다 스텁의 입장 기록(이미 입장 409)을 비워 같은 조건에서 시작한다
curl -s -X POST "http://127.0.0.1:${STUB_PORT:-18090}/__reset" >/dev/null || true

rm -f "$RESULT"
jmeter -n -t jmeter/scan.jmx \
  -Jscenario="$SCENARIO" -Jscanners="$SCANNERS" -Jduration="$DURATION" \
  -Jhost="${HOST:-127.0.0.1}" -Jport="${PORT:-18080}" -JexpoId="${EXPO_ID:-0b1f6c3e-52a4-4d6e-9d57-4b7a1e5c9a10}" \
  -JinternalToken="${INTERNAL_TOKEN:-}" \
  -Jjmeter.save.saveservice.output_format=csv \
  -Jjmeter.save.saveservice.timestamp_format=ms \
  -l "$RESULT" -j "$OUT/jmeter.log" >/dev/null

echo "== scenario=$SCENARIO scanners=$SCANNERS duration=${DURATION}s"
python3 summarize-jtl.py "$RESULT"
