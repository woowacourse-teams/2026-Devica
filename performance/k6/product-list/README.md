# 상품 목록 조회 테스트

`GET /api/product-categories/LAPTOP/products`

공통 기준(SLO, 예상 트래픽)과 측정 방법(워밍업, 반복, 판정, 기록)은 [성능 테스트 안내](../../README.md)를 따른다.

## 시나리오

### 재현할 흐름

사용자는 다음 흐름으로 상품 목록을 본다.

1. 결과 화면에서 표시할 OS를 고른다 (**맥 / 윈도우 / 둘 다**)
2. 그 OS의 권장 사양을 필터로 목록 화면에 들어가고, 필요하면 정렬을 바꾼다
3. 무한 스크롤로 한 페이지씩 내려본다

테스트는 이 흐름을 **세션**(사용자 한 명이 목록 화면 하나를 스크롤하는 과정) 단위로 재현한다. 정렬을 바꾸면 목록을 첫 페이지부터 다시 받으므로 새 세션으로 본다.

### 필터 조합

목록의 필터는 추천 결과에서 만들어지고, 추천이 낼 수 있는 조합은 유한하다. 그래서 대표 조건을 고르지 않고 **조합 전체**를 쓴다.

| OS | CPU 등급 | 메모리 (GB) | 스토리지 (GB) | 조합 수 |
| --- | --- | --- | --- | --- |
| Mac | `BASIC` | 16 / 24 / 32 | 256 / 512 / 1024 | 9 |
| Mac | `PRO` | 24 / 48 | 256 / 512 / 1024 | 6 |
| Windows | `P_HS` / `H` / `HX` | 16 / 24 / 32 / 48 | 256 / 512 / 1024 | 36 |

- 조합은 `LaptopRecommender`의 점수 구간과 Mac 메모리 구성을 옮겨 적은 것이다. 추천이 낼 수 있는 값이 바뀌면 `sessions.js`와 아래 데이터 확인 쿼리를 함께 고친다.
- 키워드·브랜드·가격 필터는 추천에서 만들어지지 않으므로 제외한다.

### 세션 구성

데이터가 없는 사용자 행동은 균등으로 가정한다.

| 항목 | 내용 |
| --- | --- |
| 화면 유형 | 맥 : 윈도우 : 둘 다 = 1 : 1 : 1 |
| 필터 조합 | 해당 OS의 조합 중 균등. 둘 다 화면은 OS별로 따로 고른다 |
| 정렬 | 그 화면에서 고를 수 있는 정렬 중 균등 |
| 페이지 | `size=12`, `page=0 → 1 → 2`를 앞 응답을 받은 뒤 차례로 |
| 다음 페이지 | `hasNext: false`이거나 응답이 200이 아니면 그 목록은 다음 페이지를 요청하지 않는다 |
| 스크롤 간격 | 1~5초 무작위 |
| 재현성 | 시드를 고정해 세션 조건 목록을 만들고, 모든 회차에서 같은 순서로 쓴다 |

화면 유형에 따라 요청 수와 정렬 선택지가 다르다.

| 화면 | 페이지당 요청 | 정렬 선택지 |
| --- | --- | --- |
| 맥 / 윈도우 | 1건 | 추천순, 가격 낮은순, 가격 높은순 |
| 둘 다 | 2건 동시 (OS별) | 가격 낮은순, 가격 높은순 |

둘 다 화면은 목록 API가 OS를 하나만 받기 때문에 OS별로 요청하고 결과를 합친다. 합친 목록은 서버의 추천순을 이어갈 수 없어 추천순을 고를 수 없다.

### 부하

예상 트래픽(홍보 직후 10분간 약 260명 유입)을 세션 단위로 바꾸면 다음과 같다.

| 항목 | 값 | 계산 |
| --- | --- | --- |
| 세션 시작 | 6초에 13개 (초당 약 2.17개) | 260명 × 목록 화면 진입 5번 ÷ 600초 |
| 페이지당 평균 요청 | 약 1.33건 | (1 + 1 + 2) ÷ 3 |
| 평균 RPS | **약 8.7** | 2.17 × 3페이지 × 1.33건 |

- **세션 시작은 일정한 속도로**: 사용자 유입은 서버가 느려도 줄지 않는다.
- **세션 안은 차례로**: 사용자는 앞 페이지를 받아야 다음으로 넘어간다.
- 세션은 몇 초씩 걸리므로, 서버가 느려져도 세션을 버리지 않도록 VU 수를 넉넉히 둔다.

### 형식 검사와 태그

| 항목 | 내용 |
| --- | --- |
| 형식 검사 | 3xx·4xx가 아니다. 200 응답은 `content` 배열, 요청한 `page`·`size`와 일치. `page=0, 1`은 `hasNext: true` |
| 태그 | `screen`, `os`, `cpu_tier`, `memory_gb`, `storage_gb`, `page`, `sort` |

조합마다 걸리는 상품 수가 수십 배 차이 나서, 결과가 기준을 넘으면 태그로 조건별 응답 시간을 나눠 본다.

### 가정

아래 값은 사용자 데이터가 없어 정한 가정이다. 실측 데이터가 생기면 바꾸고, 바꾸면 RPS도 다시 계산한다.

