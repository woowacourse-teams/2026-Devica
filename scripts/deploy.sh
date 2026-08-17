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
install -m 644 "${RELEASE_DIR}/docker-compose.prod.yml" "${APP_DIR}/docker-compose.prod.yml"

cd "${APP_DIR}"

# IMAGE_TAG 줄만 바꾼다. 운영 비밀값이 있는 다른 줄은 건드리지 않는다.
if grep -q '^IMAGE_TAG=' .env; then
  sed -i "s|^IMAGE_TAG=.*|IMAGE_TAG=${IMAGE_TAG}|" .env
else
  echo "IMAGE_TAG=${IMAGE_TAG}" >> .env
fi

docker compose -f docker-compose.prod.yml up -d

# 태그가 붙은 옛 이미지는 dangling 이 아니라서 그냥 두면 계속 쌓인다. 
# 배포마다 레이어가 늘어나므로 일주일치만 남긴다.
docker image prune -af --filter "until=168h"
