# 성능 테스트

API마다 담당자가 각자 성능 테스트를 작성한다. 결과를 서로 비교할 수 있도록 통과 기준과 측정 방법은 이 문서와 `k6/common/`에 한 번만 두고, 테스트는 API별 폴더로 나눈다.

## 구조

```
performance/
├── README.md           # 이 문서. 구조, 공통 기준, 공통 측정 방법, 실행 규칙
├── data/               # 테스트 데이터 적재 SQL
├── results/            # k6 요약 결과 JSON(Git 제외)
└── k6/
    ├── common/         # 팀 공통 모듈
    ├── data/           # 로컬에서 만드는 테스트 입력(Git 제외)
    ├── product-list/   # 상품 목록 조회
    ├── product-detail/ # 상품 상세 조회
    ├── board-list/     # 게시글 목록 조회
    └── mixed-load.js   # 여러 API를 섞는 테스트
```

API별 폴더 이름은 `도메인-동작` 형태로 한다. 각 폴더는 다음으로 구성한다.

| 파일 | 역할 |
| --- | --- |
| `requests.js` | 요청과 응답 검사. 다른 테스트도 가져다 쓴다 |
| 조건 모듈(필요할 때) | 요청 조건을 만드는 모듈. 예: `product-list/sessions.js` |
| `smoke.js` | 적은 부하로 스크립트와 서버가 정상인지 확인한다 |
| `load.js` | 예상 트래픽으로 부하를 건다 |

데이터 적재는 [data/README.md](data/README.md), 상품 목록 조회 테스트는 [k6/product-list/README.md](k6/product-list/README.md), 나머지 테스트의 입력과 실행 옵션은 [k6/README.md](k6/README.md)를 본다.

## 공통 기준

| 항목 | 기준 |
| --- | --- |
| 예상 트래픽 | 홍보 직후 10분간 약 `260명` 유입 |
| API별 RPS | `260 × 방문당 해당 API 호출 수 ÷ 600` |

### SLO 판정과 결과 유효성

| 구분 | 기준 | 넘으면 |
| --- | --- | --- |
| SLO 판정 | 오류율(5xx + 타임아웃 5초) `1%` 이하, p95 응답 시간 `200ms` 이하. 오류율은 회차별로 판정한다 | 실패 |
| 결과 유효성 | 4xx 응답이 없고, 200 응답은 모두 형식 검사(`checks`)를 통과, `dropped_iterations` 0 | 그 회차는 무효. 결과를 쓰지 않고 원인을 고친 뒤 다시 측정한다 |

- 4xx는 SLO 오류로 세지 않는다. 요청 조건은 모두 유효한 조합으로 만들기 때문에, 4xx가 나왔다면 테스트가 의도와 다른 요청을 보낸 것(스크립트 버그나 API 계약 변경)이다. 또 4xx는 검증 단계에서 바로 끝나 응답이 빨라서 p95를 실제보다 좋게 만든다. 그래서 유효성에서 걸러 낸다.
- 5xx와 타임아웃은 유효성에 넣지 않는다. SLO 오류율로 판정하므로, 유효성에 넣으면 SLO가 허용한 오류 한 건으로도 회차가 무효가 된다. 형식 검사도 200 응답에만 한다.

### 공통 모듈 (`k6/common/`)

새 테스트는 이 모듈을 쓰고 기준을 따로 적지 않는다.

| 항목 | 내용 |
| --- | --- |
| 서버 주소 | `BASE_URL` 환경 변수. 기본값 `http://localhost:8080` |
| 테스트 표시 헤더 | `User-Agent: devica-k6/<테스트 이름>`. 로그와 대시보드에서 테스트 트래픽을 구분한다 |
| 요청 옵션 | 타임아웃 5초, 리다이렉트 0. 기대 상태는 200~499로 두어, 오류율(`http_req_failed`)에는 5xx와 타임아웃만 들어가게 한다 |
| SLO 기준 | `sloThresholds()`. 워밍업이 있는 테스트는 측정 구간의 요청에만 적용한다 |
| 유효성 기준 | `validityThresholds`. 형식 검사(`checks`) 100% 통과, `dropped_iterations` 0. 워밍업을 포함한 전체에 적용한다 |
| 응답 검사 | `checkResponse()`. 4xx가 아닌지 확인하고, 200 응답이면 API별 형식 검사를 한다 |
| 결과 통계 항목 | `avg`, `med`, `p(95)`, `p(99)`, `max` |

