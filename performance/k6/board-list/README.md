# 질문 게시판 목록 조회 테스트

`GET /api/product-categories/LAPTOP/usage-purposes/BACKEND_DEVELOPMENT/posts`

공통 모듈의 요청 옵션·SLO·워밍업·결과 유효성을 사용한다. 기준과 반복 측정 방법은 [성능 테스트 안내](../../README.md)를 따른다.

## 측정 대상과 현재 페이지 처리

측정 대상은 **질문 게시판 목록 조회 API**다. `page`는 그 API에 어떤 범위의 글을 요청할지 정하는 입력이다.

현재 코드에서는 서버가 페이지를 나누어 반환한다.

- [frontend/src/board.ts](../../../frontend/src/board.ts)의 `fetchBoardPosts`가 `page`와 `size=20`을 보낸다.
- [BoardListView.tsx](../../../frontend/src/BoardListView.tsx)는 페이지가 바뀔 때 다시 요청한다. [useBoardRequest.ts](../../../frontend/src/useBoardRequest.ts)는 목록 화면에 다시 방문해도 요청한다.
- [BoardPostService.java](../../../src/main/java/com/wrb/devica/board/service/BoardPostService.java)는 `PageRequest.of(page, size)`로 조회 범위를 전달하고 `Slice`를 반환한다.

따라서 `page=0&size=20`은 최신 20건, `page=1&size=20`은 다음 20건을 받는 요청이다. 프론트는 서버가 내려준 `content`를 표시한다. 페이지 번호는 0부터 시작하며 화면에는 1페이지부터 표시된다.

## 방문 흐름과 예상 트래픽

사용자 한 명이 다음 흐름으로 이용한다고 가정한다. 실제 관측한 이용 비율은 아니다.

| 사용자 행동 | 발생하는 요청 | 목록 API 호출 수 |
| --- | --- | --- |
| 제품·사용 목적을 선택해 질문 목록에 진입 | 목록 `page=0&size=20` | 1회 |
| 글을 선택해 읽기 | 글 상세·댓글 조회 API | 0회 |
| 상세 화면의 `목록으로` 버튼으로 복귀 | 목록 `page=0&size=20` | 1회 |
| `다음` 버튼으로 2페이지 탐색 | 목록 `page=1&size=20` | 1회 |

목록 요청은 합계 3회다. 게시판 진입 전 분류 선택이나 글 상세·댓글 API의 요청은 목록 API RPS에 합산하지 않는다.

- 홍보 직후 10분간 260명 유입, 방문당 목록 요청 3회를 가정한다. 호출 수는 실측값이 아니다.
- 예상 평균 요청률은 `260 × 3 ÷ 600 = 1.3 RPS`다. 여유를 두어 **2 RPS**로 측정한다. 측정 10분에 약 1,200건으로 예상 780건보다 약 54% 많다.
- 맥쓰사 게시글을 20년간 약 160만 건으로 본 가정을 10년으로 환산하면 약 80만 건이다. 여유를 더해 우리 게시판의 10년 누적 규모를 **100만 건**으로 가정하고 조회 비용을 측정한다. 이는 홍보 직후 260명 유입과 별개인 데이터 규모 가정이다.

## 부하 구성

기본 테스트는 위 흐름에서 발생하는 **목록 요청의 비율**을 재현한다. `traffic.js`에서 `page=0 → 0 → 1`을 순환 선택하고, 반복 한 번마다 선택한 목록 요청 **한 건**을 보낸다. 여러 방문자에게서 발생할 목록 요청을 합쳐 **전체 2 RPS**를 유지한다. page=0과 page=1에 각각 2 RPS를 보내는 것이 아니다.

이는 목록 API의 병목을 같은 부하에서 비교하는 테스트다. 한 방문자의 글 읽는 시간이나 상세·댓글 요청까지 실행하는 전체 방문 세션 테스트는 포함하지 않는다. 방문 흐름 전체를 재현하려면 세션 시작률과 API별 요청률을 구분해야 한다. 예를 들어 초당 2세션을 시작하면 세션당 목록 3건 기준 최대 6 RPS가 되어 이번 목표 부하와 달라진다.

