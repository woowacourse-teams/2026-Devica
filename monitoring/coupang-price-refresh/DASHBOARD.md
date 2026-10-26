# Offers 대시보드 설정

`Devica Overview`에 **Offers** 행을 추가한다. 앱 로그 → Alloy → Grafana Cloud Loki 경로를 그대로 사용한다.
대시보드를 보는 데 이메일 알림 규칙이나 새로운 Prometheus 메트릭은 필요하지 않다.
가져올 파일은 [monitoring/dashboard.json](../dashboard.json)이다. `alert-rules.json`은 이메일 알림용으로 별개다.

## 표시 기준

| 화면 | 보여 주는 내용 |
|---|---|
| 판매처별 가격 확인 성공 | 선택한 기간 안에서 가장 최근에 종료된 실행의 `성공 / 전체 대상`. 예: `10 / 20` |
| 실행 기록 | 실행 시각, 판매처, 성공/대상, 가격 변경·동일·보류·미시도·숨김·복구 건수, 실행 ID |
| 상품별 상세 로그 | 날짜순 제품별 결과, 처리, 전후 가격, 제품 코드, 확인 링크 |
| 실패·보류 제품 | 가격 확인을 완료하지 못한 제품만 표시. 미시도도 포함 |

성공은 **가격 변경(`UPDATED`) + 가격 동일(`UNCHANGED`)**이다.
숨김에서 복구된 `RESTORED`도 가격 변화 여부에 따라 둘 중 하나에 이미 포함된다.
따라서 `UPDATED 8 + UNCHANGED 2 + 보류 10`이면 `10 / 20`이고, 숨김·복구 수를 다시 더하지 않는다.

분모는 DB의 모든 제품 수가 아니라 **그 실행에서 선택한 갱신 대상 판매상품 수**다.
현재는 쿠팡의 `ON_SALE` 또는 `HIDDEN` 상태이며 `vendorItemId`가 채워진 판매상품이다.
ID가 없거나 다른 상태라 조회 대상에서 제외된 제품은 이 분모에 포함되지 않는다.

같은 날 두 번 실행해도 `20 / 40`으로 누적하지 않는다. 최근 종료 로그 한 줄을 선택한다.
실행 중에는 이전 종료 결과가 보일 수 있으므로 실행 기록의 시각도 함께 확인한다.
종료 집계까지 못 남긴 예외는 `집계 실패`, 대상이 없는 실행은 `0 / 0`, 선택 기간에 종료 로그가 없으면 데이터 없음이다.
`집계 실패`는 성공 0건이라는 뜻이 아니다. 예외 전에 일부 제품이 저장됐을 수도 있다.
프로세스 강제 종료로 종료 로그 자체가 없으면 이전 결과가 남을 수 있다.

## 코드에서 추가한 내용

기존 가격 확인·숨김·복구 로직은 그대로 두고 관찰용 필드만 추가했다.

| 원본 JSON 필드 | Loki의 `\| json` 이후 이름 | 내용 |
|---|---|---|
| `price_refresh.seller` | `price_refresh_seller` | 모든 실행 로그의 판매처. 현재 `COUPANG` |
| `price_refresh.success` | `price_refresh_success` | 정상 종료 집계의 가격 확인 성공 수 |
| `price_refresh.total` | `price_refresh_total` | 정상 종료 집계의 전체 대상 수 |

기존 `price_refresh.run_id`, `price_refresh.outcome`, `price_refresh.result`, `product.code`,
`offer.url`, `product.url`은 실행 구분과 상세 로그에 사용한다.
판매처 필드는 로그 안에 저장하며 Alloy의 스트림 라벨을 추가하지 않는다.
새 테이블·새 클래스·추가 API 호출은 없다.

## 1. 먼저 서버 로그가 도착하는지 확인

1. 이 변경을 포함한 앱을 dev에 배포한다. 서버의 `.env`는 기존 쿠팡 키와
   `COUPANG_PRICE_REFRESH_ENABLED=true` 설정을 사용한다. 기본 실행 시각은 매일 **한국 시각 04:00**이다.
   `.env`만 수정했다면 실행 중 컨테이너가 새 환경변수를 읽도록 기존 배포 절차로 다시 생성해야 한다.
