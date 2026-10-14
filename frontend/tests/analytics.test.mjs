import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { createContext, SourceTextModule, SyntheticModule } from 'node:vm';
import { transformWithOxc } from 'vite';

const source = await readFile(new URL('../src/analytics.ts', import.meta.url), 'utf8');
const { code } = await transformWithOxc(source, 'analytics.ts');

async function setup({ key = 'test-project-key', url = 'https://devica.test/', optedOut = false } = {}) {
  const events = [];
  const session = {};
  let options;
  let importCount = 0;
  let resolveSdk;
  let rejectSdk;
  const ready = new Promise((resolve, reject) => {
    resolveSdk = resolve;
    rejectSdk = reject;
  });
  const window = {
    location: new URL(url),
    history: {
      replaceState: (_state, _title, next) => {
        window.location = new URL(next);
      },
    },
  };
  const sdk = {
    init: (_key, config) => {
      options = config;
      optedOut ||= config.opt_out_capturing_by_default;
      config.loaded?.(sdk);
    },
    register_for_session: (properties) => Object.assign(session, properties),
    opt_out_capturing: () => {
      optedOut = true;
    },
    capture: (event, properties, captureOptions) => {
      if (!optedOut) events.push({ event, properties: { ...session, ...properties }, ...captureOptions });
    },
  };
  const context = createContext({ window, URL, Date });
  const sdkModule = new SyntheticModule(
    ['default'],
    function () {
      this.setExport('default', sdk);
    },
    { context },
  );
  await sdkModule.link(() => {});
  await sdkModule.evaluate();
  const module = new SourceTextModule(code, {
    context,
    initializeImportMeta: (meta) => {
      meta.env = { VITE_POSTHOG_KEY: key };
    },
    importModuleDynamically: () => {
      importCount += 1;
      return ready;
    },
  });
  await module.link(() => {});
  await module.evaluate();
  return {
    analytics: module.namespace,
    events,
    window,
    options: () => options,
    imports: () => importCount,
    load: async () => {
      resolveSdk(sdkModule);
      await setImmediate();
    },
    fail: async () => {
      rejectSdk(new Error('SDK unavailable'));
      await setImmediate();
    },
  };
}

test('키가 없는 개발 환경에서는 SDK를 요청하거나 이벤트를 보내지 않는다', async () => {
  const app = await setup({ key: '' });
  app.analytics.initAnalytics();
  app.analytics.track('landing_viewed', {});
  assert.equal(app.imports(), 0);
  assert.equal(app.events.length, 0);
});

test('SDK 로딩 중 이동해도 클릭 당시 주소·시각과 이후 이벤트의 최초 UTM을 보존한다', async () => {
  const initial = 'https://devica.test/?utm_source=everytime2&utm_medium=community';
  const app = await setup({ url: initial });
  app.analytics.initAnalytics();
  const beforeClick = Date.now();
  app.analytics.track('service_entry_clicked', { entry_point: 'landing_hero', destination: 'spec_finder' });
  const afterClick = Date.now();
  app.window.location = new URL('https://devica.test/category');
  await app.load();
  const click = app.events[0];
  assert.equal(click.event, 'service_entry_clicked');
  assert.equal(click.properties.$current_url, initial);
  assert.equal(click.properties.$pathname, '/');
  assert.equal(click.properties.utm_source, 'everytime2');
  assert.equal(click.properties.entry_point, 'landing_hero');
  assert.ok(click.timestamp.getTime() >= beforeClick && click.timestamp.getTime() <= afterClick);
  app.analytics.track('onboarding_started', { category: 'LAPTOP', purpose: 'BACKEND_DEVELOPMENT' });
  assert.equal(app.events[1].properties.utm_source, 'everytime2');
  assert.equal(app.events[1].properties.utm_medium, 'community');
  assert.equal(app.events[1].properties.$pathname, '/category');
});

test('기존 지표 이벤트 이름과 속성을 바꾸거나 중복 전송하지 않는다', async () => {
  const app = await setup({ url: 'https://devica.test/?utm_source=everytime' });
  app.analytics.initAnalytics();
  await app.load();
  const names = [
    'onboarding_started',
    'onboarding_completed',
    'recommendation_result_viewed',
    'recommended_products_viewed',
    'board_entry_viewed',
    'board_entered',
    'post_viewed',
    'question_cta_viewed',
    'question_submitted',
  ];
  for (const name of names) app.analytics.track(name, { category: 'LAPTOP', purpose: 'BACKEND_DEVELOPMENT' });
  assert.deepEqual(
    app.events.map(({ event }) => event),
    names,
  );
  for (const { properties } of app.events) {
    assert.equal(properties.category, 'LAPTOP');
    assert.equal(properties.purpose, 'BACKEND_DEVELOPMENT');
    assert.equal(properties.utm_source, 'everytime');
  }
  assert.equal(app.options().capture_pageview, 'history_change');
  assert.equal(app.options().autocapture, false);
});

test('internal=1은 주소에서만 제거하고 대기 중인 새 이벤트도 수집하지 않는다', async () => {
  const app = await setup({ url: 'https://devica.test/?internal=1&utm_source=everytime' });
  app.analytics.initAnalytics();
  app.analytics.track('service_entry_viewed', { entry_point: 'landing_hero', destination: 'board' });
  await app.load();
  assert.equal(app.window.location.searchParams.has('internal'), false);
  assert.equal(app.window.location.searchParams.get('utm_source'), 'everytime');
  assert.equal(app.options().opt_out_capturing_by_default, true);
  assert.equal(app.events.length, 0);
});

test('기존 브라우저의 수집 제외 상태를 해제하지 않는다', async () => {
  const app = await setup({ optedOut: true });
  app.analytics.initAnalytics();
  await app.load();
  app.analytics.track('landing_viewed', {});
  assert.equal(app.events.length, 0);
});

test('SDK 로딩 실패가 서비스 이벤트 호출의 오류나 추가 SDK 요청으로 이어지지 않는다', async () => {
  const app = await setup();
  app.analytics.initAnalytics();
  app.analytics.track('landing_viewed', {});
  await app.fail();
  assert.doesNotThrow(() => app.analytics.track('service_entry_clicked', { destination: 'board' }));
  assert.equal(app.imports(), 1);
  assert.equal(app.events.length, 0);
});
