import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import { setImmediate } from 'node:timers/promises';
import { createContext, SourceTextModule, SyntheticModule } from 'node:vm';
import { transformWithOxc } from 'vite';

const source = await readFile(new URL('../src/analytics.ts', import.meta.url), 'utf8');
const { code } = await transformWithOxc(source, 'analytics.ts');

function memoryStorage() {
  const data = new Map();
  return {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
  };
}

async function setup({
  key = 'test-project-key',
  url = 'https://devica.test/',
  optedOut = false,
  sessionId = 'session-1',
  storage = memoryStorage(),
} = {}) {
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
    localStorage: storage,
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
      if (optedOut) return;
      const payload = { event, properties: { ...session, ...properties, $session_id: sessionId }, ...captureOptions };
      const result = options.before_send ? options.before_send(payload) : payload;
      if (result !== null) events.push(result);
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
    setSession: (next) => {
      sessionId = next;
    },
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

test('여러 위치를 보고 다른 페이지로 이동해도 같은 세션에는 목적지별 노출을 한 번만 보낸다', async () => {
  const app = await setup({ url: 'https://devica.test/?utm_source=everytime2' });
  app.analytics.initAnalytics();
  // SDK 로딩 전에 쌓인 노출도 같은 규칙을 적용한다.
  for (const entry_point of ['landing_hero', 'landing_guide', 'landing_closing', 'header']) {
    for (const destination of ['spec_finder', 'board']) {
      app.analytics.track('service_entry_viewed', { destination, entry_point });
    }
  }
  await app.load();
  app.window.location = new URL('https://devica.test/category');
  app.analytics.track('service_entry_viewed', { destination: 'board', entry_point: 'header' });
  assert.equal(app.events.length, 2);
  assert.deepEqual(
    app.events.map(({ properties }) => properties.destination),
    ['spec_finder', 'board'],
  );
  for (const { properties } of app.events) {
    assert.equal(properties.entry_point, 'landing_hero');
    assert.equal(properties.utm_source, 'everytime2');
  }
});

test('사양 찾기를 시작한 뒤 헤더로 게시판에 들어가도 클릭과 실제 게시판 진입은 기록한다', async () => {
  const app = await setup({ url: 'https://devica.test/?utm_source=everytime2' });
  app.analytics.initAnalytics();
  await app.load();
  app.analytics.track('service_entry_viewed', { destination: 'board', entry_point: 'landing_hero' });
  app.analytics.track('service_entry_viewed', { destination: 'spec_finder', entry_point: 'landing_hero' });
  app.analytics.track('service_entry_clicked', { destination: 'spec_finder', entry_point: 'landing_hero' });
  app.window.location = new URL('https://devica.test/questions/1');
  app.analytics.track('onboarding_started', { category: 'LAPTOP', purpose: 'BACKEND_DEVELOPMENT' });
  app.analytics.track('service_entry_viewed', { destination: 'board', entry_point: 'header' });
  app.analytics.track('service_entry_clicked', { destination: 'board', entry_point: 'header' });
  app.window.location = new URL('https://devica.test/board');
  app.analytics.track('board_entered', {});
  assert.equal(
    app.events.filter(({ event, properties }) => event === 'service_entry_viewed' && properties.destination === 'board')
      .length,
    1,
  );
  const click = app.events.find(
    ({ event, properties }) => event === 'service_entry_clicked' && properties.destination === 'board',
  );
  assert.equal(click.properties.entry_point, 'header');
  assert.equal(click.properties.$pathname, '/questions/1');
  const entered = app.events.filter(({ event }) => event === 'board_entered');
  assert.equal(entered.length, 1);
  assert.equal(entered[0].properties.utm_source, 'everytime2');
  assert.equal(entered[0].properties.$pathname, '/board');
});

test('새로고침과 다른 탭에서는 같은 세션의 노출을 유지하고 새 세션에서는 다시 센다', async () => {
  const storage = memoryStorage();
  const app = await setup({ storage });
  app.analytics.initAnalytics();
  await app.load();
  app.analytics.track('service_entry_viewed', { destination: 'board', entry_point: 'landing_hero' });

  const revisited = await setup({ storage });
  revisited.analytics.initAnalytics();
  await revisited.load();
  revisited.analytics.track('service_entry_viewed', { destination: 'board', entry_point: 'header' });
  assert.equal(revisited.events.length, 0);
  revisited.analytics.track('service_entry_clicked', { destination: 'board', entry_point: 'header' });
  assert.equal(revisited.events.length, 1);
  revisited.setSession('session-2');
  revisited.analytics.track('service_entry_viewed', { destination: 'board', entry_point: 'header' });
  assert.equal(revisited.events.length, 2);
  assert.equal(revisited.events[1].properties.$session_id, 'session-2');
});

test('저장소가 차단되어도 현재 페이지의 노출은 중복되지 않고 클릭은 모두 기록한다', async () => {
  const storage = {
    getItem: () => {
      throw new Error('Storage blocked');
    },
    setItem: () => {
      throw new Error('Storage blocked');
    },
  };
  const app = await setup({ storage });
  app.analytics.initAnalytics();
  await app.load();
  for (const entry_point of ['landing_hero', 'landing_guide', 'landing_closing']) {
    app.analytics.track('service_entry_viewed', { destination: 'spec_finder', entry_point });
    app.analytics.track('service_entry_clicked', { destination: 'spec_finder', entry_point });
  }
  assert.equal(app.events.filter(({ event }) => event === 'service_entry_viewed').length, 1);
  assert.equal(app.events.filter(({ event }) => event === 'service_entry_clicked').length, 3);
});

test('중복 제거는 새 노출 이벤트에만 적용하고 기존 이벤트와 자동 페이지뷰는 유지한다', async () => {
  const app = await setup();
  app.analytics.initAnalytics();
  await app.load();
  for (let index = 0; index < 2; index += 1) {
    app.analytics.track('board_entry_viewed', {});
    app.analytics.track('board_entered', {});
    const pageview = { event: '$pageview', properties: { $session_id: 'session-1' } };
    assert.equal(app.options().before_send(pageview), pageview);
  }
  assert.equal(app.events.length, 4);
  assert.equal(app.options().before_send(null), null);
});

test('이전 세션을 읽을 수 있지만 저장 공간 부족으로 새 기록을 쓰지 못해도 중복 노출을 막는다', async () => {
  const app = await setup({
    storage: {
      getItem: () => 'old-session',
      setItem: () => {
        throw new Error('Storage quota exceeded');
      },
    },
  });
  app.analytics.initAnalytics();
  await app.load();
  for (const entry_point of ['landing_hero', 'landing_closing', 'header']) {
    app.analytics.track('service_entry_viewed', { destination: 'board', entry_point });
  }
  assert.equal(app.events.length, 1);
});
