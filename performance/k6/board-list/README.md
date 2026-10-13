# 게시글 목록 조회 테스트

`GET /api/product-categories/LAPTOP/usage-purposes/BACKEND_DEVELOPMENT/posts`

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
k6 run -e BASE_URL=http://localhost:8080 performance/k6/board-list/smoke.js
k6 run -e BASE_URL=http://localhost:8080 -e RPS=10 -e DURATION=30s -e VUS=20 -e POST_PAGE=0 performance/k6/board-list/load.js
```

| 환경 변수 | 기본값 | 내용 |
| --- | --- | --- |
| `RPS` | `10` | 초당 요청 수 |
| `DURATION` | `30s` | 실행 시간 |
| `VUS` | `20` | 미리 준비할 VU 수 |
| `POST_PAGE` | `0` | 조회할 페이지. 크기는 20건이다 |

지정한 페이지가 데이터 범위 안에 있어야 실제 게시글 조회를 측정할 수 있다. 응답 형식 검사는 빈 목록도 통과한다. 데이터는 [게시글 적재 SQL](../../data/seed-board-posts.sql)로 만든다. 건수와 본문 길이는 [데이터 적재 안내](../../data/README.md#게시글-데이터)를 본다.
