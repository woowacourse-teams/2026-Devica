import type { BeforeSendFn, PostHog } from 'posthog-js';

// 운영 배포에만 키를 넣는다. 키가 없는 개발·미리보기 환경에서는 수집하지 않는다.
const KEY = import.meta.env.VITE_POSTHOG_KEY;

export type EventName =
  | 'landing_viewed'
  | 'service_entry_viewed'
  | 'service_entry_clicked'
  | 'landing_guide_viewed'
  | 'landing_guide_explored'
  | 'landing_guide_link_clicked'
  | 'onboarding_started'
  | 'onboarding_completed'
  | 'recommendation_result_viewed'
  | 'recommended_products_viewed'
  | 'board_entry_viewed'
  | 'board_entered'
  | 'post_viewed'
  | 'question_cta_viewed'
  | 'question_submitted';

export type Properties = Record<string, string | null>;

let client: PostHog | null = null;
// 라이브러리를 받기 전에 일어난 이벤트는 모아 두었다가 초기화한 뒤 보낸다.
const pending: [EventName, Properties, Date][] = [];
// 라이브러리를 받지 못했으면 수집을 그만둔다. 더는 이벤트를 모으지 않는다.
let unavailable = false;

// 같은 방문에서는 버튼 위치·페이지가 달라도 목적지별로 첫 노출만 센다.
const entryViewSessions = new Map<string, string>();
const deduplicateEntryView: BeforeSendFn = (event) => {
  if (event?.event !== 'service_entry_viewed') return event;
  const { destination, $session_id: sessionId } = event.properties;
  if ((destination !== 'spec_finder' && destination !== 'board') || typeof sessionId !== 'string' || !sessionId) {
    return event;
  }

  const storageKey = `devica:entry-view:${KEY}:${destination}`;
  if (entryViewSessions.get(destination) === sessionId) return null;
  try {
    // 새로고침·다른 탭에서도 PostHog의 같은 세션이면 다시 세지 않는다.
    if (window.localStorage.getItem(storageKey) === sessionId) {
      entryViewSessions.set(destination, sessionId);
      return null;
    }
  } catch {
    // 저장소가 차단되어도 현재 페이지 안에서는 메모리로 중복을 막는다.
  }
  entryViewSessions.set(destination, sessionId);
  try {
    window.localStorage.setItem(storageKey, sessionId);
  } catch {
    // 저장 실패가 측정이나 서비스 이동을 막지 않게 한다.
  }
  return event;
};

/**
 * 지표 수집 라이브러리는 앱만큼 무거워 따로 받는다. 키가 없으면 받지도 않는다.
 * 받는 사이 화면을 옮겨도 첫 유입의 UTM과 이벤트가 일어난 주소·시간을 유지한다.
 */
export function initAnalytics(): void {
  if (!KEY) {
    return;
  }
  // 팀원은 ?internal=1 로 한 번 접속해 그 브라우저의 수집을 끈다. 받는 사이 주소가 바뀔 수 있어 먼저 읽어 둔다.
  const url = new URL(window.location.href);
  const campaign = Object.fromEntries(
    ['utm_source', 'utm_medium', 'utm_campaign', 'utm_content', 'utm_term']
      .filter((key) => url.searchParams.has(key))
      .map((key) => [key, url.searchParams.get(key)]),
  );
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
        // SDK가 실제 이벤트의 세션 ID를 붙인 뒤 중복 노출만 제외한다. 클릭·기존 이벤트는 유지한다.
        before_send: deduplicateEntryView,
        // 수집을 끄기 전에 첫 $pageview 가 나가지 않게 한다.
        opt_out_capturing_by_default: internal,
        loaded: (instance) => {
          // SDK가 준비되기 전에 시작 버튼을 눌러 주소에서 UTM이 사라져도 이후 이벤트에 이어 붙인다.
          instance.register_for_session(campaign);
        },
      });
      if (internal) {
        // localStorage 에 저장되어 다음 방문부터는 주소에 붙이지 않아도 꺼져 있다.
        posthog.opt_out_capturing();
      }
      client = posthog;
      for (const [event, properties, timestamp] of pending.splice(0)) {
        posthog.capture(event, properties, { timestamp });
      }
    })
    .catch(() => {
      // 지표를 못 모을 뿐 앱 동작에는 영향이 없다.
      unavailable = true;
      pending.length = 0;
    });
}

export function track(event: EventName, properties: Properties): void {
  const context = { ...properties, $current_url: window.location.href, $pathname: window.location.pathname };
  const timestamp = new Date();
  if (client !== null) {
    client.capture(event, context, { timestamp });
    return;
  }
  if (KEY && !unavailable) {
    pending.push([event, context, timestamp]);
  }
}
