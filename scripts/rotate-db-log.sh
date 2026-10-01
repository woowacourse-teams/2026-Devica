#!/usr/bin/env bash
# 1분마다 검사한다. 10MiB 이상인 MySQL 오류 로그를 옮긴 뒤 새 파일을 열게 한다.
set -euo pipefail

exec 9>/run/lock/devica-db-logrotate.lock
flock -n 9 || exit 0

docker exec devica-db sh -eu -c '
  # 타이머는 서버에 유지한다. 이전 앱 리비전이 파일 출력을 쓰지 않으면 회전하지 않는다.
  export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
  log_path=$(mysql --user=root --connect-timeout=5 --batch --skip-column-names \
    -e "SELECT @@GLOBAL.log_error")
  [ "$log_path" = /var/log/mysql/error.log ] || exit 0
  cd /var/log/mysql

  finish_rotation() {
    # 중단 후 다시 실행해도 MySQL이 현재 파일을 닫고 error.log를 다시 열게 한다.
    mysqladmin --user=root --connect-timeout=5 flush-logs error
    mv error.log.rotating "error.log.$(date +%s%N)"
  }

  # rename 직후나 flush 직후 중단된 작업부터 마무리한다. 실패 시 다음 실행에서 재시도한다.
  if [ -f error.log.rotating ]; then
    finish_rotation
  fi

  if [ -f error.log ] && [ "$(wc -c < error.log)" -ge 10485760 ]; then
    mv error.log error.log.rotating
    finish_rotation
  fi

  # 보관 파일 이동 도중 중단되어도 이름이 겹치지 않게 하고, 다음 실행에서 보관량을 정리한다.
  set -- error.log.[0-9]*
  if [ -e "$1" ]; then
    archives=$(ls -1t -- "$@")
    count=0
    for archive in $archives; do
      count=$((count + 1))
      [ "$count" -le 5 ] || rm -f -- "$archive"
    done
  fi
'
