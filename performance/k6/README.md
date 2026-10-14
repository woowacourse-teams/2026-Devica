# 성능 테스트 작성

새 API의 성능 테스트를 추가할 때 본다. 돌리고 기록하는 방법은 [성능 테스트 안내](../README.md)에 있다.

API마다 담당자가 각자 테스트를 작성한다. 결과를 서로 비교할 수 있도록 판정 기준과 측정 구간은 `common/`에 한 번만 두고, 테스트는 API별 폴더로 나눈다.

## 구조

```
performance/
├── README.md           # 돌리고 기록하는 방법
├── 기준과_근거.md       # 기준과 가정의 근거
├── data/               # 테스트 데이터 적재 SQL
├── results/            # k6 결과(Git 제외)
└── k6/
    ├── README.md       # 이 문서
    ├── common/         # 팀 공통 모듈
    └── product-list/   # 상품 목록 조회
```

API별 폴더 이름은 `도메인-동작` 형태로 한다. 각 폴더는 다음으로 구성한다.

| 파일 | 역할 |
| --- | --- |
| `README.md` | 시나리오, 가정, 측정 전 데이터 확인 방법 |
| `requests.js` | 요청과 응답 검사 |
| 조건 모듈(필요할 때) | 요청 조건을 만드는 모듈. 예: `product-list/sessions.js` |
| `smoke.js` | 적은 부하로 스크립트와 서버가 정상인지 확인한다. 유효성 기준만 적용하고 SLO는 판정하지 않는다 |
| `load.js` | 예상 트래픽으로 부하를 건다 |

## 공통 모듈 (`common/`)

새 테스트는 이 모듈을 쓰고 기준을 따로 적지 않는다.

| 항목 | 내용 |
| --- | --- |
| 서버 주소 | `BASE_URL` 환경 변수. 기본값 `http://localhost:8080` |
| 테스트 표시 헤더 | `userAgent(testName)`. `User-Agent: devica-k6/<테스트 이름>`을 붙인다 |
| 요청 옵션 | `params(tags)`. 타임아웃 5초, 리다이렉트 0. 기대 상태를 200~499로 두어, 오류율(`http_req_failed`)에는 5xx, 타임아웃, 연결 오류만 들어간다 |
| 측정 구간 | `warmupAndMeasure(name, scenario, measure?)`. 테스트가 정한 부하 설정으로 `<name>_warmup`(1분)과 `<name>_measure`(10분) 시나리오를 만든다. 측정 구간의 부하 모양이 다르면(예: 피크) `measure`를 따로 주고, 이때 `measure`가 10분을 채워야 한다 |
| SLO 기준 | `measureThresholds(name)`. `<name>_measure`의 요청에만 걸고, 오류율만 판정한다. k6 p95와 요청 수는 요약에 남기기만 한다 |
| 유효성 기준 | `validityThresholds`. 형식 검사(`checks`) 100% 통과, `dropped_iterations` 0. 워밍업을 포함한 전체에 적용한다 |
| 응답 검사 | `checkResponse()`. 3xx·4xx가 아닌지 확인하고, 200 응답이면 API별 형식 검사를 한다 |
| 결과 통계 항목 | `summaryTrendStats`. `avg`, `med`, `p(95)`, `p(99)`, `max` |

새 테스트를 추가하면 [성능 테스트 안내](../README.md)의 테스트 목록에 한 줄 추가한다.
