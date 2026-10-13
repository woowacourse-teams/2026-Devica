# 상품 상세 조회 테스트

`GET /api/products/{id}`

공통 기준(SLO, 예상 트래픽)과 측정 방법(워밍업, 반복, 판정, 기록)은 [성능 테스트 안내](../../README.md)를 따른다.

## 시나리오

### 재현할 흐름

### 부하

### 형식 검사와 태그

### 가정

## 측정 전 데이터 확인

### 기준 결과

## 실행

명령은 레포 루트에서 실행한다. 개발 서버에서 실행할 때는 [성능 테스트 안내](../../README.md)의 실행 규칙을 따른다.

```bash
k6 run -e BASE_URL=http://localhost:8080 performance/k6/product-detail/smoke.js
k6 run -e BASE_URL=http://localhost:8080 -e RPS=10 -e DURATION=30s -e VUS=20 performance/k6/product-detail/load.js
```

| 환경 변수 | 기본값 | 내용 |
| --- | --- | --- |
| `RPS` | `10` | 초당 요청 수 |
| `DURATION` | `30s` | 실행 시간 |
| `VUS` | `20` | 미리 준비할 VU 수 |
| `PRODUCT_IDS_SOURCE` | `first-page` | `first-page`면 제품 목록 첫 페이지에서 ID를 한 번 가져온다. `file`이면 목록 API를 호출하지 않고 ID 파일에서 매 반복 무작위로 고른다 |

### ID 파일 준비

`PRODUCT_IDS_SOURCE=file`에는 `performance/k6/data/product-ids.json`이 필요하다. [여러 API를 섞는 테스트](../../README.md#여러-api를-섞는-테스트)도 이 파일을 쓴다. 형식은 양의 정수 ID 배열이다.

```json
[1, 7, 42]
```

위 숫자는 형식 예시다. 테스트할 서버에 실제로 존재하는 노트북 제품 ID를 넣어야 한다. 자동 증가 ID가 1부터 연속한다고 가정해서 만들지 않는다.

데이터를 적재한 DB에서 아래 SQL을 실행한다. `laptop.id`가 상세 API에 전달할 제품 ID이므로 판매처 테이블을 조인할 필요가 없다.

```sql
SELECT COALESCE(JSON_ARRAYAGG(id), JSON_ARRAY()) AS product_ids
FROM laptop;
```

조회 결과의 **배열 값 전체만** 복사해 저장한다. 컬럼명, 결과 표의 테두리, 배열을 감싸는 따옴표는 넣지 않는다. 결과가 `[]`이면 노트북 데이터가 없는 것이므로 데이터 적재 상태를 확인한다. 배열 순서는 보장되지 않지만 ID를 무작위로 고르므로 정렬은 필요 없다.

파일은 Git에서 제외된다. 데이터셋이나 대상 서버가 바뀌면 다시 만들고, 파일을 만든 서버와 `BASE_URL`이 가리키는 서버는 같아야 한다.
