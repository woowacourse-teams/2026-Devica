# PostHog 측정 가이드

운영 프로젝트의 기존 이벤트는 이름과 발생 조건을 유지한다. 랜딩페이지 측정은 별도 이벤트를 추가한다.
기존 `everytime`, `everytime2` 기록을 수정·삭제하거나 유입값을 바꾸지 않는다. 새 이벤트는 배포 이후부터 쌓이며 과거 방문에 소급해서 채우지 않는다.

## 기존 지표

| 이벤트 | 발생 시점 |
|---|---|
| `onboarding_started` | 제품·사용 목적 선택 후 실제 질문을 시작할 때. 랜딩의 시작 버튼 클릭과 다르다. |
| `onboarding_completed` | 마지막 질문을 마치고 결과로 이동할 때 |
| `recommendation_result_viewed` | 제품 추천 결과를 성공적으로 받아 표시될 때 |
| `recommended_products_viewed` | 결과에서 제품 검색으로 이동해 맞춤 목록을 성공적으로 받았을 때 |
| `board_entry_viewed` | 게시판 밖의 화면으로 이동할 때. 기존 헤더 노출 기준을 유지한다. |
| `board_entered` | 게시판 밖에서 게시판 영역으로 진입할 때 |
| `post_viewed`, `question_cta_viewed`, `question_submitted` | 기존 글 조회·작성 버튼 노출·질문 등록 조건 유지 |

## 추가한 이벤트

| 이벤트 | 발생 시점 | 속성 |
|---|---|---|
| `landing_viewed` | 랜딩 소개 제목이 실제로 보일 때 | — |
| `service_entry_viewed` | 같은 방문 세션에서 해당 서비스의 진입 링크가 처음 보일 때 | `destination`, `entry_point` |
| `service_entry_clicked` | 서비스 진입 링크를 클릭할 때 | `destination`, `entry_point` |
| `landing_guide_link_clicked` | 사용법 바로가기 링크 클릭 | `guide` |
| `landing_guide_viewed` | 각 사용법 제목이 보일 때 | `guide` |
| `landing_guide_explored` | 사용법 카드를 처음 옆으로 넘길 때 | `guide` |

- `destination`: `spec_finder`(제품 추천받기), `board`(질문 게시판).
- `entry_point`: `landing_hero`(상단), `landing_guide`(카드 아래), `landing_closing`(하단), `header`(헤더 게시판 링크). 노출에는 **처음 본 위치**, 클릭에는 **실제로 누른 위치**를 기록한다.
- `guide`: `find-specs`(제품 추천받기), `use-board`(게시판), `overview`(사용법 먼저 보기 링크에만 해당).
- `$current_url`, `$pathname`은 이벤트가 발생한 주소다. SDK 로딩 중 이동해도 당시 주소와 시각을 보존한다.
- 노출은 요소의 절반 이상이 보이는 탭에서 300ms 이상 표시될 때 기록한다. 투명한 등장 애니메이션 대기 상태와 화면 밖의 하단 버튼은 세지 않는다.
- `service_entry_viewed`는 **PostHog 세션과 목적지 기준으로 한 번만** 센다. 게시판 버튼 네 곳을 모두 봐도 게시판 노출은 1건이며, 제품 추천받기도 별도로 1건이다. 페이지 이동·재진입·새로고침과 같은 세션의 다른 탭에서도 유지한다. 새 세션이면 다시 센다.
- 브라우저 저장소가 차단된 경우에는 현재 페이지 안에서만 중복을 제거한다. 사람 수는 아래의 고유 사용자 집계로 확인한다.
- 랜딩 소개·사용법 제목 노출과 카드 탐색은 같은 화면에서 한 번만 센다. 다른 페이지에서 돌아오면 다시 측정한다.
- 사용법 제목 노출은 관찰 결과로만 기록한다. 제목을 빠르게 지나쳐 카드만 넘긴 경우 `landing_guide_explored`만 기록하며, 제목 조회를 보충하지 않는다. 따라서 카드 탐색 사용자 모두가 제목 조회 퍼널에 포함되지는 않는다.
- 300ms 전에 눌렀거나 새 세션에서 눌렀다면 노출을 먼저 확인하고 클릭을 기록한다. 이미 해당 세션에서 본 목적지라면 노출을 추가하지 않는다. 클릭은 반복해도 기록한다.
- 일반 클릭·키보드 활성화·수정 키 클릭·가운데 버튼 클릭을 지원한다. 브라우저의 우클릭 메뉴에서 새 탭 열기는 클릭 이벤트로 수집하지 않는다.
- 이미 게시판 안에 있을 때 헤더의 게시판 링크는 새 진입점으로 세지 않는다.

