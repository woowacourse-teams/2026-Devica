#!/bin/sh
# 커밋에 붙은 리뷰 트레일러가 그 커밋의 코드와 맞는지 검사한다. pre-push 와 CI 가 쓴다.
#
#   scripts/review-check.sh [커밋]   기본값 HEAD
#
# 통과 조건: frontend/ 에 변경이 없거나, 기준 브랜치 이후 커밋 중 hash 가 일치하는 아래 트레일러가 있고, 그중 가장 최근 것의 unresolved 가 0 이다.
#
#   Frontend-Review: hash=<review-hash.sh 출력> base=<기준 브랜치> unresolved=<미해결 필수 항목 수>
#
# 트레일러는 코드와 같은 커밋이 아니어도 된다. 해시가 검사하는 커밋의 frontend/ diff 와 같으면 리뷰한 코드가 그대로라는 뜻이다.
# 해시의 기준 브랜치는 트레일러의 base= 를 따른다(없으면 origin/develop). 다른 PR 위에 쌓은 브랜치는 부모 브랜치가 적힌다.
# CI 는 REVIEW_BASE 로 실제 PR 의 base 를 주고, 트레일러의 base= 대신 그 값으로 계산한다. 기준을 좁게 적어 리뷰 범위를 줄이는 것을 막는다.
# 부모 PR 이 머지되고 base 가 develop 으로 바뀌어도, 부모 기준 diff 와 develop 기준 diff 가 같으면 해시가 같아 다시 리뷰하지 않아도 된다.
set -eu

REV=${1:-HEAD}
SCOPE=frontend
DEFAULT_BASE=origin/develop
TRAILER=Frontend-Review
SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

cd "$(git rev-parse --show-toplevel)"

fail() {
  echo "✖ 리뷰 검사 실패: $1" >&2
  echo "  → 에이전트에서 frontend-review Skill 을 실행하고, 안내받은 $TRAILER 트레일러를 붙여 커밋하세요." >&2
  exit 1
}

require_ref() {
  git rev-parse --verify --quiet "$1" >/dev/null || fail "$1 을 찾을 수 없습니다. git fetch origin 을 실행하세요."
}

# develop 에 비해 frontend/ 가 그대로면 리뷰할 것이 없다.
require_ref "${REVIEW_BASE:-$DEFAULT_BASE}"
FORK=$(git merge-base "${REVIEW_BASE:-$DEFAULT_BASE}" "$REV")
if git diff --quiet "$FORK" "$REV" -- "$SCOPE"; then
  exit 0
fi

# 이 브랜치에서 만든 커밋의 트레일러만 본다. 최근 커밋부터 나온다.
TRAILERS=$(git log --format="%(trailers:key=$TRAILER,valueonly,separator=%x0A)" "$FORK..$REV" | sed '/^$/d')
[ -n "$TRAILERS" ] || fail "커밋에 $TRAILER 트레일러가 없습니다."

field() {
  echo "$1" | tr ' ' '\n' | sed -n "s/^$2=//p" | head -n 1
}

echo "$TRAILERS" | {
  while read -r trailer; do
    base=${REVIEW_BASE:-$(field "$trailer" base)}
    base=${base:-$DEFAULT_BASE}
    git rev-parse --verify --quiet "$base" >/dev/null || continue
    [ "$(field "$trailer" hash)" = "$("$SCRIPT_DIR/review-hash.sh" -b "$base" "$REV")" ] || continue

    # 해시가 맞는 트레일러 중 가장 최근 것이 최종 판정이다. 더 오래된 unresolved=0 으로 되돌아가지 않는다.
    unresolved=$(field "$trailer" unresolved)
    [ "$unresolved" = 0 ] || fail "미해결 필수 항목이 남아 있습니다 (unresolved=${unresolved:-값 없음}). 해결하거나 기각한 뒤 다시 리뷰하세요."
    echo "✔ 리뷰 검사 통과 (기준 $base)"
    exit 0
  done

  fail "현재 코드와 diff 해시가 일치하는 트레일러가 없습니다. 리뷰 뒤에 코드가 바뀌었거나 리뷰를 하지 않았습니다."
}
