// performance/k6/product-list/sessions.js
// 세션: 사용자 한 명이 목록 화면 하나를 스크롤하는 과정
import { SharedArray } from 'k6/data';
import { check, sleep } from 'k6';
import { jsonBody } from '../common/http.js';
import { getProductsAtOnce } from './requests.js';

const PAGE_SIZE = 12;
const PAGES = [0, 1, 2];
const SEED = 199;

// 추천 알고리즘(LaptopBackendAlgorithm)이 낼 수 있는 사양 조합
// Mac은 CPU 등급마다 살 수 있는 메모리가 정해져 있고, MAX는 PRO로 제한된다
const MAC_CPU_MEMORY = [
  ['BASIC', 16], ['BASIC', 24], ['BASIC', 32],
  ['PRO', 24], ['PRO', 48],
];
const WINDOWS_CPU = ['P_HS', 'H', 'HX'];
const WINDOWS_MEMORY = [16, 24, 32, 48];
const STORAGE = [256, 512, 1024];

const SPECS = {
  MAC: MAC_CPU_MEMORY.flatMap(([cpuTier, memoryGb]) =>
    STORAGE.map((storageGb) => ({ os: 'MAC', cpuTier, memoryGb, storageGb }))),
  WINDOWS: WINDOWS_CPU.flatMap((cpuTier) => WINDOWS_MEMORY.flatMap((memoryGb) =>
    STORAGE.map((storageGb) => ({ os: 'WINDOWS', cpuTier, memoryGb, storageGb })))),
};

// 맥 : 윈도우 : 둘 다 = 1 : 1 : 1. 둘 다 화면은 합친 목록을 가격으로만 정렬할 수 있어 추천순이 없다
const SCREENS = {
  MAC: { oses: ['MAC'], sorts: ['RECOMMENDED', 'PRICE_ASC', 'PRICE_DESC'] },
  WINDOWS: { oses: ['WINDOWS'], sorts: ['RECOMMENDED', 'PRICE_ASC', 'PRICE_DESC'] },
  BOTH: { oses: ['MAC', 'WINDOWS'], sorts: ['PRICE_ASC', 'PRICE_DESC'] },
};

// 시드를 고정한 난수(mulberry32). 같은 개수를 만들면 항상 같은 목록이 나온다
function seededRandom(seed) {
  let state = seed >>> 0;

  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function buildSessions(count) {
  const random = seededRandom(SEED);
  const pick = (items) => items[Math.floor(random() * items.length)];
  const screenNames = Object.keys(SCREENS);

  return Array.from({ length: count }, () => {
    const screen = pick(screenNames);
    const { oses, sorts } = SCREENS[screen];

    return {
      screen,
      specs: oses.map((os) => pick(SPECS[os])),
      sort: pick(sorts),
      // 페이지 사이의 스크롤 간격, 1~5초
      waits: PAGES.slice(1).map(() => Math.round((1 + random() * 4) * 10) / 10),
    };
  });
}

// 처음부터 count개를 만든다. 앞부분은 count와 관계없이 같다
export function sessionList(count) {
  return new SharedArray(`product list sessions ${count}`, () => buildSessions(count));
}

function requestOf(session, spec, page) {
  return {
    query: { ...spec, sort: session.sort, page, size: PAGE_SIZE },
    tags: {
      screen: session.screen,
      os: spec.os,
      cpu_tier: spec.cpuTier,
      memory_gb: String(spec.memoryGb),
      storage_gb: String(spec.storageGb),
      page: String(page),
      sort: session.sort,
    },
  };
}

// 앞 응답을 받은 뒤 다음 페이지를 요청한다. 둘 다 화면은 페이지마다 OS별 목록을 동시에 요청한다.
// 실제 사용자처럼 다음 페이지가 없으면 그 목록은 멈춘다
export function runSession(session) {
  let specs = session.specs;

  for (const [i, page] of PAGES.entries()) {
    if (i > 0) {
      sleep(session.waits[i - 1]);
    }

    const responses = getProductsAtOnce(specs.map((spec) => requestOf(session, spec, page)));

    if (i === PAGES.length - 1) {
      return;
    }

    // 다음 페이지가 있다고 답한 목록만 이어 간다. 200이 아닌 목록은 사용자도 더 스크롤하지 못하므로 멈춘다.
    // 테스트 데이터는 조합마다 마지막 페이지보다 많아야 하므로, 검사에 걸리면 데이터가 기준과 달라진 것이다
    specs = specs.filter((_, j) => responses[j].status === 200 && check(responses[j], {
      '마지막 페이지 전에는 다음 페이지가 있다': (res) => jsonBody(res)?.hasNext === true,
    }));

    if (specs.length === 0) {
      return;
    }
  }
}
