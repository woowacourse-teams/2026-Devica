# 성능 테스트

부하 테스트를 돌리고 결과를 기록하는 방법을 안내한다. 기준과 가정을 왜 그렇게 정했는지는 [기준과 근거](기준과_근거.md)에, 새 테스트를 작성하는 방법은 [k6/README.md](k6/README.md)에 있다.

| 테스트 | 대상 API | 문서 |
| --- | --- | --- |
| 상품 목록 조회 | `GET /api/product-categories/{categoryCode}/products` | [k6/product-list/README.md](k6/product-list/README.md) |

## 판정 기준

**측정 구간(10분)** 의 값으로 판정한다. 두 기준을 모두 만족해야 통과다.

| 기준 | 값 | 어디서 보나 |
| --- | --- | --- |
| 오류율 (5xx + 5초 타임아웃) | 회차마다 `1%` 이하 | k6 결과 |
| 서버 처리 시간 p95 | 3회의 중앙값이 `200ms` 이하 | Grafana 요청 로그 |

아래 중 하나라도 해당하면 그 회차는 **무효**다. 결과를 쓰지 않고 원인을 고친 뒤 다시 잰다.

- 3xx·4xx 응답이 있다 (스크립트가 의도와 다른 요청을 보냈다)
- 200 응답의 형식 검사(`checks`)가 하나라도 실패했다
- `dropped_iterations`가 0이 아니다 (k6가 계획한 부하를 다 걸지 못했다)
- 워밍업 첫 10초가 그 뒤보다 뚜렷이 느리다 (서버가 덜 데워졌다)

## 측정 순서

명령은 레포 루트에서 실행한다. 아래는 상품 목록 기준이고, 다른 테스트는 파일 경로와 시나리오 이름만 바꾼다.

### 1. 공지

팀 채널에 시작·종료 시각을 공지하고, **공지한 시각에 시작한다.** 스모크 1분과 부하 3회(회차당 약 12분)를 합쳐 약 40분을 잡는다.

- 테스트 중에는 develop 머지를 보류한다. 머지되면 재배포되어 측정이 깨진다.
- 두 사람이 동시에 부하 테스트를 돌리지 않는다.
- 측정 중에는 개발 서버를 쓰지 않는다. 요청 로그로 판정하는데 다른 요청이 섞이면 값이 달라진다.

### 2. 데이터 확인

테스트 데이터가 기준과 같아야 개선 전후를 비교할 수 있다. 각 테스트 README의 "측정 전 데이터 확인" 쿼리를 개발 DB에 실행하고 기준 결과와 비교한다.

개발 DB는 외부에 열려 있지 않아 SSH 터널로 접속한다. 자세한 내용은 [백엔드 배포 가이드](../docs/백엔드_배포_가이드.md)에 있다.

```bash
ssh -L 3310:<dev RDS 엔드포인트>:3306 ubuntu@<dev EC2 주소>   # 터널을 열어 둔 채로
mysql -h 127.0.0.1 -P 3310 -u <DB 사용자> -p devica              # 다른 터미널에서 접속해 쿼리 실행
```

### 3. 스모크

스크립트와 서버가 정상인지 확인하고 서버를 데운다. 끝에 `checks`와 `dropped_iterations`가 ✓면 다음으로 넘어간다.

```bash
k6 run -e BASE_URL=https://2026-devica.vercel.app performance/k6/product-list/smoke.js
```

개발 서버 주소는 `https://2026-devica.vercel.app`이다. Vercel이 `/api` 요청을 개발 서버(`dev.devica.co.kr`)로 넘겨 주므로, k6 응답 시간에는 Vercel을 거치는 시간이 들어간다. 판정은 서버 처리 시간으로 하므로 영향이 없다.

### 4. 부하 (3회)

같은 명령을 세 번 이어서 실행한다. 회차마다 워밍업이 들어 있으므로 사이에 쉬지 않는다.

```bash
T=$(date +%Y%m%d-%H%M); echo "k6 시작 $(date +%H:%M:%S)"
k6 run -e BASE_URL=https://2026-devica.vercel.app \
  --summary-export=performance/results/product-list-load-$T.json \
  --out json=performance/results/product-list-load-$T-raw.json \
  performance/k6/product-list/load.js
```

