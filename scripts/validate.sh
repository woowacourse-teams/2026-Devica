#!/usr/bin/env bash
# CodeDeploy 의 ValidateService 훅. 새 컨테이너가 실제로 요청을 받는지 확인한다.
#
# 실패하면 exit 1 만 한다. 롤백은 여기서 하지 않는다.
# 배포그룹의 "배포 실패 시 롤백"이 이전 성공 리비전을 재배포하는데, 그 번들에는 그때의
# image-tag.txt 가 들어 있어 정확히 이전 이미지로 돌아간다. 직접 구현하는 것보다 정확하다.
set -euo pipefail

# ELB 가 EC2 의 80 번으로 보내고, compose 가 80 -> 컨테이너 8080 을 매핑한다.
# application-prod.yml 이 health 엔드포인트만 노출해 두었다.
HEALTH_URL="http://localhost/actuator/health"
MAX_ATTEMPTS=20
INTERVAL=3

for i in $(seq 1 "$MAX_ATTEMPTS"); do
  if body="$(curl -fsS --max-time 5 "$HEALTH_URL" 2>/dev/null)" \
     && printf '%s' "$body" | grep -q '"status":"UP"'; then
    echo "헬스체크 통과 ($i번째 시도): $body"
    exit 0
  fi
  echo "헬스체크 대기 중 ($i/$MAX_ATTEMPTS)"
  sleep "$INTERVAL"
done

echo "헬스체크 실패. $((MAX_ATTEMPTS * INTERVAL))초 동안 UP 응답이 없었다."
echo "--- 앱 컨테이너 로그 (마지막 50줄) ---"
docker logs --tail 50 devica-app 2>&1 || true
exit 1