2. dev의 `DEVICA_PUBLIC_BASE_URL`은 `https://2026-devica.vercel.app`, prod는 `https://devica.co.kr`로 둔다.
3. 실제 예약 작업 실행 후 Grafana **Explore**에서 기존 Loki 데이터 소스(`grafanacloud-…-logs`)를 선택한다.
4. 시간 범위를 **Last 2 days**, 쿼리 편집기를 **Code**로 선택하고 아래 쿼리를 실행한다.

```logql
{env="dev", app="devica-app"}
| json
| price_refresh_seller="COUPANG"
```

종료 로그를 펼쳐 `price_refresh.success`, `price_refresh.total`, `price_refresh.run_id`를 확인한다.
키가 있는 이전 로그를 바꾸는 작업은 하지 않으므로 **배포 전 로그에는 새 필드가 없다**.
로컬 `./gradlew coupangPriceRefresh`가 만든 `build/reports/*.log`는 현재 서버 Alloy가 수집하지 않는다.
따라서 로컬 명령만 실행한 상태라면 Cloud에서 데이터가 안 보이는 것이 정상이다.
패널 새로고침은 Loki 로그만 조회하며 쿠팡 API를 다시 호출하지 않는다.

## 2. 준비한 JSON으로 가져오기

1. Grafana **Dashboards → New → Import**를 연다.
2. [dashboard.json](../dashboard.json)을 업로드하거나 파일 내용을 붙여 넣고 **Load**를 누른다.
3. 현재 Cloud 대시보드에 별도로 수정한 내용이 있을 수 있으므로 처음에는
   이름을 `Devica Overview - Offers 확인`, UID를 `devica-overview-offers-preview`로 바꿔 가져온다.
   기존 UID `devica-overview`로 덮어쓰면 그 대시보드 전체가 이 파일 내용으로 바뀐다.
4. **Import** 후 상단 변수를 고른다.
   - `Datasource`: 기존 `grafanacloud-…-prom`
   - `Logs Datasource`: 기존 `grafanacloud-…-logs`
   - `Env`: `dev` (확인 후 `prod`)
   - `Offer Seller`: `COUPANG`
5. 시간 범위를 **Last 2 days**로 바꾼다. 기존 전체 대시보드 기본값은 Last 6 hours이므로,
   하루 한 번 실행하는 작업은 기본 범위에 없을 수 있다.
6. 대시보드 설정의 **Time zone**을 **Asia/Seoul**로 지정하고 저장한다.
   아래쪽 **Offers** 행에서 수치와 실행 기록을 확인한다.
7. 기존 대시보드로 합칠 때는 확인한 패널을 복사하고 `offer_seller` 변수도 추가하거나,
   기존 대시보드를 내보내 보관한 뒤 업데이트된 전체 JSON을 반영한다.

파일 가져오기는 알림을 생성하거나 이메일을 보내지 않는다.

## 3. 기존 대시보드에 직접 만드는 경우

### 변수와 행

기존 `Devica Overview`를 **Edit**하고 행(Row)을 추가해 제목을 **Offers**로 설정한다.
대시보드 설정의 **Variables → Add variable**에서 다음 변수를 추가한다.

| 항목 | 값 |
|---|---|
| Name | `offer_seller` |
| Label | `Offer Seller` |
| Type | Custom |
| Values | `COUPANG` |
| Multi-value / Include All | 켬 |
| Custom all value | `.+` (판매처가 없는 과거 로그 제외) |

`env`와 `logs_ds`는 기존 변수다. 새 대시보드라면 우선 쿼리의 `$env`를 `dev`,
`${offer_seller:regex}`를 `COUPANG`으로 바꾸고 Loki 데이터 소스를 직접 선택해도 된다.

### 가격 확인 성공 카드

패널을 추가하고 데이터 소스를 **Loki**, 시각화를 **Stat**으로 고른다.
Code 모드에 아래 쿼리를 넣는다.

