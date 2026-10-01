import type { PostHog } from 'posthog-js';

// 운영 배포에만 키를 넣는다. 키가 없는 개발·미리보기 환경에서는 수집하지 않는다.
const KEY = import.meta.env.VITE_POSTHOG_KEY;

type EventName =
  | 'onboarding_started'
  | 'onboarding_completed'
  | 'recommendation_result_viewed'
  | 'recommended_products_viewed'
  | 'board_entry_viewed'
  | 'board_entered'
  | 'post_viewed'
  | 'question_cta_viewed'
  | 'question_submitted';

type Properties = Record<string, string | null>;

let client: PostHog | null = null;
// 라이브러리를 받기 전에 일어난 이벤트는 모아 두었다가 초기화한 뒤 보낸다.
const pending: [EventName, Properties][] = [];
// 라이브러리를 받지 못했으면 수집을 그만둔다. 더는 이벤트를 모으지 않는다.
let unavailable = false;

/**
 * 지표 수집 라이브러리는 앱만큼 무거워 따로 받는다. 키가 없으면 받지도 않는다.
 * 받는 사이 앱이 주소를 바꾸면 첫 주소의 UTM 을 놓친다. 접속하자마자 주소를 바꾸는 곳은 예전 링크를 옮기는 LegacyRedirect 뿐이다.
 */
export function initAnalytics(): void {
  if (!KEY) {
    return;
  }
  // 팀원은 ?internal=1 로 한 번 접속해 그 브라우저의 수집을 끈다. 받는 사이 주소가 바뀔 수 있어 먼저 읽어 둔다.
  const url = new URL(window.location.href);
  const internal = url.searchParams.get('internal') === '1';
  if (url.searchParams.has('internal')) {
    // 주소를 복사해 건네면 받은 사람의 수집까지 꺼지므로 주소에서 지운다.
    url.searchParams.delete('internal');
    window.history.replaceState(window.history.state, '', url);
  }
  import('posthog-js')
    .then(({ default: posthog }) => {
      posthog.init(KEY, {
        api_host: 'https://us.i.posthog.com',
        // 주소만 바뀌는 SPA 라 화면 이동마다 $pageview 를 보내게 한다.
        capture_pageview: 'history_change',
        // 탭을 닫거나 사이트를 떠날 때 $pageleave 를 보낸다. 앱 안에서 이동할 때의 체류 시간은 다음 $pageview 에 담긴다.
        capture_pageleave: true,
        autocapture: false,
        // 수집을 끄기 전에 첫 $pageview 가 나가지 않게 한다.
        opt_out_capturing_by_default: internal,
      });
      if (internal) {
        // localStorage 에 저장되어 다음 방문부터는 주소에 붙이지 않아도 꺼져 있다.
        posthog.opt_out_capturing();
      }
      client = posthog;
      for (const [event, properties] of pending.splice(0)) {
        posthog.capture(event, properties);
      }
    })
    .catch(() => {
      // 지표를 못 모을 뿐 앱 동작에는 영향이 없다.
      unavailable = true;
      pending.length = 0;
    });
}

export function track(event: EventName, properties: Properties): void {
  if (client !== null) {
    client.capture(event, properties);
    return;
  }
  if (KEY && !unavailable) {
    pending.push([event, properties]);
  }
}
