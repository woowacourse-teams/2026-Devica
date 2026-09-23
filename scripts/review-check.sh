#!/bin/sh
# 커밋에 담긴 리뷰 기록이 그 커밋의 코드와 맞는지 검사한다. pre-push 와 CI 가 쓴다.
#
#   scripts/review-check.sh [커밋]   기본값 HEAD
#
# 통과 조건: frontend/ 에 변경이 없거나, .review/*.md 중 diff-hash 가 일치하고 미해결 필수 항목이 없는 기록이 있다.
# 기록은 작업 트리가 아니라 커밋에서 읽는다. 기록을 커밋하지 않고 push 하면 CI 에서 막히기 때문이다.
set -eu

REV=${1:-HEAD}
SCOPE=frontend
BASE_BRANCH=origin/develop
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

cd "$(git rev-parse --show-toplevel)"

fail() {
  echo "✖ 리뷰 검사 실패: $1" >&2
  echo "  → 에이전트에서 review Skill 을 실행하고, 코드와 .review/ 기록을 함께 커밋하세요." >&2
  exit 1
}

git rev-parse --verify --quiet "$BASE_BRANCH" >/dev/null || fail "$BASE_BRANCH 를 찾을 수 없습니다. git fetch origin develop 을 실행하세요."

BASE=$(git merge-base "$BASE_BRANCH" "$REV")
if git diff --quiet "$BASE" "$REV" -- "$SCOPE"; then
  exit 0
fi

HASH=$("$SCRIPT_DIR/review-hash.sh" "$REV")

RECORDS=$(git ls-tree --name-only "$REV" .review/ | grep '\.md$' || true)
[ -n "$RECORDS" ] || fail "리뷰 기록(.review/*.md)이 커밋에 없습니다."

for record in $RECORDS; do
  content=$(git show "$REV:$record")
  echo "$content" | grep -qx "diff-hash: $HASH" || continue

  # 표의 5번째 칸(구분)이 필수이고 6번째 칸(상태)이 미해결인 행
  unresolved=$(echo "$content" | awk -F'|' '
    function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }
    /^\|/ && trim($5) == "필수" && trim($6) == "미해결" { print "  - " trim($2) " " trim($3) }')
  [ -z "$unresolved" ] || fail "$record 에 미해결 필수 항목이 있습니다.
$unresolved"

  echo "✔ 리뷰 검사 통과: $record"
  exit 0
done

fail "현재 코드와 diff 해시가 일치하는 리뷰 기록이 없습니다. 리뷰 뒤에 코드가 바뀌었거나 리뷰를 하지 않았습니다. (현재 해시: $HASH)"
