#!/usr/bin/env bash
# 1분마다 검사한다. 10MiB 이상인 MySQL 오류 로그를 옮긴 뒤 새 파일을 열게 한다.
set -euo pipefail

exec 9>/run/lock/devica-db-logrotate.lock
flock -n 9 || exit 0

docker exec devica-db sh -eu -c '
  cd /var/log/mysql
  [ -f error.log ] || exit 0
  [ "$(wc -c < error.log)" -ge 10485760 ] || exit 0

  # 기존 파일을 옮기고 MySQL에 새 파일을 열게 해, 복사·비우기 중 로그 유실을 피한다.
  mv error.log error.log.rotating
  if ! MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqladmin --user=root --connect-timeout=5 flush-logs error; then
    mv error.log.rotating error.log
    exit 1
  fi

  rm -f error.log.5
  for index in 4 3 2 1; do
    if [ -f "error.log.$index" ]; then
      mv "error.log.$index" "error.log.$((index + 1))"
    fi
  done
  mv error.log.rotating error.log.1
'