| 테스트 | 요청 조건 | 목적 |
| --- | --- | --- |
| **기본: 목록 조회** | `POST_PAGE` 생략, `page=0 → 0 → 1`, `size=20` | 가정한 방문 흐름의 목록 요청 비율에서 SLO 확인 |
| 추가 진단: 첫 페이지 고정 | `POST_PAGE=0`, `size=20` | 최신 목록 조회 비용만 분리해 확인 |
| 추가 진단: 깊은 페이지 고정 | `POST_PAGE=1000`, `size=20` | 20,000건을 건너뛰는 OFFSET 조회 비용 확인 |

각 테스트는 **따로 실행**한다. 깊은 페이지는 일반 방문 흐름에 포함하지 않고 필요할 때만 실행하는 진단 조건이다.

응답 속도와 관계없이 초당 2번 반복을 시작하는 `constant-arrival-rate`를 사용한다. 앞 요청이 실패해도 예정된 부하는 유지한다. ([k6 실행 방식](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/))

| 항목 | 설정 |
| --- | --- |
| 대상 게시판 | `LAPTOP / BACKEND_DEVELOPMENT` |
| 정렬 / 크기 | 서버 기본 정렬 `created_at DESC, id DESC` / 20건 |
| 부하 | 워밍업과 측정 모두 2 RPS, 시나리오별 20 VU 확보 |
| 시간 | 공통 모듈의 워밍업 1분 → 측정 10분, 진행 중인 요청은 최대 10초 더 기다림 |
| 입력 | `BASE_URL`(기본 `http://localhost:8080`), `POST_PAGE`(생략하면 기본 목록 조회, 지정하면 고정 페이지 진단) |
| 응답 검사 | 200이면 목록 1~20건, 요청한 page·size, hasNext 및 각 글의 id·title·createdAt·commentCount 형식 확인 |
| 태그 | `page`, `size`, `action`(`enter`·`return`·`next`·`fixed`), 요청 이름 `GET board posts` |

`RPS`, `DURATION`, `VUS`, `BOARD_TEST`는 새 스크립트의 입력이 아니다. 부하는 `load.js`, 측정 시간과 SLO는 공통 모듈에서 관리한다.

## 측정 전 데이터 확인

100만 건을 다시 적재하지 않는다. 개발 DB에서 아래 결과를 기록하고, 개선 전후 같은 데이터인지 확인한다. 대상 게시판 건수·ID 범위·작성 시각 범위와 댓글 수를 기록하고, 측정 사이에는 글·댓글을 추가하거나 수정하지 않는다.

```sql
SET @board_purpose_id = (
    SELECT up.id
    FROM usage_purpose up
    JOIN product_category pc ON pc.id = up.product_category_id
    WHERE pc.code = 'LAPTOP' AND up.code = 'BACKEND_DEVELOPMENT'
);

SELECT (SELECT COUNT(*) FROM board_post) AS total_posts,
       COUNT(*) AS target_posts,
       MIN(id) AS min_id, MAX(id) AS max_id,
       MIN(created_at) AS first_created_at, MAX(created_at) AS last_created_at
FROM board_post
WHERE usage_purpose_id = @board_purpose_id;

SELECT COUNT(*) AS target_comments
FROM board_comment c
JOIN board_post p ON p.id = c.board_post_id
WHERE p.usage_purpose_id = @board_purpose_id;
```

- `target_posts`는 기존 100만 건 적재 후 실제 건수를 기준으로 한다. 다른 게시판을 합친 `total_posts`만으로 판단하지 않는다.
- 기본 테스트의 두 페이지를 20건씩 채우려면 최소 40건, 깊은 페이지 진단은 최소 20,020건이 필요하다. 이것은 요청 조건의 최소치이며, 전후 비교용 데이터는 기존 100만 건을 유지한다. 빈 목록은 이번 테스트의 유효성 검사에서 실패한다.
- 적재 SQL은 댓글을 만들지 않는다. 댓글이 거의 없는 데이터라면 댓글이 많은 상황의 성능까지 검증한 것으로 해석하지 않는다.

## 실행

레포 루트에서 실행한다. 개발 서버 사용 시간은 팀에 공유하고 다른 부하 테스트·배포와 겹치지 않게 한다.

