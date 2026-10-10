# 제품 상세 부하 테스트의 ID 준비

파일 모드의 상세 조회 테스트에는 `performance/k6/data/product-ids.json`이 필요하다. 별도 생성기 없이 직접 만들 수 있으며, 형식은 다음과 같은 양의 정수 ID 배열이다.

```json
[1, 7, 42]
```

위 숫자는 형식 예시다. 테스트할 서버에 실제로 존재하는 노트북 제품 ID를 넣어야 한다. 자동 증가 ID가 1부터 연속한다고 가정해서 만들지 않는다.

전체 노트북을 대상으로 할 때는 데이터를 적재한 DB에서 아래 SQL을 실행한다. `laptop.id`가 제품 상세 API에 전달할 제품 ID이므로 판매처 테이블을 조인할 필요가 없다.

```sql
SELECT COALESCE(JSON_ARRAYAGG(id), JSON_ARRAY()) AS product_ids
FROM laptop;
```

조회 결과의 **배열 값 전체만** 복사해 `performance/k6/data/product-ids.json`에 저장한다. 컬럼명, 결과 표의 테두리, 배열을 감싸는 따옴표는 넣지 않는다. 결과가 `[]`이면 노트북 데이터가 없는 것이므로 데이터 적재 상태를 확인한다. 파일은 Git에서 제외되며, 데이터셋이나 대상 서버가 바뀌면 다시 만든다.

`JSON_ARRAYAGG`는 여러 ID를 JSON 배열 하나로 모은다. 배열 순서는 보장되지 않지만, k6는 ID를 무작위로 선택하므로 정렬은 필요 없다. [MySQL 공식 문서](https://dev.mysql.com/doc/refman/8.4/en/aggregate-functions.html#function_json-arrayagg)

```bash
k6 run -e BASE_URL=http://localhost:8080 -e PRODUCT_IDS_SOURCE=file -e RPS=10 -e DURATION=30s -e VUS=20 performance/k6/detail-load.js
```

`PRODUCT_IDS_SOURCE=file`을 생략하면 기존처럼 제품 목록 첫 페이지에서 ID를 한 번 가져온다. 파일 모드에서는 목록 API를 호출하지 않고 저장된 ID 중 하나를 매 반복마다 무작위로 골라 상세 API를 호출한다. ID 파일을 생성한 서버와 `BASE_URL`이 가리키는 서버는 같아야 한다.

## 게시글 목록 데이터 준비

[게시글 적재 SQL](../data/seed-board-posts.sql)을 실행하면 `LAPTOP / BACKEND_DEVELOPMENT` 게시판에 기본 100만 건을 만든다. 건수와 본문 길이를 조정하는 방법은 [데이터 적재 안내](../data/README.md#게시글-데이터)를 참고한다.

`board-load.js`와 `mixed-load.js`의 게시글 시나리오는 목록 조회이므로 ID 파일이 필요 없다. `POST_PAGE`로 페이지를 지정하며 기본 크기는 20건이다. 지정한 페이지가 데이터 범위 안에 있어야 실제 게시글 조회를 측정할 수 있다. 현재 응답 형식 검사는 빈 목록도 통과한다.

## 혼합 부하 테스트

제품 목록, 제품 상세, 게시글 목록의 요청률을 각각 지정한다. 세 값을 합한 수가 목표 RPS다. `0`을 지정한 요청은 실행하지 않는다.

```bash
k6 run -e BASE_URL=http://localhost:8080 -e PRODUCT_IDS_SOURCE=file \
  -e PRODUCT_LIST_RPS=1 -e PRODUCT_DETAIL_RPS=1 -e BOARD_LIST_RPS=1 \
  -e VUS=5 -e DURATION=30s performance/k6/mixed-load.js
```

`VUS`는 실행하는 시나리오마다 준비할 VU 수다. 게시글 목록은 기본적으로 0페이지를 조회하며, `POST_PAGE`로 다른 페이지를 지정할 수 있다.
