import type { PostHog } from 'posthog-js';

// 운영 배포에만 키를 넣는다. 키가 없는 개발·미리보기 환경에서는 수집하지 않는다.
const KEY = import.meta.env.VITE_POSTHOG_KEY;

type EventName =
  | 'onboarding_started'
  | 'onboarding_completed'
  | 'recommendation_result_viewed'
  | 'recommended_products_viewed';

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
  import('posthog-js')
    .then(({ default: posthog }) => {
      posthog.init(KEY, {
        api_host: 'https://us.i.posthog.com',
        // 주소만 바뀌는 SPA 라 화면 이동마다 $pageview 를 보내게 한다.
        capture_pageview: 'history_change',
        // 탭을 닫거나 사이트를 떠날 때 $pageleave 를 보낸다. 앱 안에서 이동할 때의 체류 시간은 다음 $pageview 에 담긴다.
        capture_pageleave: true,
        autocapture: false,
      });
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