```logql
{env="$env", app="devica-app"}
| json
| price_refresh_seller=~"${offer_seller:regex}"
| price_refresh_outcome!=""
| __error__=""
| line_format `{{ if .price_refresh_total }}{{ .price_refresh_success }} / {{ .price_refresh_total }}{{ else }}집계 실패{{ end }}`
```

| 설정 | 값 |
|---|---|
| Query Type | **Range**. 로그 한 줄을 조회하므로 Instant가 아님 |
| Line limit | **1** |
| Direction | **Backward**. 최신 종료부터 찾음 |
| Value options → Show | Calculate |
| Calculation | Last (not null) |
| Fields | **Line**. 숫자 필드 기본값 대신 문자열 필드를 선택 |
| Text mode | Value |
| Graph mode / Color mode | None / None |
| 제목 | `$offer_seller 가격 확인 성공 (최근 종료)` |
| Repeat options → Repeat by variable | `offer_seller`, Horizontal |

`Line`이 선택지에 없으면 먼저 Query inspector에서 로그 한 줄이 반환됐는지 확인한다.
Loki 응답 필드명을 바꾸는 버전에서는 로그 본문 문자열 필드를 선택하면 된다.
총계를 한 글자씩 잘라내거나 숫자 비율로 나누지 않고 `10 / 20` 문자열 자체를 표시한다.
최근 실행이 예외로 종료돼 건수가 없으면 이전 성공 수치를 대신 가져오지 않고 `집계 실패`를 표시한다.

### 날짜별 실행 기록

시각화는 **Logs**로 만든다. 모든 로그 패널은 **Range / Backward**, Line limit **1000**,
Show time **On**, Wrap lines **On**, Log details **On**, Newest first, Deduplication **None**으로 둔다.

```logql
{env="$env", app="devica-app"}
| json
| price_refresh_seller=~"${offer_seller:regex}"
| price_refresh_outcome!=""
| __error__=""
| line_format `{{ .price_refresh_seller }} | {{ .price_refresh_outcome }} | {{ if .price_refresh_total }}성공={{ .price_refresh_success }}/{{ .price_refresh_total }} 변경={{ .price_refresh_updated }} 동일={{ .price_refresh_unchanged }} 보류={{ .price_refresh_deferred }} 미시도={{ .price_refresh_not_attempted }} 숨김={{ .price_refresh_hidden }} 복구={{ .price_refresh_restored }}{{ else }}집계 실패 오류={{ .error_type }}{{ end }} | run={{ .price_refresh_run_id }}`
```

하루의 여러 실행을 각각 한 줄로 보여 준다. 전체 실행이 실패해 제품 결과가 하나도 없는 경우도 여기에 나타난다.

### 상품별 상세 로그

시각화는 **Logs**, 위와 같은 옵션으로 설정한다.

```logql
{env="$env", app="devica-app"}
| json
| price_refresh_seller=~"${offer_seller:regex}"
| price_refresh_result!=""
| __error__=""
| line_format `{{ .price_refresh_seller }} | {{ .product_code }} | {{ .price_refresh_result }} / {{ .price_refresh_action }} | 가격={{ .price_refresh_previous_price }}→{{ .price_refresh_current_price }} | 오류={{ .error_code }} {{ .error_type }} | run={{ .price_refresh_run_id }} | Devica={{ .product_url }} | 판매처={{ .offer_url }}`
```

### 실패·보류 제품 로그

상세 로그 패널을 복제하고 `price_refresh_result!=""` 바로 다음에 아래 필터를 추가한다.

```logql
| price_refresh_result!~"UPDATED|UNCHANGED|RESTORED"
```

`NO_MATCH`, `NO_SEARCH_RESULTS`, `IDENTIFIER_UNAVAILABLE`, `INVALID_PRICE`, `CONFLICTING_PRICES`,
`API_FAILURE`, `DB_FAILURE`, `SKIPPED`, `NOT_ATTEMPTED`가 남는다.
`NO_MATCH` 등은 INFO로 남기므로 **ERROR 등급만 필터링하면 안 된다**.
NO_MATCH는 반환된 후보의 ID를 모두 읽었지만 저장 ID가 없다는 뜻이며 쿠팡 판매 종료를 입증하는 값은 아니다.

