#!/bin/sh
# 기준 커밋에 존재하는 버전 마이그레이션의 수정·삭제를 차단한다.
# 사용법: sh scripts/check-flyway-migrations.sh <기준 커밋> [검사 커밋: HEAD]
# 작업 트리가 아닌 커밋을 검사한다. PR 에서는 기준 브랜치와 병합 결과를 전달한다.
set -eu

fail() {
  printf 'Flyway 마이그레이션 검사 실패: %s\n' "$1" >&2
  exit 1
}

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ] || [ -z "$1" ]; then
  printf '사용법: sh scripts/check-flyway-migrations.sh <기준 커밋> [검사 커밋: HEAD]\n' >&2
  exit 2
fi

BASE_REF=$1
TARGET_REF=${2:-HEAD}

cd "$(git rev-parse --show-toplevel)"

BASE_SHA=$(git rev-parse --verify --quiet --end-of-options "${BASE_REF}^{commit}") ||
  fail "기준 커밋 '$BASE_REF'을 찾을 수 없습니다. 비교 기준과 가져온 Git 이력을 확인하세요."
TARGET_SHA=$(git rev-parse --verify --quiet --end-of-options "${TARGET_REF}^{commit}") ||
  fail "검사 커밋 '$TARGET_REF'을 찾을 수 없습니다. 비교 대상과 가져온 Git 이력을 확인하세요."

# 이름·경로 변경도 기존 파일 삭제로 검사한다. 새 파일 추가(A)는 허용한다.
# 공백 변경(M)과 심볼릭 링크 등 파일 유형 변경(T)도 차단한다.
CHANGED_FILES=$(git diff --no-ext-diff --no-textconv --no-renames --name-only \
  --diff-filter=MDT "$BASE_SHA" "$TARGET_SHA" -- \
  ':(glob)src/main/resources/db/migration/**/V*.sql') ||
  fail 'Git 변경 내역을 읽지 못했습니다.'

if [ -n "$CHANGED_FILES" ]; then
  printf '수정·삭제된 기존 Flyway 마이그레이션:\n%s\n' "$CHANGED_FILES" >&2
  fail '기존 파일을 기준 커밋의 상태로 복원하고, 필요한 변경은 다음 버전의 V<버전>__<설명>.sql 파일로 추가하세요.'
fi

printf 'Flyway 마이그레이션 검사 통과: 기존 버전 파일의 변경이 없습니다.\n'
