#!/bin/bash
# 구성 요소 하나를 에이전트 CLI 로 한 번 실행한다(ADR 0049). 에이전트를 부르는 곳은 이 파일의 마지막 호출 한 줄이다. 작업 폴더(레포)에서 실행한다.
# 예약 실행은 install.sh 가 등록한 launchd 작업이 origin/develop 의 이 파일을 읽어 실행한다.
#
#   run.sh <metric-collector | feedback-analyzer | recommendation-improver>
set -uo pipefail

C=${1:?구성 요소 이름이 필요하다}
ST="$HOME/.devica-product-recommendation"
mkdir -p "$ST/$C"

# 비대화 실행이라 물어볼 수 없다. 지시문이 쓰는 것만 허용하고, 그 밖은 막힌다.
# 상태 디렉터리 안의 파일 쓰기·지우기(리다이렉션, mkdir, rm 포함)는 acceptEdits 가 허용하고, 레포 쓰기는 막는다
TOOLS=(
  'Bash(git *)' 'Bash(gh *)' 'Bash(python3 *)' 'Bash(ruby *)'
  'Bash(export *)' 'Bash(mkdir *)' 'Bash(find *)' 'Bash(ls *)' 'Bash(cat *)' 'Bash(head *)' 'Bash(date *)'
  'Read' 'Edit(~/.devica-product-recommendation/**)'
)
case $C in
  metric-collector) TOOLS+=('mcp__claude_ai_PostHog__exec') ;;
  feedback-analyzer) ;;
  recommendation-improver) TOOLS+=('Bash(docker *)' 'Bash(./gradlew *)' 'Bash(cd *)' 'Bash(cp *)' 'Bash(UPDATE_SNAPSHOT=true *)') ;;
  *) echo "알 수 없는 구성 요소: $C" >&2; exit 2 ;;
esac

PROMPT="작업 폴더의 git 레포에서 \`git fetch -q origin\` 을 실행한 뒤,
\`git show origin/develop:recommendation-improvement/$C/prompt.md\` 의 출력을
이번 실행의 지시문으로 삼아 끝까지 따른다. 지시문의 SELF_REF 는 origin/develop 이다."

echo "== $(date -u +%FT%TZ) $C"
# 이 파일은 파이프로 들어온다(install.sh). 표준 입력을 끊지 않으면 claude 가 남은 스크립트를 입력으로 읽는다
# 허용 목록에 없어 막힌 명령은 결과 뒤에 적는다. 목록을 고칠 근거다
claude -p "$PROMPT" --add-dir "$ST" --permission-mode acceptEdits \
  --allowedTools "${TOOLS[@]}" --disallowedTools 'Edit(/**)' --output-format json < /dev/null \
  | python3 -c 'import json,sys; r=json.load(sys.stdin); print(r.get("result","")); [print("막힘:", d["tool_name"], json.dumps(d["tool_input"], ensure_ascii=False)) for d in r.get("permission_denials",[])]'
CODES=("${PIPESTATUS[@]}")
# 에이전트가 성공해도 결과를 읽지 못했으면 실패로 끝낸다
RC=${CODES[0]}; [ "$RC" -eq 0 ] && RC=${CODES[1]}
echo "== $(date -u +%FT%TZ) 끝 (종료 코드 $RC)"
exit "$RC"
