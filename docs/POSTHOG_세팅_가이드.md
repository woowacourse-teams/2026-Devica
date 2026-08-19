# PostHog 세팅 가이드

> **[0장](#0-무엇을-재고-어떻게-재는가)에서 전체 그림을 보고, [1장](#1-계정과-프로젝트-생성)~[4장](#4-수집-확인)을 따라가면 세팅이 끝난다.** 여기까지 20~30분이다.
> [5장](#5-퍼널-만들기)·[6장](#6-세션-리플레이)은 쌓인 데이터를 읽는 방법이고, [부록](#부록-운영-참고)은 운영 중에 찾아 읽는 참고 자료다. 처음부터 끝까지 읽을 문서가 아니다.
> **도구를 왜 PostHog로 골랐는지는 [ADR 0013](adr/0013-제품-지표-수집-도구.md)에 있다.**

## 0. 무엇을 재고, 어떻게 재는가

MVP의 핵심 유스케이스가 실제로 동작하는지 확인하기 위해 PostHog Cloud를 연결한다. 확인하려는 것은 두 가지다.

| 지표 | 묻는 것 | 계산식 | 목표 |
|---|---|---|---|
| 지표 1 | 추천 플로우를 끝까지 완주하는가 | 권장 사양 확정 ÷ 플로우 시작 | 60% 이상 |
| 지표 2 | 확정한 사양으로 **제품을 실제로 살펴보는가** | 제품 상세 열람 ÷ 권장 사양 확정 | 50% 이상 |

### 이벤트는 기존 것을 쓴다 — 딱 하나만 새로 만든다

`probe-app.js`의 `recordEvent()`가 이미 12종의 이벤트를 `POST /api/probe/events`로 보내 DB에 적재하고 있다. 두 지표에 필요한 네 단계 중 셋이 **그 안에 있다.**

| 순서 | 이벤트 | 발생 지점 | 의미 | 보내는 곳 |
|---|---|---|---|---|
| ① | `RECOMMENDATION_STARTED` | `startRecommendation()` | 플로우 시작 | DB + PostHog |
| ② | `RECOMMENDATION_COMPLETED` | 결과 화면 진입 | 권장 사양 노출 | DB + PostHog |
| ③ | `PRODUCT_LIST_VIEWED` | `showProducts()` | 권장 사양 확정 · 목록 도착 | DB + PostHog |
| ④ | `PRODUCT_DETAIL_VIEWED` | `showProductDetail()` | **제품 상세 열람** | **PostHog만** |

**지표 1 = ③ ÷ ①**, **지표 2 = ④ ÷ ③.** 한 줄로 이어지는 흐름이라 [5장](#5-퍼널-만들기)에서 퍼널 하나로 두 숫자를 모두 읽는다.

④만 새로 만든다. **상세 화면 진입을 기록하는 이벤트가 원래 없었기 때문이다.** 이름이 비슷한 기존 `PRODUCT_CLICKED`는 상세 화면 안의 **구매 링크 클릭**이라 지표 2가 묻는 "리스트를 살펴봤는가"보다 훨씬 뒤의 행동이다. PostHog에만 보내는 이유는 [부록](#product_detail_viewed만-posthog-전용인-이유)에 있다.

나머지는 그대로 둔다. 이미 있는 행동에 PostHog 전용 이름을 새로 붙이면 같은 행동이 두 이름으로 갈라진다.

### 알고 시작해야 할 것 — Devica는 단일 페이지 앱이다

PostHog 공식 문서와 타임리프 예제 대부분은 **페이지 이동마다 브라우저가 전체를 새로 로드하는** 다중 페이지 앱을 전제한다. Devica는 템플릿이 `index.html` 하나뿐이고, 화면 5개를 `is-hidden` 클래스로 토글하며, **URL이 변하지 않는다.**

그래서 이 문서는 흔한 예제와 세 군데가 다르다.

- 공통 fragment를 만들지 않는다 (상속시킬 템플릿이 없다)
- URL 쿼리 파라미터로 이벤트를 분기하지 않는다 (URL이 안 바뀐다)
- `$pageview`는 세션당 1건뿐이고, **화면 전환 신호는 전적으로 위 커스텀 이벤트가 담당한다**

### 전제

PostHog 계정을 만들 수 있고, `templates/pages/probe/index.html`과 `static/js/probe-app.js`를 수정·배포할 수 있는 상태.

---

## 1. 계정과 프로젝트 생성

1. [posthog.com](https://posthog.com) 접속 후 회원가입한다.
2. 가입 과정에서 **호스팅 리전**을 고른다. 나중에 바꾸려면 데이터 마이그레이션이 필요하므로 처음에 정한다.
   - **US** — 기본값. 특별한 규정 요건이 없으면 이쪽.
   - **EU** — GDPR 등 유럽 개인정보 규정을 따라야 할 때.
3. 프로젝트는 가입과 동시에 자동 생성된다.

가입이 끝나면 온보딩 화면에 아래 두 값이 나온다. **복사해 둔다.**

| 값 | 형태 | 설명 |
|---|---|---|
| Project API Key | `phc_`로 시작하는 문자열 | 이벤트를 어느 프로젝트로 보낼지 식별 |
| Host URL | `https://us.i.posthog.com` 또는 `https://eu.i.posthog.com` | 위 2번에서 고른 리전에 따라 다름 |

나중에 다시 확인하려면 **Settings → Project**에서 볼 수 있다.

> **Project API Key는 브라우저에 노출되는 것을 전제로 만들어진 공개 키다.** HTML에 그대로 박혀도 보안 문제가 없다.
> 반면 **Personal API Key**는 계정 전체 권한을 가지므로 절대 노출하면 안 된다. 이번 세팅에서는 쓰지 않는다.

---

## 2. 스니펫 삽입

`templates/pages/probe/index.html`의 `<head>` 안, 마지막 `<link>` 뒤에 넣는다.

```html
    <link rel="stylesheet" th:href="@{/css/pages/probe/index.css(v='...')}">

    <script>
      /* PostHog가 제공하는 스니펫 본문 */
      !function(t,e){var o,n,p,r;e.__SV||(window.posthog=e,e._i=[],e.init=function(i,s,a){
        /* ...생략... */
      },e.__SV=1)}(document,window.posthog||[]);

      posthog.init('phc_여기에_복사한_키', {
        api_host: 'https://us.i.posthog.com',
        defaults: '2026-05-30'
      });
    </script>
</head>
```

**스니펫 본문**은 PostHog 대시보드의 **Settings → Project → Web snippet**(또는 온보딩 화면의 HTML 탭)에서 `<script>` 전체를 복사한다. 복사한 뒤 **마지막 줄의 `posthog.init(...)`를 위 형태로 바꾼다.**

### `defaults`를 반드시 넣는다

`defaults`는 **설정 스냅샷의 날짜**다. 생략하면 PostHog가 레거시 기본값으로 초기화된다. 인터넷의 오래된 예제 상당수가 `posthog.init(KEY, { api_host })`까지만 적고 있으니 그대로 따라 쓰지 않는다. 최신 스냅샷 날짜는 [PostHog JS 설치 문서](https://posthog.com/docs/libraries/js)에서 확인한다.

여기까지 하면 **페이지뷰와 세션 리플레이는 자동으로 수집된다.** 키를 설정 파일로 빼지 않는 이유는 [ADR 0013](adr/0013-제품-지표-수집-도구.md)에 있다.

---

## 3. 이벤트 연결

`static/js/probe-app.js`의 `recordEvent()` 맨 앞에 한 블록을 추가한다.

```js
    function recordEvent(eventName, details = {}) {
        // 기존 이벤트를 PostHog로 미러링한다. PostHog 전용 이벤트는 만들지 않는다.
        window.posthog?.capture(eventName, {
            ...details,
            sessionId: state.sessionId,
            questionSetVersion: config.version
        });

        const request = {
            // ...기존 코드 그대로
```

이것으로 12개 이벤트가 전부 PostHog에 들어간다. 기존 DB 적재 경로는 그대로 남는다.

- **순서 문제 없음.** 스니펫의 스텁이 `capture` 호출을 큐에 쌓았다가 `array.js` 로드 후 전송한다. `probe-app.js`가 `defer`여도 안전하다.
- **`sessionId`를 속성으로 싣는다.** `experiment_event` 테이블의 `session_id`와 같은 값이므로, DB 쿼리로 찾은 세션을 PostHog에서 그대로 조회할 수 있다. [6장](#6-세션-리플레이)에서 쓴다.
- **`window.posthog?.`로 접근한다.** 광고 차단기가 스니펫을 막아도 추천 플로우가 멈추지 않는다.

### 상세 열람 이벤트 추가 (④)

같은 파일의 `showProductDetail()`에 한 블록을 더 넣는다.

```js
    function showProductDetail(productId) {
        const product = productView.findProduct(state.matchedProducts, productId);
        if (!product) {
            return;
        }
        state.selectedProductId = product.id;
        // 상세 진입은 experiment_event ENUM에 없어 PostHog에만 보낸다.
        window.posthog?.capture("PRODUCT_DETAIL_VIEWED", {
            optionId: product.id,
            sessionId: state.sessionId,
            questionSetVersion: config.version
        });
        // ...기존 코드 그대로
```

`recordEvent()`를 쓰지 않고 `posthog.capture()`를 직접 부르는 이유는 [부록](#product_detail_viewed만-posthog-전용인-이유)에 있다.

### 지금 하지 않는 것

- 이벤트 속성 추가 — 필요해지면 `details`에 얹으면 되고, 지금은 지표 계산에 쓰이지 않는다.
- `posthog.identify()` — 로그인 없는 MVP는 익명 ID로 충분하다.

---

## 4. 수집 확인

배포하기 전에 로컬에서 먼저 확인한다.

1. 앱을 실행하고 **플로우를 끝까지 한 번 통과**한다. 시작 → 질문 응답 → 결과 확인 → 제품 보기 → **제품 하나의 «상세 보기»**까지 눌러야 4단계가 모두 발생한다.
2. PostHog 왼쪽 메뉴에서 **Activity**를 연다. 30초마다 자동 갱신된다.
3. `RECOMMENDATION_STARTED` · `RECOMMENDATION_COMPLETED` · `PRODUCT_LIST_VIEWED` · `PRODUCT_DETAIL_VIEWED` 네 개가 모두 올라오는지 확인한다.

### 이벤트가 보이지 않을 때

| 증상 | 원인 | 해결 |
|---|---|---|
| 아무 이벤트도 안 뜸 | 광고 차단기가 요청을 막음 | 시크릿 창이나 확장 프로그램을 끈 상태로 재시도 |
| 아무 이벤트도 안 뜸 | 스니펫이 렌더링되지 않음 | 페이지 소스 보기로 `posthog.init`이 실제로 들어갔는지 확인 |
| `$pageview`만 뜨고 커스텀 이벤트가 없음 | JS 수정이 브라우저에 반영 안 됨 | `index.html`의 `?v=` 캐시 버스터 값을 올려 JS를 다시 받게 한다 |
| 이벤트는 뜨는데 `sessionId` 속성이 없음 | `capture()` 두 번째 인자 누락 | 3장 코드와 대조 |
| `$autocapture`가 잔뜩 뜸 | 정상 동작 | 지표 계산과 무관하므로 무시한다. 거슬리면 Settings → Project에서 autocapture를 끈다 |

**확인이 끝나면 배포한다.** `?v=` 캐시 버스터 값도 함께 올린다.

---

## 5. 퍼널 만들기

이벤트가 정상 수집되는 것을 확인한 뒤에 만든다. 한 번도 안 들어온 이벤트는 선택 목록에 뜨지 않는다.

0장의 네 단계가 한 줄로 이어지므로 **퍼널 하나로 두 지표를 모두 읽는다.**

1. 왼쪽 메뉴 **Product analytics → New insight**
2. 상단 탭에서 **Funnel** 선택
3. 단계를 순서대로 추가한다.
   - Step 1: `RECOMMENDATION_STARTED`
   - Step 2: `RECOMMENDATION_COMPLETED`
   - Step 3: `PRODUCT_LIST_VIEWED`
   - Step 4: `PRODUCT_DETAIL_VIEWED`
4. 필터에 `$host` `≠` `localhost:8080`을 추가해 로컬 테스트 데이터를 제외한다.
5. **Save** → `추천_플로우_퍼널`, 이어서 **Add to dashboard**

### 읽는 법

| 지표 | 퍼널에서 볼 곳 | 목표 |
|---|---|---|
| 지표 1 | **Step 1 → Step 3** 누적 전환율 | 60% 이상 |
| 지표 2 | **Step 3 → Step 4** 단계 전환율 | 50% 이상 |

대시보드에서 지표별 타일을 따로 보고 싶어지면 그때 ①~③ / ③~④ 두 인사이트로 쪼갠다. 지금은 하나가 전체 흐름과 이탈 지점을 같이 보여줘서 더 쓸모 있다.

**한계**: 목록을 스크롤하며 훑기만 하고 «상세 보기»를 안 누른 사람은 Step 4에 안 잡힌다. Step 3 → Step 4가 낮게 나오면 숫자를 다시 재기 전에 [6장](#6-세션-리플레이)의 녹화로 "목록에서 뭘 하고 있었는지"를 먼저 본다.

---

## 6. 세션 리플레이

**이번 도구 선택의 핵심 이유가 되는 기능이므로 반드시 켜져 있는지 확인한다.** Devica는 URL이 바뀌지 않아 이탈 지점을 주소로 되짚을 수 없고, 리플레이가 사실상 유일한 단서다.

1. **Settings → Project → Replay**로 이동
2. **Record user sessions** 토글을 켠다. **꺼져 있으면 녹화가 전혀 되지 않는다.**
3. 왼쪽 메뉴 **Session replay**에서 녹화 목록을 볼 수 있다.

### 퍼널과 연결해서 쓰는 법

1. 5장에서 만든 퍼널 그래프에서 **이탈이 발생한 구간을 클릭**한다.
2. 그 구간에서 빠져나간 사용자 목록이 나온다.
3. 개별 사용자 행의 재생 아이콘을 누르면 해당 세션 녹화로 이동한다.

지표가 목표에 미달했을 때는 **숫자를 다시 계산하기 전에 녹화 3~5개를 먼저 본다.** 원인이 대개 그 안에 있다.

`experiment_event` 테이블에서 먼저 이상한 세션을 찾았다면, PostHog 검색창에 그 `session_id`를 넣어 같은 세션의 녹화로 바로 갈 수 있다. 3장에서 `sessionId`를 이벤트 속성으로 실은 이유다.

---

## 세팅 완료 체크리스트

- [ ] PostHog 계정 생성, 리전 선택 완료 *(1장)*
- [ ] `index.html` `<head>`에 스니펫 삽입, `defaults` 값 포함 확인 *(2장)*
- [ ] `probe-app.js` `recordEvent()`에 미러링 블록 추가, `showProductDetail()`에 `PRODUCT_DETAIL_VIEWED` 추가 *(3장)*
- [ ] 로컬에서 플로우 1회 통과, Activity에서 이벤트 4개 확인 *(4장)*
- [ ] `?v=` 캐시 버스터 값 갱신 후 배포 *(4장)*
- [ ] 4단계 퍼널 생성, `$host ≠ localhost` 필터 적용, 대시보드에 고정 *(5장)*
- [ ] **Record user sessions 토글 켜짐 확인** *(6장 — 이번 선택의 핵심 기능)*

---

## 부록. 운영 참고

도구 선택 자체의 근거 — 왜 PostHog인지, 왜 프론트엔드 수집인지 — 는 [ADR 0013](adr/0013-제품-지표-수집-도구.md)에 있다. 여기에는 세팅과 운영 중에 참고할 것만 둔다.
아래는 세팅과 운영 중에 참고할 사실들이다.

### `PRODUCT_DETAIL_VIEWED`만 PostHog 전용인 이유

`experiment_event.event_name`은 MySQL **`ENUM` 컬럼**이다. `ExperimentEventName`에 값을 추가하면 컬럼 타입을 바꿔야 하는데, `ddl-auto`가 로컬 `update`·운영 `validate`이고 저장소에 마이그레이션 도구가 없다. 즉 **이벤트 하나 늘리는 데 수동 `ALTER TABLE`이 따라온다.**

PostHog에만 보내면 그 비용 없이 지표 2를 잴 수 있고, 지금 필요한 것은 전환율뿐이다. 나머지 12종은 이미 DB에 있는 행동이라 PostHog 전용 이름을 새로 붙이지 않는다. **이름을 공유해야 DB에서 찾은 세션을 PostHog에서 그대로 조회할 수 있다.**

DB 집계까지 필요해지면 그때 `ExperimentEventName`에 값을 추가하고 `ALTER TABLE`로 `ENUM`을 확장한 뒤, 해당 호출을 `recordEvent()`로 바꾼다.

### 무료 티어 한도

| 항목 | 월 무료 한도 |
|---|---|
| 이벤트 (Product analytics) | 100만 건 |
| 세션 리플레이 (웹) | **5,000건** |

이벤트보다 **리플레이 한도가 먼저 닿는다.** 사용자 1명이 플로우를 한 번 통과하면 이벤트는 10건 내외지만 리플레이는 1건이다. 초기 검증 기간에는 문제없고, 트래픽이 늘면 Settings → Project → Replay에서 샘플링 비율을 낮춘다.

### 광고 차단기로 인한 누락

일부 사용자의 이벤트는 수집되지 않는다. 전환율을 보는 목적이므로 검증에는 문제가 없지만, **"총 방문자 수"를 정확한 값으로 신뢰하면 안 된다.** 정확한 절대 수치는 `POST /api/probe/events`로 쌓이는 `experiment_event` 테이블에서 본다.

단, `PRODUCT_DETAIL_VIEWED`는 PostHog에만 쌓이므로 이 단계만은 DB로 보정할 수 없다.

### 환경 변수로 키를 분리할 때

`phc_` 키는 공개 키라 템플릿에 박아 둔다. dev/prod 프로젝트를 분리하게 되면 `application.yml`로 뺀다.

```yaml
posthog:
  key: ${POSTHOG_KEY:}
  host: ${POSTHOG_HOST:https://us.i.posthog.com}
```

```html
<script th:inline="javascript">
  /*<![CDATA[*/
  const POSTHOG_KEY  = /*[[${@environment.getProperty('posthog.key')}]]*/ '';
  const POSTHOG_HOST = /*[[${@environment.getProperty('posthog.host')}]]*/ '';
  /*]]>*/
</script>
```

`th:inline="javascript"`가 없으면 표현식이 치환되지 않고 주석으로 남는다. Docker 실행 시에는 `-e POSTHOG_KEY=phc_...`로 전달한다.

그때까지 로컬 개발 데이터는 인사이트의 `$host` 필터로 제외한다.

### 메뉴 이름이 다를 때

PostHog는 UI가 자주 바뀐다. 이 문서는 **2026년 8월 기준**이다. 적힌 메뉴가 보이지 않으면 [posthog.com/docs](https://posthog.com/docs)에서 확인하는 편이 빠르다.