| 가정 | 값 | 실제와 다르면 |
| --- | --- | --- |
| 방문당 목록 화면 진입 | 5번 (정렬 변경 포함) | 세션 시작 속도가 비례해 바뀐다 |
| 화면 유형 비율 | 1 : 1 : 1 | 결과 화면의 기본값이 "둘 다"라, 실제로는 둘 다 화면이 더 많을 수 있다. 그러면 RPS가 커진다 |
| 스크롤 깊이 | 3페이지 | 첫 페이지에서 멈추는 사용자가 많으면 RPS가 작아진다. 부하를 크게 잡는 보수적인 가정이다 |
| 스크롤 간격 | 1~5초 | 평균 RPS는 그대로이고, 요청이 몰리는 정도만 달라진다 |
| `size=12` | 프론트엔드의 무한 스크롤 전환 기준 | 페이지 크기가 바뀌면 데이터 확인 기준(24건)도 바뀐다 |

## 측정 전 데이터 확인

조건에 걸리는 상품 수가 바뀌면 같은 요청이라도 DB가 하는 일이 달라져, 개선 전후를 비교할 수 없다. 그래서 측정 전에 대상 DB에서 아래 쿼리를 실행하고 기준 결과와 비교한다.

조건은 목록 API와 같다. CPU 등급은 그 등급의 최소 점수 이상, 메모리와 스토리지는 이상으로 찾는다. 최소 점수는 `CpuTier.java`에서 옮겨 적었으므로, 점수가 바뀌면 쿼리도 함께 고친다.

```sql
WITH cpu_memory (os, cpu_tier, min_score, memory_gb) AS (
  VALUES
    ROW('MAC', 'BASIC', 20000, 16), ROW('MAC', 'BASIC', 20000, 24), ROW('MAC', 'BASIC', 20000, 32),
    ROW('MAC', 'PRO', 30000, 24), ROW('MAC', 'PRO', 30000, 48)
  UNION ALL
  SELECT 'WINDOWS', t.cpu_tier, t.min_score, m.memory_gb
  FROM (VALUES ROW('P_HS', 20000), ROW('H', 24000), ROW('HX', 35000)) AS t (cpu_tier, min_score)
  CROSS JOIN (VALUES ROW(16), ROW(24), ROW(32), ROW(48)) AS m (memory_gb)
),
combo AS (
  SELECT c.os, c.cpu_tier, c.min_score, c.memory_gb, s.storage_gb
  FROM cpu_memory c
  CROSS JOIN (VALUES ROW(256), ROW(512), ROW(1024)) AS s (storage_gb)
),
matched AS (
  SELECT combo.os, combo.cpu_tier, combo.memory_gb, combo.storage_gb, COUNT(l.id) AS products
  FROM combo
  LEFT JOIN (laptop l JOIN cpu ON cpu.id = l.cpu_id)
    ON l.os = combo.os
    AND cpu.score >= combo.min_score
    AND l.memory_gb >= combo.memory_gb
    AND l.storage_gb >= combo.storage_gb
  GROUP BY combo.os, combo.cpu_tier, combo.memory_gb, combo.storage_gb
)
SELECT COUNT(*) AS combos,
       MIN(products) AS min_products,
       SUM(products) AS total_products,
       SUM(products <= 24) AS combos_without_page_2
FROM matched;
```

### 기준 결과

Flyway 기본 데이터에 [제품 적재 SQL](../../data/seed-products.sql)을 기본값으로 한 번 실행한 상태의 결과다.

| combos | min_products | total_products | combos_without_page_2 |
| --- | --- | --- | --- |
| 51 | 269 | 134,602 | 0 |

| 결과 | 판단 |
| --- | --- |
| 네 값이 모두 기준과 같다 | 측정을 진행한다 |
| `combos_without_page_2`가 0이 아니다 | 그 조합에서 `page=1`의 `hasNext`가 `false`가 되어 형식 검사가 실패하고, 회차가 무효가 된다. 데이터를 확인한다 |
| `min_products`나 `total_products`가 다르다 | 데이터가 바뀐 것이다. 개선 전후 비교에 쓰지 않는다 |

## 실행

명령은 레포 루트에서 실행한다. 개발 서버에서 실행할 때는 [성능 테스트 안내](../../README.md)의 실행 규칙을 따른다.

```bash
# 스모크: 초당 약 0.25 세션(약 1 RPS), 1분
k6 run -e BASE_URL=http://localhost:8080 performance/k6/product-list/smoke.js

# 부하: 워밍업 1분 → 측정 10분. 진행 중인 세션은 끝까지 기다린다
k6 run -e BASE_URL=http://localhost:8080 \
  --summary-export=performance/results/product-list-load-$(date +%Y%m%d-%H%M).json \
  --out json=performance/results/product-list-load-$(date +%Y%m%d-%H%M)-raw.json \
  performance/k6/product-list/load.js
```

- 요약 결과(`--summary-export`)는 판정과 기록에 쓴다.
- 요청별 결과(`--out json`)는 태그로 조건별 응답 시간을 나눠 보거나, 1분 단위 p95 추이로 워밍업 길이를 확인할 때 쓴다.
