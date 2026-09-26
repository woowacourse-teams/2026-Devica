#!/bin/sh
# 커밋에 담긴 리뷰 기록이 그 커밋의 코드와 맞는지 검사한다. pre-push 와 CI 가 쓴다.
#
#   scripts/review-check.sh [커밋]   기본값 HEAD
#
# 통과 조건: frontend/ 에 변경이 없거나, .review/*.md 중 diff-hash 가 일치하고 미해결 필수 항목이 없는 기록이 있다.
# 기록은 작업 트리가 아니라 커밋에서 읽는다. 기록을 커밋하지 않고 push 하면 CI 에서 막히기 때문이다.
#
# 해시의 기준 브랜치는 기록의 base: 줄을 따른다(없으면 origin/develop). 다른 PR 위에 쌓은 브랜치는 부모 브랜치가 적힌다.
# CI 는 REVIEW_BASE 로 실제 PR 의 base 를 주고, 기록의 base: 대신 그 값으로 계산한다. 기준을 좁게 적어 리뷰 범위를 줄이는 것을 막는다.
# 부모 PR 이 머지되고 base 가 develop 으로 바뀌어도, 부모 기준 diff 와 develop 기준 diff 가 같으면 해시가 같아 다시 리뷰하지 않아도 된다.
set -eu

REV=${1:-HEAD}
SCOPE=frontend
DEFAULT_BASE=origin/develop
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

cd "$(git rev-parse --show-toplevel)"

fail() {
  echo "✖ 리뷰 검사 실패: $1" >&2
  echo "  → 에이전트에서 frontend-review Skill 을 실행하고, 코드와 .review/ 기록을 함께 커밋하세요." >&2
  exit 1
}

require_ref() {
  git rev-parse --verify --quiet "$1" >/dev/null || fail "$1 을 찾을 수 없습니다. git fetch origin 을 실행하세요."
}

# develop 에 비해 frontend/ 가 그대로면 리뷰할 것이 없다.
require_ref "${REVIEW_BASE:-$DEFAULT_BASE}"
if git diff --quiet "$(git merge-base "${REVIEW_BASE:-$DEFAULT_BASE}" "$REV")" "$REV" -- "$SCOPE"; then
  exit 0
fi

RECORDS=$(git ls-tree --name-only "$REV" .review/ | grep '\.md$' || true)
[ -n "$RECORDS" ] || fail "리뷰 기록(.review/*.md)이 커밋에 없습니다."

for record in $RECORDS; do
  content=$(git show "$REV:$record")
  base=${REVIEW_BASE:-$(echo "$content" | sed -n 's/^base: //p' | head -n 1)}
  base=${base:-$DEFAULT_BASE}
  git rev-parse --verify --quiet "$base" >/dev/null || continue
  hash=$("$SCRIPT_DIR/review-hash.sh" -b "$base" "$REV")
  echo "$content" | grep -qx "diff-hash: $hash" || continue

  # 표의 5번째 칸(구분)이 필수이고 6번째 칸(상태)이 미해결인 행
  unresolved=$(echo "$content" | awk -F'|' '
    function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }
    /^\|/ && trim($5) == "필수" && trim($6) == "미해결" { print "  - " trim($2) " " trim($3) }')
  [ -z "$unresolved" ] || fail "$record 에 미해결 필수 항목이 있습니다.
$unresolved"

  echo "✔ 리뷰 검사 통과: $record (기준 $base)"
  exit 0
done

fail "현재 코드와 diff 해시가 일치하는 리뷰 기록이 없습니다. 리뷰 뒤에 코드가 바뀌었거나 리뷰를 하지 않았습니다."
