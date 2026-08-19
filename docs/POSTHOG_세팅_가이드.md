# PostHog 세팅 가이드

> **[1장](#1-계정-만들기)부터 [4장](#4-확인하기)까지 따라가면 세팅이 끝난다.** 20~30분 걸린다.
> [5장](#5-퍼널-만들기)·[6장](#6-세션-리플레이-켜기)은 쌓인 데이터를 읽는 방법이고, [부록](#부록-운영-참고)은 막혔을 때 찾아보는 곳이다.

## 0. 무엇을 재나

MVP가 실제로 동작하는지 확인하려고 PostHog를 붙인다. 확인할 것은 두 가지다.

| 지표 | 묻는 것 | 목표 |
|---|---|---|
| **지표 1** | 추천 플로우를 끝까지 완주하는가 | 60% 이상 |
| **지표 2** | 확정한 사양으로 제품을 실제로 살펴보는가 | 50% 이상 |

이 두 숫자는 아래 네 이벤트로 계산한다. 각 이벤트는 **화면 전환 한 번**에 대응한다.

| | 이벤트 | 사용자가 한 행동 |
|---|---|---|
| ① | `RECOMMENDATION_STARTED` | 첫 화면에서 «선택 사양 조정»을 눌렀다 |
| ② | `RECOMMENDATION_COMPLETED` | 질문을 다 답해서 권장 사양 결과가 나왔다 |
| ③ | `PRODUCT_LIST_VIEWED` | «이 사양으로 제품 보기»를 눌러 목록에 왔다 |
| ④ | `PRODUCT_DETAIL_VIEWED` | 제품 카드의 «상세 보기»를 눌렀다 |

**지표 1 = ③ ÷ ①**
**지표 2 = ④ ÷ ③**

①~③은 이미 앱에 있는 이벤트다. ④만 새로 만든다. 세팅에서 할 일은 **스니펫을 넣고, 기존 이벤트를 PostHog로도 보내는 것**이 전부다.

> **왜 이렇게 하나**
> 도구 선택 근거는 [ADR 0013](adr/0013-제품-지표-수집-도구.md)에, ④를 새로 만든 이유는 [부록](#posthog-전용-이벤트가-따로-있는-이유)에 있다.

### 시작 전에 알아둘 것

Devica는 **템플릿이 `index.html` 하나뿐인 단일 페이지 앱**이다. 화면 5개를 클래스 토글로 바꾸므로 **URL이 변하지 않는다.**

그래서 PostHog 공식 문서나 타임리프 예제와 세 군데가 다르다.

- 공통 fragment를 안 만든다. 상속시킬 템플릿이 없다.
- URL 파라미터로 이벤트를 나누지 않는다. URL이 안 바뀐다.
- `$pageview`는 세션당 1건뿐이다. 위 커스텀 이벤트가 화면 전환을 기록하는 유일한 수단이다.

**필요한 것**: PostHog 계정, 그리고 `templates/pages/probe/index.html`과 `static/js/probe-app.js`를 고쳐 배포할 수 있는 권한.

---

## 1. 계정 만들기

1. [posthog.com](https://posthog.com)에서 회원가입한다.
2. **호스팅 리전**을 고른다. 나중에 바꾸려면 데이터 마이그레이션이 필요하다.
   - **US** — 기본값. 특별한 요건이 없으면 이쪽.
   - **EU** — GDPR 등 유럽 규정을 따라야 할 때.
3. 프로젝트는 자동으로 만들어진다.

가입이 끝나면 온보딩 화면에 두 값이 나온다. **복사해 둔다.**

| 값 | 형태 |
|---|---|
| Project API Key | `phc_`로 시작하는 문자열 |
| Host URL | `https://us.i.posthog.com` 또는 `https://eu.i.posthog.com` |

나중에 **Settings → Project**에서 다시 볼 수 있다.

> `phc_` 키는 브라우저에 노출되는 것을 전제로 만들어진 **공개 키**다. HTML에 그대로 박아도 된다.
> 반면 **Personal API Key**는 계정 전체 권한을 가진다. 절대 노출하면 안 되고, 이번 세팅에서는 쓰지 않는다.

---

## 2. 스니펫 넣기

`templates/pages/probe/index.html`의 `<head>`, 마지막 `<link>` 뒤에 넣는다.

```html
    <link rel="stylesheet" th:href="@{/css/pages/probe/index.css(v='...')}">

    <script>
      /* PostHog가 주는 스니펫 본문 */
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

스니펫 본문은 **Settings → Project → Web snippet**(또는 온보딩 화면의 HTML 탭)에서 통째로 복사한다. 복사한 마지막 줄의 `posthog.init(...)`을 위 형태로 바꾼다.

> **`defaults`를 빼먹지 말 것.**
> 설정 스냅샷의 날짜다. 없으면 PostHog가 레거시 기본값으로 초기화된다. 인터넷의 오래된 예제는 대부분 `posthog.init(KEY, { api_host })`까지만 적혀 있으니 그대로 쓰지 않는다. 최신 날짜는 [PostHog JS 문서](https://posthog.com/docs/libraries/js)에서 확인한다.

여기까지 하면 페이지뷰와 세션 리플레이는 자동으로 수집된다.

---

## 3. 이벤트 연결하기

### 기존 이벤트 미러링 (①②③)

`static/js/probe-app.js`의 `recordEvent()` 맨 앞에 넣는다.

```js
    function recordEvent(eventName, details = {}) {
        // DB로 보내는 이벤트를 PostHog로도 그대로 미러링한다.
        window.posthog?.capture(eventName, {
            ...details,
            sessionId: state.sessionId,
            questionSetVersion: config.version
        });

        const request = {
            // ...기존 코드 그대로
```

이 한 블록으로 앱의 12개 이벤트가 전부 PostHog에 들어간다. 기존 DB 적재는 그대로 남는다.

- **순서 걱정 없다.** 스니펫 스텁이 `capture` 호출을 큐에 쌓아뒀다가 로드 후 보낸다. `probe-app.js`가 `defer`여도 안전하다.
- **`sessionId`를 같이 싣는다.** `experiment_event` 테이블의 `session_id`와 같은 값이라, DB에서 찾은 세션을 PostHog에서 바로 조회할 수 있다.
- **`window.posthog?.`로 접근한다.** 광고 차단기가 스니펫을 막아도 추천 플로우는 멈추지 않는다.

### 상세 열람 이벤트 추가 (④)

같은 파일의 `showProductDetail()`에 넣는다.

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

여기만 `recordEvent()`를 안 쓰고 `posthog.capture()`를 직접 부른다. [이유는 부록에](#posthog-전용-이벤트가-따로-있는-이유).

### 건너뛴 질문 기록

지표 1·2에는 안 쓰이지만, **어느 질문이 자주 버려지는지**는 문항을 다듬는 근거가 된다. 같은 파일의 `showNextQuestion()` 맨 앞에 넣는다.

```js
    function showNextQuestion() {
        const question = state.visibleQuestions[state.questionIndex];
        if (!isQuestionComplete(question)) {
            // 건너뛰기는 experiment_event ENUM에 없어 PostHog에만 보낸다.
            window.posthog?.capture("QUESTION_SKIPPED", {
                questionId: question.id,
                sessionId: state.sessionId,
                questionSetVersion: config.version
            });
        }
        // ...기존 코드 그대로
```

건너뛰기는 답을 저장하지 않으므로 `recordEvent()`를 타지 않는다. 이 호출이 없으면 **PostHog에 아무 흔적도 남지 않는다.** `PRODUCT_DETAIL_VIEWED`와 같은 이유로 DB에는 안 넣는다.

`questionId`로 분해하면 질문별 건너뛰기 수가 바로 나온다. Product analytics → Trends에서 `QUESTION_SKIPPED`를 고르고 **Breakdown**에 `questionId`를 지정하면 된다.

### 지금은 안 해도 되는 것

- **이벤트에 속성 더 붙이기** — 지표 계산에 안 쓰인다. 필요해지면 `details`에 얹으면 된다.
- **`posthog.identify()`** — 로그인 없는 MVP는 익명 ID로 충분하다.

---

## 4. 확인하기

배포 전에 로컬에서 먼저 본다.

1. 앱을 띄우고 **플로우를 끝까지 한 번 통과**한다.
   시작 → 질문 답변 → 결과 확인 → 제품 보기 → **제품 하나의 «상세 보기»** 까지 눌러야 네 단계가 다 발생한다.
   도중에 **질문 하나는 아무것도 고르지 말고 «건너뛰기»** 로 넘긴다. `QUESTION_SKIPPED`까지 같이 확인할 수 있다.
2. PostHog 왼쪽 메뉴에서 **Activity**를 연다. 30초마다 자동 갱신된다.
   기간 기본값이 **Last hour**라 비어 보이기 쉽다. **Last 7 days**로 바꿔 본다.
3. 네 이벤트가 다 올라왔는지 확인한다.
   `RECOMMENDATION_STARTED` · `RECOMMENDATION_COMPLETED` · `PRODUCT_LIST_VIEWED` · `PRODUCT_DETAIL_VIEWED`
   건너뛴 질문이 있으면 `QUESTION_SKIPPED`도 함께 보인다.

확인이 끝나면 **`?v=` 캐시 버스터 값을 올리고 배포**한다.

### 이벤트가 안 보일 때

| 증상 | 해결 |
|---|---|
| 아무것도 안 뜬다 | 광고 차단기를 의심한다. 시크릿 창에서 재시도 |
| 아무것도 안 뜬다 | 페이지 소스 보기로 `posthog.init`이 실제로 렌더링됐는지 확인 |
| `$pageview`만 뜨고 커스텀 이벤트가 없다 | JS가 캐시됐다. `?v=` 값을 올려 다시 받게 한다 |
| 이벤트는 뜨는데 `sessionId`가 없다 | `capture()` 두 번째 인자를 빠뜨렸다. 3장 코드와 대조 |
| `$autocapture`가 잔뜩 뜬다 | 정상이다. 무시해도 되고, 거슬리면 Settings → Project에서 끈다 |

---

## 5. 퍼널 만들기

이벤트가 한 번이라도 들어와야 선택 목록에 이름이 뜬다. 4장을 끝낸 뒤에 만든다.

1. 왼쪽 메뉴 **Product analytics → New insight**
2. 상단 탭에서 **Funnel** 선택
3. 단계를 순서대로 추가한다.
   - Step 1 `RECOMMENDATION_STARTED`
   - Step 2 `RECOMMENDATION_COMPLETED`
   - Step 3 `PRODUCT_LIST_VIEWED`
   - Step 4 `PRODUCT_DETAIL_VIEWED`
4. 필터에 **`Host` `= equals` `devica.co.kr`** 을 추가한다.
5. **Save** → `추천_플로우_퍼널` → **Add to dashboard**

### 읽는 법

우측 상단 레이아웃을 **Top to bottom**으로 바꾸면 읽기 쉽다.

```
Total conversion rate: 0.00%

1  RECOMMENDATION_STARTED     3 persons
2  RECOMMENDATION_COMPLETED   1 person  (33.33%) completed step   2 persons (66.67%) dropped off
3  PRODUCT_LIST_VIEWED        1 person  (33.33%) completed step   0 persons (66.67%) dropped off
4  PRODUCT_DETAIL_VIEWED      0 persons (0%)     completed step   1 person  (100%)   dropped off
```

| 지표 | 어디서 읽나 | 위 예시 |
|---|---|---|
| 지표 1 | Step 3 옆 괄호 % 를 그대로 | 33.3% |
| 지표 2 | Step 4 명수 ÷ Step 3 명수 | 0 ÷ 1 = 0% |

#### 괄호 안 %에 주의

**명수는 직전 단계 기준인데, %는 Step 1 기준이다.** 기준이 섞여 있어 헷갈린다. 위 3번 줄을 뜯어보면:

```
3  PRODUCT_LIST_VIEWED   1 person (33.33%)   0 persons (66.67%) dropped off
```

- `1 person` — 2단계에서 3단계로 **넘어온 인원**
- `(33.33%)` — 1÷3, **Step 1 대비** 비율
- `0 persons` — 2→3 구간에서 **이탈한 인원**
- `(66.67%)` — 2÷3, 이것도 **Step 1 대비** 비율

"0명이 이탈했는데 66.67%"는 오류가 아니다. 인원은 구간 기준, 비율은 전체 기준이라 그렇다.

그래서 **지표 1은 Step 3의 %를 그대로 읽으면 되지만, 지표 2는 화면에 없다.** PostHog 퍼널은 단계 간 전환율을 표시하지 않는다.

> 지표 2를 계산 없이 보고 싶으면 `PRODUCT_LIST_VIEWED → PRODUCT_DETAIL_VIEWED` **2단계 퍼널**을 하나 더 만든다. 2단계짜리는 `Total conversion rate`가 곧 지표 2다.

#### 숫자보다 먼저 볼 것

어느 구간에서 끊겼는지가 전환율보다 빨리 답을 준다. 위 예시라면 이렇게 읽는다.

- 3명 중 2명이 **질문 도중에 이탈**했다 (①→②)
- 완주한 1명은 목록까지 그대로 갔다 (②→③, 이탈 0명)
- 목록까지 간 1명이 **상세를 안 열고 나갔다** (③→④)

이탈이 몰린 막대를 클릭하면 그 사용자 목록이 뜨고, **View recordings**로 세션 녹화까지 이어진다.

**한 가지 한계**: 목록을 훑기만 하고 «상세 보기»를 안 누른 사람은 Step 4에 안 잡힌다. ③→④가 낮으면 숫자를 다시 재기 전에 녹화부터 본다.

### 퍼널이 비어 보일 때

| 증상 | 해결 |
|---|---|
| 대시보드 타일만 비었고 인사이트를 열면 정상 | 타일이 옛 결과를 캐시하고 있다. 대시보드 상단 **Refresh**. 쿨다운이면 몇 분 뒤 다시 |
| 인사이트도 "There are no matching events" | 저장된 결과가 캐시된 것이다. 기간을 다른 값으로 바꿨다 되돌리면 강제 재계산된다 (`?refresh=true`는 안 먹힌다) |
| 배포했는데 여전히 비었다 | `Host` 필터 값이 실제 도메인과 다르다. Activity에서 실제 `$host`를 확인해 맞춘다 |
| Step 4만 0이다 | 배포본 JS에 `PRODUCT_DETAIL_VIEWED`가 있는지 먼저 확인한다. 있다면 **진짜로 아무도 안 누른 것**이다 |

---

## 6. 세션 리플레이 켜기

Devica는 URL이 안 바뀌어서 이탈 지점을 주소로 되짚을 수 없다. **녹화가 사실상 유일한 단서다.** 반드시 켜져 있는지 확인한다.

1. **Settings → Project → Replay**
2. **Record user sessions** 토글을 켠다. 꺼져 있으면 녹화가 전혀 안 된다.
3. 왼쪽 메뉴 **Session replay**에서 목록을 본다.

### 퍼널과 이어서 쓰기

1. 5장에서 만든 퍼널에서 **이탈이 발생한 구간을 클릭**한다.
2. 그 구간에서 빠져나간 사용자 목록이 뜬다.
3. 재생 아이콘을 누르면 해당 세션 녹화로 이동한다.

지표가 목표에 미달했을 때는 **숫자를 다시 계산하기 전에 녹화 3~5개를 먼저 본다.** 원인이 대개 그 안에 있다.

DB에서 먼저 이상한 세션을 찾았다면, PostHog 검색창에 그 `session_id`를 넣으면 같은 세션의 녹화로 바로 간다. 3장에서 `sessionId`를 실은 이유다.

---

## 체크리스트

- [ ] 계정 생성, 리전 선택 *(1장)*
- [ ] `index.html`에 스니펫 삽입, `defaults` 포함 확인 *(2장)*
- [ ] `recordEvent()`에 미러링 블록 추가 *(3장)*
- [ ] `showProductDetail()`에 `PRODUCT_DETAIL_VIEWED` 추가 *(3장)*
- [ ] `showNextQuestion()`에 `QUESTION_SKIPPED` 추가 *(3장)*
- [ ] 로컬에서 플로우 1회 통과, Activity에서 이벤트 4개 확인 *(4장)*
- [ ] `?v=` 값 갱신 후 배포 *(4장)*
- [ ] 4단계 퍼널 생성, `Host = devica.co.kr` 필터, 대시보드에 고정 *(5장)*
- [ ] **Record user sessions 토글 켜짐 확인** *(6장)*

---

## 부록. 운영 참고

도구 선택 근거 — 왜 PostHog인지, 왜 프론트엔드 수집인지 — 는 [ADR 0013](adr/0013-제품-지표-수집-도구.md)에 있다. 여기에는 운영하면서 참고할 것만 둔다.

### PostHog 전용 이벤트가 따로 있는 이유

`PRODUCT_DETAIL_VIEWED`와 `QUESTION_SKIPPED` 두 개는 `recordEvent()`를 안 타고 `posthog.capture()`를 직접 부른다. **DB에 대응하는 행동이 원래 없었다는 점**이 같다.

- **`PRODUCT_DETAIL_VIEWED`** — 이름이 비슷한 `PRODUCT_CLICKED`는 상세 화면 안의 **구매 링크 클릭**이라, 지표 2가 묻는 "리스트를 살펴봤는가"보다 한참 뒤의 행동이다.
- **`QUESTION_SKIPPED`** — 건너뛰기는 저장할 답이 없어서 `QUESTION_ANSWERED`가 발생하지 않는다. 이벤트가 아예 안 나가므로 **부재로만 추론**해야 하는데, 그 추론은 퍼널로 짜기 번거롭다.

그렇다고 DB에 이벤트를 추가하기는 비싸다. `experiment_event.event_name`이 MySQL **`ENUM` 컬럼**이라 값을 늘리면 컬럼 타입을 바꿔야 하는데, `ddl-auto`가 로컬 `update`·운영 `validate`이고 저장소에 마이그레이션 도구가 없다. **이벤트 하나에 수동 `ALTER TABLE`이 따라온다.**

PostHog에만 보내면 그 비용 없이 둘 다 잴 수 있다.

나머지 12종은 이미 DB에 있는 행동이라 새 이름을 붙이지 않는다. **이름을 공유해야 DB에서 찾은 세션을 PostHog에서 그대로 조회할 수 있다.**

DB 집계까지 필요해지면 그때 `ExperimentEventName`에 값을 추가하고 `ALTER TABLE`로 `ENUM`을 넓힌 뒤, 해당 호출을 `recordEvent()`로 바꾼다.

### 무료 티어 한도

| 항목 | 월 무료 한도 |
|---|---|
| 이벤트 | 100만 건 |
| 세션 리플레이 (웹) | **5,000건** |

**리플레이 한도가 먼저 닿는다.** 한 사람이 플로우를 한 번 통과하면 이벤트는 10건 남짓이지만 리플레이는 1건이다. 초기 검증에는 문제없고, 트래픽이 늘면 Settings → Project → Replay에서 샘플링을 낮춘다.

### 광고 차단기로 인한 누락

일부 사용자의 이벤트는 수집되지 않는다. 전환율을 보는 목적이라 검증에는 지장이 없지만, **"총 방문자 수"를 정확한 값으로 믿으면 안 된다.** 정확한 수치는 `experiment_event` 테이블에서 본다.

단 `PRODUCT_DETAIL_VIEWED`는 PostHog에만 쌓이므로, 이 단계만은 DB로 보정할 수 없다.

### 키를 환경 변수로 뺄 때

`phc_`는 공개 키라 지금은 템플릿에 박아 둔다. dev/prod 프로젝트를 분리하게 되면 `application.yml`로 뺀다.

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

`th:inline="javascript"`가 없으면 표현식이 치환되지 않고 주석으로 남는다. Docker로 실행할 때는 `-e POSTHOG_KEY=phc_...`로 넘긴다.

그때까지 로컬 데이터는 [5장](#5-퍼널-만들기)의 `Host = devica.co.kr` 필터로 걸러낸다.

### 메뉴 이름이 다를 때

PostHog는 UI가 자주 바뀐다. 이 문서는 **2026년 8월 기준**이다. 적힌 메뉴가 안 보이면 [posthog.com/docs](https://posthog.com/docs)를 보는 편이 빠르다.
