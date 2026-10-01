#!/usr/bin/env bash
# CodeDeploy 의 AfterInstall 훅. 번들에 실려온 이미지를 올리고 컨테이너를 교체한다.
# 번들 안의 image.tar 에 의존하므로 배포가 끝난 뒤 단독으로 다시 실행할 수는 없다.
set -euo pipefail

APP_DIR=/opt/app
RELEASE_DIR="${APP_DIR}/release"

# 볼륨 이름은 compose 프로젝트 이름이 접두사로 붙어 정해지고(예: app_mysql-data),
# 프로젝트 이름의 기본값은 실행 디렉터리 이름이다. 여기가 어긋나면 빈 볼륨이 새로 생겨
# 운영 DB 가 통째로 사라진 것처럼 보인다. 기본값에 기대지 않고 명시한다.
# 현재 운영 볼륨이 app_mysql-data 이므로 프로젝트 이름은 app 이다. 바꾸면 DB 를 잃는다.
export COMPOSE_PROJECT_NAME=app

IMAGE_TAG="$(cat "${RELEASE_DIR}/image-tag.txt")"
echo "배포할 이미지 태그: ${IMAGE_TAG}"

docker load -i "${RELEASE_DIR}/image.tar"
# 롤백은 CodeDeploy 가 이전 리비전 번들을 다시 받으므로 여기 남겨둘 이유가 없다.
rm -f "${RELEASE_DIR}/image.tar"

# 배포되는 커밋의 compose 파일이 항상 적용되게 한다.
install -m 644 "${RELEASE_DIR}/docker-compose.server.yml" "${APP_DIR}/docker-compose.server.yml"
install -D -m 644 "${RELEASE_DIR}/monitoring/config.alloy" "${APP_DIR}/monitoring/config.alloy"

cd "${APP_DIR}"

# 이미지의 실행 사용자에 맞춰 로그 전용 디렉터리를 준비한다. DB 데이터와 비밀값은 연결하지 않는다.
APP_LOG_UID="$(docker run --rm --entrypoint id "devica:${IMAGE_TAG}" -u)"
DB_LOG_UID="$(docker run --rm --entrypoint id mysql:8.4 -u mysql)"
# Alloy의 GID를 공통 읽기 그룹으로 사용한다. setgid로 새 파일에도 그룹이 상속된다.
ALLOY_UID=473
ALLOY_GID=473
# 서버의 install 은 uutils 라 -o/-g 에 숫자 ID 를 주면 실패한다. 숫자 ID 는 chown 으로 지정한다.
mkdir -p "${APP_DIR}/logs/app" "${APP_DIR}/logs/db"
chown "0:${ALLOY_GID}" "${APP_DIR}/logs"
chown "${APP_LOG_UID}:${ALLOY_GID}" "${APP_DIR}/logs/app"
chown "${DB_LOG_UID}:${ALLOY_GID}" "${APP_DIR}/logs/db"
chmod 750 "${APP_DIR}/logs"
chmod 2750 "${APP_DIR}/logs/app" "${APP_DIR}/logs/db"
# 이전 배포에서 만들어진 파일도 Alloy가 읽을 수 있게 한다.
find "${APP_DIR}/logs/app" "${APP_DIR}/logs/db" -type f -exec chown ":${ALLOY_GID}" {} +
find "${APP_DIR}/logs/app" "${APP_DIR}/logs/db" -type f -exec chmod g+r {} +
# 기존 root 소유의 읽기 위치도 일반 계정으로 이전한다. DB 데이터 볼륨은 건드리지 않는다.
if ! docker run --rm --user 0:0 --entrypoint chown \
  -v "${COMPOSE_PROJECT_NAME}_alloy-data:/var/lib/devica-alloy" \
  mysql:8.4 -R "${ALLOY_UID}:${ALLOY_GID}" /var/lib/devica-alloy; then
  echo "alloy 읽기 위치 볼륨의 권한을 변경하지 못했다. 로그·메트릭 수집이 중단될 수 있으나 앱 배포는 계속한다."
fi
install -D -m 750 "${RELEASE_DIR}/scripts/rotate-db-log.sh" "${APP_DIR}/scripts/rotate-db-log.sh"
install -m 644 "${RELEASE_DIR}/monitoring/devica-db-logrotate.service" /etc/systemd/system/devica-db-logrotate.service
install -m 644 "${RELEASE_DIR}/monitoring/devica-db-logrotate.timer" /etc/systemd/system/devica-db-logrotate.timer
systemctl daemon-reload
systemctl enable --now devica-db-logrotate.timer

# IMAGE_TAG 줄만 바꾼다. 운영 비밀값이 있는 다른 줄은 건드리지 않는다.
if grep -q '^IMAGE_TAG=' .env; then
  sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=${IMAGE_TAG}|" .env
else
  echo "IMAGE_TAG=${IMAGE_TAG}" >> .env
fi

docker compose -f docker-compose.server.yml up -d app db

# 수집 에이전트는 이미지를 받지 못하거나 뜨지 못해도 앱 배포를 실패시키지 않는다.
# 설정 파일은 마운트라서 내용이 바뀌어도 up 이 컨테이너를 교체하지 않는다. restart 로 새 설정을 읽게 한다.
if ! { docker compose -f docker-compose.server.yml up -d alloy && docker compose -f docker-compose.server.yml restart alloy; }; then
  echo "alloy 를 띄우지 못했다. 앱 배포는 계속한다."
fi

# 태그가 붙은 옛 이미지는 dangling 이 아니라서 그냥 두면 계속 쌓인다.
# 배포마다 레이어가 늘어나므로 일주일치만 남긴다.
docker image prune -af --filter "until=168h"
