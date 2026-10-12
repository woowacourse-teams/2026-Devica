# k6 테스트

공통 기준, 측정 방법, 결과 저장 방법은 [성능 테스트 안내](../README.md)를 본다. 명령은 레포 루트에서 실행한다. 모든 테스트는 `BASE_URL`로 대상 서버를 지정한다(기본값 `http://localhost:8080`).

## 상품 목록 조회 (`product-list/`)

실행 방법과 측정 전 데이터 확인은 [상품 목록 조회 테스트](product-list/README.md)를 본다.

## 상품 상세 조회 (`product-detail/`)

```bash
k6 run performance/k6/product-detail/smoke.js
k6 run -e RPS=10 -e DURATION=30s -e VUS=20 performance/k6/product-detail/load.js
```

`load.js`는 기본적으로 제품 목록 첫 페이지에서 ID를 한 번 가져온다. 전체 노트북을 대상으로 하려면 `PRODUCT_IDS_SOURCE=file`을 주고 `performance/k6/data/product-ids.json`을 준비한다. 파일 모드에서는 목록 API를 호출하지 않고 저장된 ID 중 하나를 매 반복마다 무작위로 골라 상세 API를 호출한다.

### ID 파일 준비

형식은 다음과 같은 양의 정수 ID 배열이다. 별도 생성기 없이 직접 만들 수 있다.

```json
[1, 7, 42]
```

위 숫자는 형식 예시다. 테스트할 서버에 실제로 존재하는 노트북 제품 ID를 넣어야 한다. 자동 증가 ID가 1부터 연속한다고 가정해서 만들지 않는다.

전체 노트북을 대상으로 할 때는 데이터를 적재한 DB에서 아래 SQL을 실행한다. `laptop.id`가 제품 상세 API에 전달할 제품 ID이므로 판매처 테이블을 조인할 필요가 없다.

```sql
SELECT COALESCE(JSON_ARRAYAGG(id), JSON_ARRAY()) AS product_ids
FROM laptop;
```

조회 결과의 **배열 값 전체만** 복사해 `performance/k6/data/product-ids.json`에 저장한다. 컬럼명, 결과 표의 테두리, 배열을 감싸는 따옴표는 넣지 않는다. 결과가 `[]`이면 노트북 데이터가 없는 것이므로 데이터 적재 상태를 확인한다. 파일은 Git에서 제외되며, 데이터셋이나 대상 서버가 바뀌면 다시 만든다. ID 파일을 생성한 서버와 `BASE_URL`이 가리키는 서버는 같아야 한다.

`JSON_ARRAYAGG`는 여러 ID를 JSON 배열 하나로 모은다. 배열 순서는 보장되지 않지만, k6는 ID를 무작위로 선택하므로 정렬은 필요 없다. [MySQL 공식 문서](https://dev.mysql.com/doc/refman/8.4/en/aggregate-functions.html#function_json-arrayagg)

```bash
k6 run -e PRODUCT_IDS_SOURCE=file -e RPS=10 -e DURATION=30s -e VUS=20 performance/k6/product-detail/load.js
```

## 게시글 목록 조회 (`board-list/`)

[게시글 적재 SQL](../data/seed-board-posts.sql)을 실행하면 `LAPTOP / BACKEND_DEVELOPMENT` 게시판에 기본 100만 건을 만든다. 건수와 본문 길이를 조정하는 방법은 [데이터 적재 안내](../data/README.md#게시글-데이터)를 참고한다.

```bash
k6 run performance/k6/board-list/smoke.js
k6 run -e RPS=10 -e DURATION=30s -e VUS=20 -e POST_PAGE=0 performance/k6/board-list/load.js
```

목록 조회이므로 ID 파일이 필요 없다. `POST_PAGE`로 페이지를 지정하며 기본 크기는 20건이다. 지정한 페이지가 데이터 범위 안에 있어야 실제 게시글 조회를 측정할 수 있다. 현재 응답 형식 검사는 빈 목록도 통과한다.

## 혼합 부하 테스트 (`mixed-load.js`)

제품 목록, 제품 상세, 게시글 목록의 요청률을 각각 지정한다. 세 값을 합한 수가 목표 RPS다. `0`을 지정한 요청은 실행하지 않는다.

```bash
k6 run -e PRODUCT_IDS_SOURCE=file \
  -e PRODUCT_LIST_RPS=1 -e PRODUCT_DETAIL_RPS=1 -e BOARD_LIST_RPS=1 \
  -e VUS=5 -e DURATION=30s performance/k6/mixed-load.js
```

`VUS`는 실행하는 시나리오마다 준비할 VU 수다. 제품 목록은 `PRODUCT_PAGE`, 게시글 목록은 `POST_PAGE`로 페이지를 지정한다(기본 0).