```bash
# 스모크: 기본 목록 요청 비율로 4초마다 1건, 1분
k6 run -e BASE_URL=http://dev.devica.co.kr \
  performance/k6/board-list/smoke.js

# 기본 목록 조회: 총 2 RPS, 워밍업 1분 → 측정 10분
k6 run -e BASE_URL=http://dev.devica.co.kr \
  --summary-export=performance/results/board-list-browse-before-1.json \
  performance/k6/board-list/load.js

# 추가 진단: 첫 페이지 고정
k6 run -e BASE_URL=http://dev.devica.co.kr -e POST_PAGE=0 \
  --summary-export=performance/results/board-list-first-before-1.json \
  performance/k6/board-list/load.js

# 추가 진단: 깊은 페이지 고정. 앞 테스트의 서버 작업이 정리된 뒤 실행
k6 run -e BASE_URL=http://dev.devica.co.kr -e POST_PAGE=1000 \
  --summary-export=performance/results/board-list-deep-before-1.json \
  performance/k6/board-list/load.js
```

스모크는 공통 유효성 기준만 적용한다. 종료 코드가 0이어도 5xx·타임아웃이 있으면 정상 응답을 확인한 것은 아니다. 오류와 `board_requests_succeeded`도 확인한다. 유효한 요청에서 발생하는 5xx·타임아웃은 부하 테스트에서 개선 전 SLO 실패로 기록할 수 있다.

요약 파일 하나와 k6 종료 화면·Grafana 캡처를 수동으로 기록하면 된다. 파일명에서 `before`/`after`, 회차 `1`/`2`/`3`을 바꿔 보관한다. 원시 지표가 필요할 때만 `--out json=performance/results/board-list-browse-before-1-raw.json`을 추가한다.

## 판정과 기록

측정 구간은 실행 시작 **1분 후부터 11분 후까지**다. 예를 들어 14:30에 시작하면 14:31~14:41이며, 마지막 요청을 기다리는 시간은 별도다. 서버 커밋과 측정 시작·종료 시각을 기록하고 Grafana·RDS도 같은 측정 구간과 시간대(KST 등)로 맞춘다.

[공통 기록 표](../../README.md#기록)의 모든 항목을 같은 측정 구간에서 확인한다. 오류율은 `http_req_failed{scenario:board_list_measure}`, k6 응답 시간은 `http_req_duration{scenario:board_list_measure}`에서 읽는다. 요청 RPS는 `http_reqs{scenario:board_list_measure}`의 count ÷ 600으로 계산한다. 정상 응답 RPS는 `board_requests_succeeded{scenario:board_list_measure}`의 count ÷ 600으로 별도 기록한다.

워밍업을 포함한 전체 합계나 k6 요약의 `rate`를 측정 RPS로 쓰지 않는다. `board_requests_succeeded`의 기준은 값을 요약에 남기는 용도여서 ✓가 SLO 통과를 뜻하지 않는다.

Grafana Explore에서 측정 종료 시각으로 맞추고 아래 쿼리를 Instant로 실행한다. 결과는 나노초이므로 1,000,000으로 나누어 ms로 기록한다. 요청 로그 필드와 한계는 공통 안내를 따른다.

```logql
quantile_over_time(0.95, {env="dev"} |= "요청 처리 완료" | json | http_route="/api/product-categories/{categoryCode}/usage-purposes/{purposeCode}/posts" | unwrap event_duration [10m]) by (env)
```

클라이언트가 5초에 타임아웃돼도 서버 작업은 더 오래 계속될 수 있다. 긴 요청이 많으면 k6의 측정 요청과 로그의 완료 시각 기준 구간이 달라질 수 있으므로, 종료 후에도 남는 200·500 로그와 커넥션 대기를 함께 기록한다. 다른 페이지 테스트는 앞 테스트의 서버 작업이 정리된 뒤 시작한다.

개선 전후에 같은 테스트 조건을 3회씩 측정하고 공통 기록 표를 채운다. 기본 목록 조회와 고정 페이지 진단은 별도 결과로 기록한다. 이전의 첫 페이지 고정 결과를 새 기본 테스트와 직접 비교하지 말고, 전후 비교의 개선 전 값도 같은 조건으로 다시 측정한다.