## 추천 결과 직접 수정

제품 추천 결과 화면에서 제품 추천 결과를 고친 뒤 제품을 검색할 때 `recommended_spec_edited`를 보낸다. 속성 이름의 `recommended`는 다른 이벤트와 같이 제품 추천 결과를 뜻한다. 제품 추천 개선 루프가 이 이벤트로 어느 답 조합에서 어느 항목을 어느 값으로 고쳤는지 읽으므로, 이름과 속성을 바꾸면 루프도 함께 고친다(#263).

| 속성 | 값 |
|---|---|
| `category`, `purpose` | 제품 유형과 사용 목적 |
| `os` | 고친 제품 추천 결과의 OS(`MAC`, `WINDOWS`). 화면에 보이는 OS 별 제품 추천 결과 중 고친 것마다 한 건씩 보낸다. |
| `cpu_tier_recommended`, `cpu_tier_edited` | 제품 추천 결과의 CPU 등급 코드와 고친 등급 코드 |
| `memory_gb_recommended`, `memory_gb_edited` | 제품 추천 결과의 메모리와 고친 메모리(GB) |
| `storage_gb_recommended`, `storage_gb_edited` | 제품 추천 결과의 저장 공간과 고친 저장 공간(GB) |

- 고치지 않은 항목의 `_edited`는 비어 있다. 제품 추천 결과와 같은 값으로 고르면 고치지 않은 것으로 본다.
- 답 조합은 `$current_url`의 `answer` 값에 있다. `recommendation_result_viewed`와 같은 방법으로 읽는다. 고친 값은 주소의 `edit`(`OS.항목코드.값`)에도 남는다.
- 고치기만 하고 검색하지 않으면 보내지 않는다.

## PostHog에서 보는 방법

기존 **Devica 핵심 지표**의 이벤트·분모·필터를 새 이벤트로 교체하지 않는다. 새 분석을 추가해서 이전 지표와 나란히 비교한다.
아래 퍼널은 **고유 사용자(Unique users)** 기준, 전환 기간 1일로 만든다. 추세 차트에서 사람 수를 볼 때도 `Total count` 대신 `Unique users`를 선택한다. 같은 사람이 여러 번 클릭하거나 새 세션으로 다시 방문해도 조회 기간 내 같은 브라우저 식별자는 1명으로 집계한다.
제품 추천받기와 게시판을 각각 볼 때는 `destination`으로 필터링한다. 두 서비스를 본 사람을 합쳐 1명으로 볼 때는 목적지 필터 없이 고유 사용자를 조회하며, 두 목적지의 인원수를 더하지 않는다. 로그인 계정으로 식별하지 않으므로 다른 브라우저·기기는 별도 사용자로 잡힐 수 있다.

| 분석 | 단계와 조건 |
|---|---|
| 랜딩에서 제품 추천받기 진입률 | `landing_viewed` → `service_entry_clicked` (`destination=spec_finder`, `entry_point`가 `landing_`으로 시작) |
| 랜딩에서 게시판 진입률 | `landing_viewed` → `service_entry_clicked` (`destination=board`) → `board_entered` |
| 서비스별 진입률 | `service_entry_viewed` → `service_entry_clicked`. 두 단계의 `destination`만 같은 값으로 유지한다. 처음 본 위치와 실제 클릭 위치는 다를 수 있으므로 `entry_point`를 동일 값으로 제한하지 않는다. |
| 위치별 클릭 사용자 수 | `service_entry_clicked`를 `destination`으로 필터링하고 `entry_point`로 분해하여 고유 사용자 수를 본다. 여러 위치를 누른 사람은 각 위치에 포함되므로 위치별 인원수를 더해 전체 인원으로 사용하지 않는다. |
| 제품 추천받기 전체 전환 | `service_entry_clicked` (`destination=spec_finder`) → `onboarding_started` → `onboarding_completed` → `recommendation_result_viewed` → `recommended_products_viewed` |
| 사용법을 본 뒤 진입 | `landing_guide_viewed` → `landing_guide_explored` → `service_entry_clicked` (`entry_point=landing_guide`). 제품 추천받기와 게시판을 각각 해당 `guide`/`destination` 조건으로 나눈다. |

기본 권장 사양 바로 보기처럼 질문을 건너뛰는 경로는 전체 온보딩 퍼널에 포함되지 않는다. 전체 제품 추천 도달률을 볼 때는 `service_entry_clicked` → `recommendation_result_viewed`로 별도 집계한다.
카드를 넘기지 않고 진입하는 방문도 있으므로 카드 탐색 퍼널을 전체 방문자의 필수 경로로 해석하지 않는다.
제품 추천을 시작한 뒤 헤더에서 게시판으로 들어가도 `service_entry_clicked(destination=board, entry_point=header)`와 `board_entered`가 기록된다. 게시판 진입 퍼널에 제품 추천 완료나 특정 클릭 위치를 필수 조건으로 넣지 않는다.
노출은 첫 위치만 남기므로 위치별 노출률·클릭률로 해석하지 않는다. 위치 비교는 실제 클릭 이벤트를 사용한다.

`utm_source` 필터는 각 이벤트에 기록된 유입값을 기준으로 한다. `everytime`과 `everytime2`를 서로 바꾸지 않고 각각 선택한다.
SDK가 준비되기 전에 버튼을 눌러 URL에서 UTM이 사라져도 최초 유입값을 같은 세션의 이벤트에 유지한다.
과거에 기록되지 않은 버튼 위치는 복원할 수 없다. 새 분석의 기간은 이 변경의 운영 배포 시점 이후로 지정한다.
조회 기간·필터를 바꾸거나 새 데이터가 쌓이면 집계 숫자는 달라질 수 있지만, 이것이 과거 이벤트를 수정하는 것은 아니다.

## 검증

- `npm run test:analytics --prefix frontend`: 실제 분석 모듈을 가짜 SDK에 연결해 키 없음, 로딩 실패, 지연 로딩 중 이동, UTM 보존, 내부 사용자 제외, 목적지별 세션 중복 제거, 이후 헤더 게시판 진입, 저장소 차단을 확인한다. PostHog로 이벤트를 보내지 않는다.
- 같은 명령으로 Vitest·React Testing Library 화면 테스트도 실행한다. 실제 훅·컴포넌트를 렌더링하고 교차 관찰 입력과 시계를 제어해 50%·연속 300ms·활성 탭·등장 전 숨김·정리 조건, 카드 탐색과 제목 조회의 독립성, 일반·수정 키·가운데 버튼 클릭 및 헤더 이동을 검증한다. 실제 브라우저 레이아웃을 재현하는 E2E 테스트는 아니다.
- CI의 `frontend` 작업은 의존성 설치 후 `npm run test:analytics`와 빌드를 실행한다. 분석 모듈 테스트 또는 화면 측정 테스트가 실패하면 해당 CI 작업도 실패한다.
- 개발·미리보기에는 키를 넣지 않는다. 운영만 `VITE_POSTHOG_KEY`가 필요하다.
- `?internal=1`로 접속한 브라우저는 이후에도 수집이 꺼져 있으므로, 그 브라우저의 방문을 운영 지표 검증에 사용하지 않는다.
- 배포 후 실제 이벤트의 `entry_point`, `destination`, `utm_source`를 확인한다. 같은 세션에서 여러 위치를 봐도 목적지별 노출이 1건인지, 제품 추천받기 이후 헤더에서 게시판에 들어가도 클릭과 진입이 기록되는지 확인한다.

집계 기준 참고: [PostHog Trends](https://posthog.com/docs/product-analytics/trends/overview), [Funnels](https://posthog.com/docs/product-analytics/funnels).