| 결과 | 확인할 부분 |
|---|---|
| `NO_MATCH` | 저장 `offer.vendor_item_id`와 후보 ID 비교, 저장된 판매처 링크 확인 |
| `NO_SEARCH_RESULTS` | 해당 검색어의 응답이 비어 있음. 상품의 판매처 페이지 확인 |
| `IDENTIFIER_UNAVAILABLE` | URL 해석 실패 수·ID 누락 수 확인 |
| `INVALID_PRICE` / `CONFLICTING_PRICES` | 가격 누락·0 이하·서로 다른 가격 여부 |
| `API_FAILURE` | `error.code` 확인. 인증·호출 제한 등을 구분 |
| `DB_FAILURE` | `error.type`, 같은 시각의 DB 상태 확인 |
| `SKIPPED` | 조회 중 DB 가격·상태·연결이 바뀌어 저장을 보류했는지 확인 |
| `NOT_ATTEMPTED` | 같은 run ID의 앞선 인증 실패·호출 제한·중단 원인 확인 |

로그를 펼쳐 링크를 확인한다. 숨김 제품의 Devica 상세는 404이므로 저장된 판매처 링크를 사용한다.
원본 후보 ID 배열까지 보려면 Explore에서 `line_format`을 제거하고
`| price_refresh_run_id="실행 ID" | product_code="제품 코드"`로 좁혀 원본 JSON을 읽는다.

## 4. 날짜를 바꿔 확인하기

- 우측 상단 시간 선택기에서 특정 날짜의 **00:00:00 ~ 다음 날 00:00:00**으로 범위를 잡는다.
  한국 날짜를 기준으로 하려면 Time zone이 Asia/Seoul인지 확인한다.
- 예: 10월 26일 범위에서 카드는 그날의 마지막 종료 결과, 로그들은 그 기간의 실행 기록이다.
- Last 7 days로 바꾸면 로그는 7일 기록, 카드는 그 7일 중 가장 최근 종료 결과다. 7일 누적 성공 수가 아니다.
- 1,000줄 한도에 도달하면 날짜·판매처·실행 ID로 좁혀 확인한다. 조회 가능한 과거 기간은 Loki 보존 설정에 따른다.
- 선택 기간에 데이터가 없으면 `0 / 20`을 만들어 표시하지 않는다. 실행 누락·수집 문제·조회 기간을 구분해서 확인한다.

## 판매처가 늘어날 때

새 판매처 작업에서도 동일한 `price_refresh.*` 집계·결과 필드와 링크를 남기고,
`price_refresh.seller`에 그 판매처 값을 넣는다. `offer_seller` 변수에 그 값을 추가하면 반복 카드가 나뉘고,
All 선택 시 로그 패널에서 여러 판매처를 함께 볼 수 있다.
현재 코드가 다른 판매처 API를 실행하는 것은 아니며, 각 연동은 별도로 구현해야 한다.

## 검증 범위와 참고 문서

앱의 집계·로그 필드는 모의 API 응답 테스트로 검증한다. JSON 구문과 쿼리·필드 구성은 로컬에서 확인하지만,
실제 Cloud 데이터 조회·화면 렌더링은 가져온 뒤 확인해야 한다.

- [Grafana 대시보드 가져오기](https://grafana.com/docs/grafana/latest/visualizations/dashboards/build-dashboards/import-dashboards/)
- [Loki query editor: Range, line limit, direction](https://grafana.com/docs/grafana/latest/datasources/loki/query-editor/)
- [Stat: 문자열 값과 Fields 선택](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/visualizations/stat/)
- [Logs 시각화](https://grafana.com/docs/grafana/latest/visualizations/panels-visualizations/visualizations/logs/)
- [대시보드 시간대 설정](https://grafana.com/docs/grafana/latest/visualizations/dashboards/build-dashboards/modify-dashboard-settings/)