## 공통 측정 방법

### 측정 진행

| 항목 | 방법 |
| --- | --- |
| 한 회차 | 워밍업 1분 → 측정 10분을 끊지 않고 진행한다. 워밍업은 결과에서 제외한다 |
| 워밍업 길이 | 1분은 초기값이다. 첫 측정의 k6 결과로 1분 단위 p95 추이를 보고 확정한다 |
| 반복 | 같은 조건으로 3회. 회차마다 워밍업을 포함하므로 회차 사이 휴식은 두지 않는다 |
| p95 집계 | 회차별 값을 모두 기록한다. 판정과 비교는 중앙값으로, 편차는 최소~최대 범위로 남긴다 |
| 오류율 판정 | 회차별로 판정한다. 한 회차라도 1%를 넘으면 실패다 |
| 측정 전 확인 | 테스트 데이터가 기준 결과와 같은지 확인한다. 확인 방법은 API별 README에 둔다 |

### 관찰 지표

| 대상 | 지표 | 위치 |
| --- | --- | --- |
| 앱 서버 | p95 (서버 쪽) | Grafana Explore. 측정 종료 시점에 p95 패널의 쿼리 기간을 `[10m]`으로 바꿔 조회한다 |
| 앱 서버 | CPU Usage, Available Memory | Grafana `Devica Overview` |
| JVM | Heap Usage, GC Pause | Grafana `Devica Overview` |
| 커넥션 풀 | DB Connections (사용 중·대기 중) | Grafana `Devica Overview` |
| DB | CPU 사용률 | RDS 콘솔 (CloudWatch) |

대시보드의 p95는 직전 5분 기준이라 측정 구간 전체를 담지 못한다. `p95 Latency by URI` 패널의 쿼리에서 기간만 `[10m]`으로 바꾼다.

```promql
histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket{env="dev", uri!="/readyz"}[10m])))
```

DB가 RDS에 있어 DB 지표는 대시보드에 없다. 대시보드의 CPU와 메모리는 앱 서버만 나타낸다. 병목이 DB로 의심되면 해당 쿼리의 실행 계획(`EXPLAIN ANALYZE`)을 확인한다.

### 기록

k6 요약 결과는 JSON으로 `performance/results/`에 저장한다. 이 폴더는 Git에서 제외하므로, 비교에 쓴 결과는 PR이나 성능 테스트 문서에 첨부한다.

```bash
k6 run --summary-export=performance/results/<테스트 이름>-<YYYYMMDD-HHmm>.json performance/k6/<API>/load.js
```

워밍업이 있는 테스트는 측정 구간 값을 `{scenario:measure}`가 붙은 항목에서 읽는다. 실제 RPS는 `http_reqs{scenario:measure}`의 `count`를 측정 시간(600초)으로 나눈다. 요약의 `rate`는 워밍업까지 포함한 전체 시간으로 나눈 값이라 쓰지 않는다.

회차마다 아래 표를 채운다.

| 항목 | 1회차 | 2회차 | 3회차 | 집계 |
| --- | --- | --- | --- | --- |
| 측정 일시 | | | | |
| 장소·네트워크 | | | | |
| 서버 커밋 | | | | |
| 데이터 확인 결과 | | | | |
| k6 p95 (ms) | | | | 중앙값 (최소~최대) |
| k6 p99 (ms) | | | | |
| 오류율 | | | | 회차별 판정 |
| 실제 RPS | | | | |
| `dropped_iterations` | | | | 모두 0이어야 유효 |
| 서버 p95 (ms) | | | | |
| CPU Usage | | | | |
| Available Memory | | | | |
| Heap Usage | | | | |
| GC Pause | | | | |
| DB Connections (사용 중·대기 중) | | | | |
| RDS CPU | | | | |

## 개발 서버 실행 규칙

- 시작과 종료를 팀 채널에 공지한다.
- 테스트 중에는 develop 머지를 보류한다. 머지되면 재배포되어 측정이 깨진다.
- **두 사람이 동시에 부하 테스트를 실행하지 않는다.**
- 개선 전후 측정은 같은 장소, 같은 네트워크에서 한다.
