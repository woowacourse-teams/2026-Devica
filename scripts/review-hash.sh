#!/bin/sh
# 리뷰 대상 코드의 diff 해시를 출력한다. frontend-review Skill, pre-push, CI 가 모두 이 스크립트를 쓴다.
#
#   scripts/review-hash.sh [-b <기준 브랜치>]          작업 트리 기준 (커밋 안 한 변경과 새 파일 포함). 리뷰 시점에 쓴다
#   scripts/review-hash.sh [-b <기준 브랜치>] <커밋>   그 커밋 기준. push·CI 시점에 쓴다
#
# 기준점은 기준 브랜치(기본 origin/develop)와의 merge-base, 범위는 frontend/ 다.
# 기준 브랜치는 PR 이 머지될 브랜치다. 다른 PR 위에 쌓은 브랜치는 부모 브랜치를 준다. 그래야 부모의 변경이 리뷰 범위에 섞이지 않는다.
# .review/ 는 범위 밖이라 기록을 써도 해시가 바뀌지 않는다. 같은 내용이면 커밋 전(작업 트리)과 커밋 후(커밋)의 해시가 같다.
set -eu

SCOPE=frontend
BASE_BRANCH=origin/develop
while getopts b: option; do
  case $option in
    b) BASE_BRANCH=$OPTARG ;;
    *) exit 2 ;;
  esac
done
shift $((OPTIND - 1))

cd "$(git rev-parse --show-toplevel)"

if [ $# -ge 1 ]; then
  TREE=$(git rev-parse "$1^{tree}")
  BASE=$(git merge-base "$BASE_BRANCH" "$1")
else
  # 실제 인덱스를 건드리지 않도록 임시 인덱스에 작업 트리를 올려 트리를 만든다. untracked 파일도 여기서 들어간다.
  TMP_INDEX=$(mktemp)
  trap 'rm -f "$TMP_INDEX"' EXIT
  export GIT_INDEX_FILE="$TMP_INDEX"
  git read-tree HEAD
  git add -A -- "$SCOPE"
  TREE=$(git write-tree)
  unset GIT_INDEX_FILE
  BASE=$(git merge-base "$BASE_BRANCH" HEAD)
fi

# 사용자별 git 설정(diff 알고리즘, 이름 변경 감지, 경로 접두사 등)에 따라 diff 문자열이 달라지지 않게 옵션을 고정한다.
git -c core.quotePath=true diff --no-color --no-ext-diff --no-textconv --no-renames --full-index --binary \
  --diff-algorithm=myers --src-prefix=a/ --dst-prefix=b/ \
  "$BASE" "$TREE" -- "$SCOPE" \
  | git hash-object --stdin  # sha256sum(Linux)·shasum(macOS)처럼 OS마다 다른 명령 대신 어디서나 같은 git 을 쓴다
