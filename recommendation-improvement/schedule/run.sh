#!/bin/bash
# 구성 요소 하나를 Claude Code CLI 로 한 번 실행한다(ADR 0049). 작업 폴더(레포)에서 실행한다.
# 권한 규칙·실행 옵션·출력 형식이 모두 Claude Code 의 것이라, 다른 에이전트로 바꾸려면 이 파일을 그 에이전트에 맞게 다시 쓴다.
# 예약 실행은 install.sh 가 등록한 launchd 작업이 origin/develop 의 이 파일을 읽어 실행한다.
#
#   run.sh <metric-collector | feedback-analyzer | recommendation-improver>
#   SELF_REF=origin/<브랜치> run.sh <구성 요소>   머지 전에 그 브랜치의 지시문·스크립트로 시험한다
#
# 매번 같은 준비(환경 변수, 잠금, 실행 기록 파일, 스크립트·스키마 복사, worktree)는 이 스크립트가 한다. 에이전트는 지시문의 판단만 맡는다.
# 준비를 지시문에 두면 에이전트가 `export`·함수 정의·`for`·변수 지정 같은 셸 구문을 쓰게 되는데, 아래 허용 목록이 명령 조각마다 검사해 막힌다.
set -uo pipefail

C=${1:?구성 요소 이름이 필요하다}
export GH_REPO=woowacourse-teams/2026-Devica
export SELF_REF=${SELF_REF:-origin/develop}
export ST="$HOME/.devica-product-recommendation/$C"
mkdir -p "$ST/runs"

# 에이전트는 이 구성 요소만 쓰는 clone 에서 실행한다. 평소 작업하는 clone 이 바뀔 가능성을 줄이려는 것이고, 막아 주지는 않는다(ADR 0049)
export REPO="$ST/repo"
[ -d "$REPO/.git" ] || git clone -q "$(git remote get-url origin)" "$REPO" || exit 1
cd "$REPO" || exit 1

# 비대화 실행이라 물어볼 수 없다. 지시문이 쓰는 것만 허용하고, 그 밖은 막힌다.
# 상태 디렉터리 안의 파일 쓰기·지우기(리다이렉션, mkdir, rm 포함)는 acceptEdits 가 허용한다. 레포 편집 거부는 에이전트의 편집 기능에만 걸리고, 허용한 명령의 쓰기는 막지 못한다
TOOLS=(
  'Bash(git *)' 'Bash(gh *)' 'Bash(python3 *)' 'Bash(ruby *)'
  'Bash(mkdir *)' 'Bash(find *)' 'Bash(ls *)' 'Bash(cat *)' 'Bash(head *)' 'Bash(date *)' 'Bash(printenv *)'
  'Read' 'Edit(~/.devica-product-recommendation/**)'
)
# SELF_REF 에서 상태 디렉터리로 평평하게 복사할 파일. 스크립트는 common.py 를 같은 디렉터리에서 import 한다
FILES=(common.py interface/artifacts/{analysis-result,improvement-instruction,improvement-result}.schema.yml)
case $C in
  metric-collector) TOOLS+=('mcp__claude_ai_PostHog__exec'); FILES+=(metric-collector/signals.py) ;;
  feedback-analyzer) ;;
  recommendation-improver)
    TOOLS+=('Bash(docker *)' 'Bash(./gradlew *)' 'Bash(cd *)' 'Bash(cp *)' 'Bash(UPDATE_SNAPSHOT=true *)')
    FILES+=(recommendation-improver/snapshot.py)
    export WT="$ST/worktree" SNAP=src/test/resources/recommendation/backend-development-snapshot.json ;;
  *) echo "알 수 없는 구성 요소: $C" >&2; exit 2 ;;
esac

echo "== $(date -u +%FT%TZ) $C"

# 잠금. 겹쳐 실행되면 같은 산출물을 두 번 남긴다. 6시간 넘게 남은 잠금은 앞선 실행이 비정상으로 끝난 것으로 보고 지운다.
# 에이전트가 어느 단계에서 끝나든(죽어도) 이 스크립트가 끝날 때 푼다. trap 은 잠금을 잡은 뒤에 걸어 남의 잠금을 지우지 않는다
STALE=
if ! mkdir "$ST/lock" 2>/dev/null; then
  [ -n "$(find "$ST/lock" -maxdepth 0 -mmin +360)" ] || { echo "== 다른 실행이 진행 중이라 끝낸다 (잠금: $ST/lock)"; exit 0; }
  { rm -rf "$ST/lock" && mkdir "$ST/lock"; } || exit 1
  STALE="- 6시간 넘게 남은 잠금을 지우고 다시 잡았다. 앞선 실행이 비정상으로 끝난 것이다."
fi
trap 'rm -rf "$ST/lock"' EXIT

# 실행 기록. 에이전트는 이 파일에 단계마다 무엇을 보고 무엇을 했는지 이어 적는다
TS=$(date -u +%Y%m%dT%H%M%SZ)
export RUN="$ST/runs/$TS.md"
{ echo "# 실행 $TS"; echo; echo "SELF_REF: $SELF_REF"; [ -n "$STALE" ] && echo "$STALE"; echo; } > "$RUN"
fail() { echo "- $*" >> "$RUN"; echo "== $*"; exit 1; }

# 레포 갱신과 스크립트·스키마 준비. 실행될 때마다 SELF_REF 에서 새로 읽어 온다
git fetch -q origin || fail "\`git fetch\` 실패. 아무것도 하지 않고 끝낸다."
for f in "${FILES[@]}"; do
  git show "$SELF_REF:recommendation-improvement/$f" > "$ST/${f##*/}" || fail "$f 를 $SELF_REF 에서 읽지 못했다."
done
[ -z "${WT:-}" ] || [ -d "$WT" ] || git worktree add -q --detach "$WT" origin/develop || fail "worktree 를 만들지 못했다."

# 허용 목록을 에이전트에게도 알려 준다. 모르면 env·echo·for 같은 막히는 명령을 시도하다 턴을 쓴다
CMDS=$(printf '%s\n' "${TOOLS[@]}" | sed -n 's/^Bash(\(.*\) \*)$/\1/p' | paste -sd ' ' -)
PROMPT="작업 폴더의 git 레포에서 \`git show $SELF_REF:recommendation-improvement/$C/prompt.md\` 의 출력을
이번 실행의 지시문으로 삼아 끝까지 따른다. 셸 명령은 다음으로 시작하는 한 줄 명령만 허용된다: $CMDS.
그 밖의 명령과 변수 지정·함수 정의·반복문은 막힌다. 파일은 Edit 로 쓴다."

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
