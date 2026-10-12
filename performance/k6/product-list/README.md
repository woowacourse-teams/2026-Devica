# 상품 목록 조회 테스트

`GET /api/product-categories/LAPTOP/products`

공통 기준과 측정 방법은 [성능 테스트 안내](../../README.md)를 본다.

## 시나리오

사용자가 목록을 보는 흐름을 **세션**(사용자 한 명이 목록 화면 하나를 스크롤하는 과정) 단위로 재현한다.

| 항목 | 내용 |
| --- | --- |
| 화면 유형 | 맥 : 윈도우 : 둘 다 = 1 : 1 : 1 (가정) |
| 필터 조합 | 추천 알고리즘이 낼 수 있는 조합(Mac 15개, Windows 36개) 중 해당 OS에서 균등. 둘 다 화면은 OS별로 따로 고른다 |
| 정렬 | 맥·윈도우 화면은 추천순·가격 낮은순·가격 높은순, 둘 다 화면은 가격 낮은순·가격 높은순 중 균등 |
| 페이지 | `size=12`, `page=0 → 1 → 2`를 앞 응답을 받은 뒤 차례로. 둘 다 화면은 페이지마다 OS별 2건을 동시에 |
| 다음 페이지 | `hasNext: false`이거나 응답이 200이 아니면 그 목록은 다음 페이지를 요청하지 않는다 |
| 스크롤 간격 | 1~5초 무작위 |
| 세션 시작 | 6초에 13개(초당 약 2.17개)를 일정한 속도로. 평균 약 9 RPS |
| 재현성 | 시드를 고정해 세션 조건 목록을 만들고, 모든 회차에서 같은 순서로 쓴다 |
| 형식 검사 | 3xx·4xx가 아니다. 200 응답은 `content` 배열, 요청한 `page`·`size`와 일치. `page=0, 1`은 `hasNext: true` |
| 태그 | `screen`, `os`, `cpu_tier`, `memory_gb`, `storage_gb`, `page`, `sort` |

필터 조합은 `LaptopBackendAlgorithm`의 결과 범위를 옮겨 적은 것이다. 알고리즘이 낼 수 있는 CPU 등급·메모리·스토리지가 바뀌면 `sessions.js`와 아래 데이터 확인 쿼리를 함께 고친다. 페이지 크기 `size=12`는 프론트엔드의 무한 스크롤 전환을 기준으로 한다.

## 측정 전 데이터 확인

`page=0, 1`에서 `hasNext: true`가 나오려면 모든 필터 조합의 결과가 24건보다 많아야 한다. 측정 전에 대상 DB에서 아래 쿼리를 실행하고 기준 결과와 비교한다. 조건은 목록 API와 같다. CPU 등급은 그 등급의 최소 점수 이상, 메모리와 스토리지는 이상으로 찾는다.

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
       SUM(products <= 24) AS combos_without_page_2
FROM matched;
```

CPU 등급의 최소 점수는 `CpuTier.java`에서 옮겨 적었다. 점수가 바뀌면 쿼리도 함께 고친다.

### 기준 결과

Flyway 기본 데이터에 [제품 적재 SQL](../../data/seed-products.sql)을 기본값으로 한 번 실행한 상태의 결과다.

| combos | min_products | combos_without_page_2 |
| --- | --- | --- |
| 51 | 269 | 0 |

`combos_without_page_2`가 0이 아니면 그 조합에서 형식 검사가 실패해 회차가 무효가 된다. `min_products`가 기준과 다르면 데이터가 바뀐 것이므로, 개선 전후 비교에 쓰지 않는다.

## 실행

명령은 레포 루트에서 실행한다.

```bash
# 스모크: 초당 약 0.25 세션(약 1 RPS), 1분
k6 run -e BASE_URL=http://localhost:8080 performance/k6/product-list/smoke.js

# 부하: 워밍업 1분 → 측정 10분. 진행 중인 세션은 끝까지 기다린다
k6 run -e BASE_URL=http://localhost:8080 \
  --summary-export=performance/results/product-list-load-$(date +%Y%m%d-%H%M).json \
  performance/k6/product-list/load.js
```

조건별 결과는 결과 출력(`--out json=...`)을 태그로 묶어 본다.
