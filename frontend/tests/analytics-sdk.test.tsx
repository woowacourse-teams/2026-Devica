import type { CaptureResult, PostHog } from 'posthog-js';
import { afterEach, expect, test, vi } from 'vitest';

const observed = vi.hoisted(() => ({ events: [] as CaptureResult[], client: null as PostHog | null }));

// 실제 SDK의 세션 교체와 속성 병합을 실행한다. 외부 전송과 원격 설정만 끈다.
vi.mock('posthog-js', async (importOriginal) => {
  const { PostHog } = await importOriginal<typeof import('posthog-js')>();
  const client = new PostHog();
  observed.client = client;
  const init = client.init.bind(client);
  vi.spyOn(client, 'init').mockImplementation((key, options) =>
    init(key, {
      ...options,
      persistence: 'memory',
      advanced_disable_flags: true,
      disable_external_dependency_loading: true,
      disable_session_recording: true,
      capture_pageview: false,
      capture_pageleave: false,
      loaded: (instance) => {
        // 이전 방문의 세션이 만료된 채 새 페이지를 여는 상황.
        instance.sessionManager?.resetSessionId();
        const expiredAt = Date.now() - 31 * 60 * 1000;
        client.persistence?.register({ $sesid: [expiredAt, 'expired-session', expiredAt] });
        options?.loaded?.(instance);
      },
      before_send: (event) => {
        const result = typeof options?.before_send === 'function' ? options.before_send(event) : event;
        if (result) observed.events.push(result);
        return null;
      },
    }),
  );
  return { default: client };
});

afterEach(async () => {
  await observed.client?.shutdown();
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
});

test('만료된 세션으로 재방문해도 첫 이벤트와 이후 화면에 최초 UTM을 유지한다', async () => {
  vi.stubEnv('VITE_POSTHOG_KEY', 'test-project-key');
  window.history.replaceState(null, '', '/?utm_source=everytime2&utm_medium=community');
  const { initAnalytics, track } = await import('../src/analytics');
  initAnalytics();
  track('board_entry_viewed', {});
  // SDK가 준비되기 전에 시작 버튼을 눌러 주소에서 UTM이 사라진 경우도 포함한다.
  window.history.replaceState(null, '', '/category');
  await vi.waitFor(() => expect(observed.events).toHaveLength(1));
  track('onboarding_started', {});
  expect(observed.events.map((event) => event.properties.utm_source)).toEqual(['everytime2', 'everytime2']);
  expect(observed.events.map((event) => event.properties.utm_medium)).toEqual(['community', 'community']);
  expect(observed.events[0].properties.$session_id).not.toBe('expired-session');

  // 같은 페이지에서 다음 세션이 시작되면 이전 캠페인을 다시 붙이지 않는다.
  const nextVisit = Date.now() + 31 * 60 * 1000;
  vi.spyOn(Date, 'now').mockReturnValue(nextVisit);
  observed.client?.capture('board_entered', {}, { timestamp: new Date(nextVisit) });
  expect(observed.events.at(-1)?.properties.utm_source).toBeUndefined();
});
