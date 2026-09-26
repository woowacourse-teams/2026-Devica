# 리뷰 기록

base: origin/feature/109-product-purpose-select
diff-hash: bd0029c5ede1322cb7a4eccb3fe8042003341a48

| 규칙 ID | 위치 | 지적 | 구분 | 상태 | 비고 |
|---|---|---|---|---|---|
| ASYNC-2 | frontend/src/App.tsx:173 | 탭으로 유형을 바꿔도 목록이 받아 둔 이전 유형의 제품을 비우지 않아, 새 유형을 받는 동안 이전 유형의 제품이 새 결과처럼 보임 | 필수 | 해결 | 목록에 key={유형 코드}를 줘 유형이 바뀌면 새로 그림. 기존 린트 에러가 있는 ProductListView.tsx 는 건드리지 않음 |
