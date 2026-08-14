# AWS 실험 MVP 배포 런북

대상 URL: `https://devica.land2ing.site/`  
대상 브랜치: `codex/experiment-ai-probe`  
배포 태그: `probe-2026-08-14`

## 원칙

- EC2는 SSH가 아니라 AWS Systems Manager Session Manager로 접속한다.
- 기존 ELB, DNS, HTTPS 설정을 변경하지 않는다.
- EC2에서 ARM64 이미지를 빌드한다.
- 운영 DB 볼륨을 삭제할 수 있는 `docker compose down -v`를 실행하지 않는다.
- 질문 세트가 승인되기 전에는 공개 트래픽을 받지 않는다.
- 앱만 교체하고 MySQL 컨테이너와 `mysql-data` 볼륨은 유지한다.

## 1. 배포 전 읽기 전용 점검

AWS 콘솔에서 EC2 인스턴스를 선택하고 **Connect → Session Manager**로 접속한다.

```bash
uname -m
cd /opt/app
docker compose -f docker-compose.prod.yml ps
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker volume ls
df -h
free -h
grep '^IMAGE_TAG=' .env
curl -fsS http://127.0.0.1/actuator/health
```

확인할 값:

- `uname -m`이 `aarch64`인지
- `devica-app`, `devica-db`의 현재 상태
- 현재 `IMAGE_TAG`
- `mysql-data` 볼륨 존재 여부
- 디스크와 메모리 여유

현재 태그는 롤백을 위해 별도로 기록한다. `.env` 전체를 출력하지 않는다.

## 2. 코드와 이미지 준비

질문 세트 승인과 전체 테스트가 끝난 커밋만 사용한다.

```bash
cd /opt/app
git fetch origin
git checkout codex/experiment-ai-probe
git pull --ff-only origin codex/experiment-ai-probe

DEPLOY_COMMIT_SHA=$(git rev-parse HEAD)
./gradlew clean bootJar
docker build -t ghcr.io/woowacourse-teams/2026-devica:"${DEPLOY_COMMIT_SHA}" .
```

`DEPLOY_COMMIT_SHA`는 이 배포 절차에서만 쓰는 변수다. 이미지 빌드가 실패하면 기존 앱을 건드리지 않고 중단한다.

## 3. 실험 이벤트 테이블 적용

MySQL 컨테이너 환경변수를 사용해 스키마를 적용한다. SQL은 `CREATE TABLE IF NOT EXISTS`이므로 동일 파일을 다시 실행해도 기존 이벤트를 삭제하지 않는다.

```bash
docker compose -f docker-compose.prod.yml exec -T db \
  sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE"' \
  < deploy/sql/001_create_experiment_event.sql
```

적용 확인:

```bash
docker compose -f docker-compose.prod.yml exec -T db \
  sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SHOW TABLES LIKE '\''experiment_event'\'';"'
```

## 4. 앱 컨테이너 교체

새 태그를 `.env`의 `IMAGE_TAG`에 기록하되 다른 환경변수는 수정하지 않는다.

```bash
sed -i.bak "s/^IMAGE_TAG=.*/IMAGE_TAG=${DEPLOY_COMMIT_SHA}/" .env
docker compose -f docker-compose.prod.yml up -d app
docker compose -f docker-compose.prod.yml ps
```

`DDL_AUTO=validate`를 사용하므로 스키마가 맞지 않으면 앱이 기동하지 않는다. 이때 임의로 `update`로 바꾸지 않고 SQL과 엔티티 차이를 확인한다.

## 5. 검증

EC2 내부:

```bash
curl -fsS http://127.0.0.1/health
curl -fsS -o /dev/null -w '%{http_code}\n' http://127.0.0.1/
```

외부:

```bash
curl -fsS https://devica.land2ing.site/health
curl -fsS -o /dev/null -w '%{http_code}\n' https://devica.land2ing.site/
```

완료 조건:

- 내부와 외부 `/health`가 `UP`과 HTTP 200을 반환한다.
- 외부 `/`가 HTTP 200을 반환한다.
- 추천 흐름을 완료할 수 있다.
- 인라인 피드백을 제출할 수 있다.
- `experiment_event`에 테스트 이벤트가 저장된다.
- MySQL 3306 포트가 외부에 공개되지 않는다.

이벤트 저장 확인:

```bash
docker compose -f docker-compose.prod.yml exec -T db \
  sh -c 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" -e "SELECT event_name, question_set_version, received_at FROM experiment_event ORDER BY id DESC LIMIT 5;"'
```

## 6. 롤백

새 앱이 정상 기동하지 않거나 외부 검증이 실패하면 배포 전 기록한 태그로 앱만 되돌린다.

```bash
ROLLBACK_IMAGE_TAG=<배포_전_IMAGE_TAG>
sed -i.bak "s/^IMAGE_TAG=.*/IMAGE_TAG=${ROLLBACK_IMAGE_TAG}/" .env
docker compose -f docker-compose.prod.yml up -d app
curl -fsS http://127.0.0.1/actuator/health
```

실험 이벤트 테이블과 MySQL 볼륨은 삭제하지 않는다.
