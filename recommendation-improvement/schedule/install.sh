#!/bin/bash
# 세 구성 요소의 예약 실행을 이 Mac 의 launchd 에 등록한다(ADR 0049). 레포 안 어디서 실행해도 된다.
# 등록한 작업은 실행할 때마다 git fetch 뒤 origin/develop 의 run.sh 를 실행한다. 머지된 것만 돌고, 바뀌어도 다시 등록하지 않는다.
#
#   install.sh             등록한다. 이미 있으면 새로 등록한다
#   install.sh uninstall   등록을 지운다
set -euo pipefail

REPO=$(git -C "$(dirname "$0")" rev-parse --show-toplevel)
ST="$HOME/.devica-product-recommendation"
AGENTS="$HOME/Library/LaunchAgents"
# launchd 는 로그인 셸의 PATH 를 쓰지 않는다. claude·gh·docker·java 가 있는 곳을 넣는다
RUN_PATH="$(dirname "$(command -v claude)"):$HOME/.jenv/shims:/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin"

# 이름 | 주기(plist 조각)
JOBS=(
  "metric-collector|<key>StartCalendarInterval</key><dict><key>Hour</key><integer>9</integer><key>Minute</key><integer>0</integer></dict>"
  "feedback-analyzer|<key>StartCalendarInterval</key><dict><key>Hour</key><integer>10</integer><key>Minute</key><integer>0</integer></dict>"
  "recommendation-improver|<key>StartInterval</key><integer>14400</integer>"
)

for job in "${JOBS[@]}"; do
  C=${job%%|*} WHEN=${job#*|}
  LABEL="kr.co.devica.product-recommendation.$C"
  PLIST="$AGENTS/$LABEL.plist"
  launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
  rm -f "$PLIST"
  [ "${1:-}" = uninstall ] && { echo "지움: $C"; continue; }

  mkdir -p "$AGENTS" "$ST/$C"
  cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key>
  <array>
    <string>/bin/bash</string><string>-c</string>
    <string>set -o pipefail; git fetch -q origin &amp;&amp; git show origin/develop:recommendation-improvement/schedule/run.sh | bash -s $C</string>
  </array>
  <key>WorkingDirectory</key><string>$REPO</string>
  <key>EnvironmentVariables</key><dict><key>PATH</key><string>$RUN_PATH</string></dict>
  $WHEN
  <key>StandardOutPath</key><string>$ST/$C/launchd.log</string>
  <key>StandardErrorPath</key><string>$ST/$C/launchd.log</string>
</dict>
</plist>
EOF
  launchctl bootstrap "gui/$(id -u)" "$PLIST"
  echo "등록: $C"
done