한 회차는 워밍업 1분 → 측정 10분이다. 출력된 **k6 시작 시각에 1분을 더한 때부터 10분**이 측정 구간이다. 예를 들어 `k6 시작 14:33:24`면 측정 구간은 14:34:24~14:44:24다. 이 시각을 기록 표에 적고, Grafana 조회에도 그대로 쓴다.

결과 파일은 `performance/results/`에 남는다. 이 폴더는 Git에서 제외하므로, 비교에 쓴 결과는 PR에 기록 표로 남긴다.

### 5. 수집과 기록

회차가 끝날 때마다 [값 수집 방법](#값-수집-방법)대로 값을 모아 [기록 표](#기록-표)를 채운다. 3회가 끝나면 종료를 공지한다.

## 값 수집 방법

### k6 결과에서

요약 파일에서 측정 구간 값만 뽑는다. `s`는 시나리오 이름(상품 목록은 `product_list`)이다.

```bash
jq -r --arg s product_list '.metrics as $m |
  "k6 p95 / p99 (ms): \($m["http_req_duration{scenario:\($s)_measure}"]["p(95)"] | round) / \($m["http_req_duration{scenario:\($s)_measure}"]["p(99)"] | round)",
  "오류율: \($m["http_req_failed{scenario:\($s)_measure}"].value * 10000 | round / 100)%",
  "실제 RPS: \($m["http_reqs{scenario:\($s)_measure}"].count / 600 * 100 | round / 100)",
  "dropped_iterations: \($m.dropped_iterations.count)"' performance/results/product-list-load-<시각>.json
```

헷갈리기 쉬운 것:

- k6 화면의 `http_req_failed`에서 `passes`는 **실패한 요청 수**다. 오류율은 `value`(위 명령의 값)를 본다.
- 요약 화면의 `rate`는 워밍업까지 포함한 값이라 쓰지 않는다. 위 명령은 측정 구간만 계산한다.
- `p(95)>=0`, `count>0`처럼 판정과 상관없는 기준이 ✓로 보이는데, 측정 구간 값을 요약에 남기려고 걸어 둔 것이다. 통과를 뜻하지 않는다.
- 타임아웃이 5%를 넘으면 k6 p95는 5,000ms에 붙어 의미가 없다. 이때는 오류율로 비교하고, 요청별 결과(`-raw.json`)로 1분 단위 오류율 추이를 함께 남긴다.
- 오류가 많으면 실제 RPS가 계획보다 낮게 나온다. 실패한 세션은 다음 페이지를 요청하지 않기 때문이다.

### Grafana 요청 로그에서 (서버 처리 시간)

판정에 쓰는 값이다.

1. Grafana → **Explore**를 열고, 데이터 소스를 `grafanacloud-logs`로 고른다.
2. 오른쪽 위 시간 선택에서 **Absolute time range**를 열어 From·To에 측정 구간 시작·끝 시각을 넣는다.
3. 쿼리 편집기를 **Code** 모드로 바꾸고 아래 쿼리를 붙여 넣는다. `<API 경로 패턴>`은 테스트 README의 대상 API다(상품 목록은 `/api/product-categories/{categoryCode}/products`).
4. 쿼리 옵션의 Type을 **Instant**로 바꾸고 실행한다. To 시각 기준으로 직전 10분을 계산한다.
5. 결과는 나노초다. 1,000,000으로 나눠 ms로 적는다. `0.95`를 `0.5`, `0.99`로 바꿔 p50, p99도 얻는다.

```logql
quantile_over_time(0.95, {env="dev"} |= "요청 처리 완료" | json | http_route="<API 경로 패턴>" | unwrap event_duration [10m]) by (env)
```

### Grafana 지표에서 (서버 자원)

판정에는 쓰지 않고, 기준을 넘었을 때 원인을 찾는 데 쓴다.

1. Grafana → **Explore**를 열고, 데이터 소스를 Prometheus로 고른다(`Devica Overview` 대시보드 위쪽 `ds`에 선택된 것과 같다).
2. 시간 범위와 Type은 요청 로그와 같게 둔다(측정 구간, **Instant**).
3. 아래 쿼리를 하나씩 실행한다.

| 기록 표 항목 | 쿼리 | 단위 |
| --- | --- | --- |
| 서버 p95 히스토그램 | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{env="dev", uri="<API 경로 패턴>"}[10m])))` | 초 → ms로 바꿔 적는다 |
| CPU Usage 평균 | `avg_over_time((1 - avg(rate(node_cpu_seconds_total{env="dev", mode="idle"}[2m])))[10m:1m])` | 0~1 → %로 적는다 |
| CPU Usage 최대 | 위 쿼리의 `avg_over_time`을 `max_over_time`으로 | 〃 |
| Available Memory 최소 | `min_over_time(node_memory_MemAvailable_bytes{env="dev"}[10m])` | 바이트 → MiB |
| Heap Usage 최대 | `max_over_time(sum(jvm_memory_used_bytes{env="dev", area="heap"})[10m:1m])` | 바이트 → MiB |
| GC Pause 합계 | `sum(increase(jvm_gc_pause_seconds_sum{env="dev"}[10m]))` | 초 → ms |
| DB Connections 사용 중 최대 | `max_over_time(sum(hikaricp_connections_active{env="dev"})[10m:1m])` | 개 |
| DB Connections 대기 중 최대 | `max_over_time(sum(hikaricp_connections_pending{env="dev"})[10m:1m])` | 개 |

- 지표는 60초마다 수집된다. 1분보다 짧게 치솟은 값은 놓칠 수 있다.
- 히스토그램 p95는 구간 안에서 값을 추정해 실제보다 크게 나올 수 있다. 그래서 판정은 요청 로그로 한다.
- 시간대별 흐름을 보고 싶으면 `Devica Overview` 대시보드에서 `env`를 `dev`로, 시간 범위를 측정 구간으로 둔다. 대시보드 패널은 5분 평균이라 짧은 피크가 낮게 보인다.

### AWS 콘솔에서 (DB)

DB 지표는 Grafana에 없다.

1. AWS 콘솔 → **RDS** → 데이터베이스 → `db-devica-dev` → **모니터링** 탭을 연다.
2. 시간 범위를 측정 구간으로 맞추고 **CPUUtilization** 그래프에서 평균과 최대를 읽는다.

### 그 밖의 항목

| 기록 표 항목 | 적는 것 |
| --- | --- |
| 측정 일시 (측정 구간) | 날짜와 측정 구간. 예: `10/14 14:34:24~14:44:24` |
| 장소·네트워크 | k6를 돌린 곳. 예: `캠퍼스 Wi-Fi`. 개선 전후는 같은 곳에서 잰다 |
| 서버 커밋 | 개발 서버에 배포된 커밋. `git fetch && git log -1 --format=%h origin/develop`으로 확인하고, AWS CodePipeline에서 개발 환경의 마지막 실행이 이 커밋으로 성공했는지 본다 |
| 데이터 확인 결과 | 2단계 쿼리의 결과와 기준과 같은지 여부 |

## 기록 표

회차마다 한 칸씩 채운다.

| 항목 | 1회차 | 2회차 | 3회차 | 집계 |
| --- | --- | --- | --- | --- |
| 측정 일시 (측정 구간) | | | | |
| 장소·네트워크 | | | | |
| 서버 커밋 | | | | |
| 데이터 확인 결과 | | | | |
| **서버 처리 시간 p50 / p95 / p99 (ms)** | | | | **p95 중앙값 (최소~최대)로 판정** |
| **오류율** | | | | **회차마다 판정** |
| k6 p95 / p99 (ms, 참고) | | | | |
| 실제 RPS | | | | |
| `dropped_iterations` | | | | 모두 0이어야 유효 |
| 서버 p95 히스토그램 (ms) | | | | |
| CPU Usage 평균 / 최대 | | | | |
| Available Memory 최소 | | | | |
| Heap Usage 최대 | | | | |
| GC Pause 합계 | | | | |
| DB Connections 사용 중 / 대기 중 최대 | | | | |
| RDS CPU 평균 / 최대 | | | | |
