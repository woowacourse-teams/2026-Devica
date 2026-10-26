// 세션: 사용자 한 명이 목록 화면 하나를 스크롤하는 과정
import { SharedArray } from 'k6/data';
import { check, sleep } from 'k6';
import { jsonBody } from '../common/http.js';
import { getProducts } from './requests.js';

// 프론트엔드의 PAGE_SIZE(frontend/src/products.ts)와 같다
const PAGE_SIZE = 10;
const PAGES = [0, 1, 2];
const SEED = 199;

// 추천(BackendDevelopmentRecommender)이 낼 수 있는 사양 조합
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

// 맞춤 : 전체 = 1 : 1. 맞춤 목록은 맥·윈도우 탭 중 하나를 1 : 1로 보고, 그 탭의 권장 사양이 필터가 된다.
// 전체 목록은 진입 직후처럼 필터 없이 본다. 두 비율과 전체 목록의 필터는 근거 없이 정한 가정이다(README의 가정 표)
const SCREENS = {
  MATCHED: { tabs: [SPECS.MAC, SPECS.WINDOWS], sorts: ['RECOMMENDED', 'PRICE_ASC', 'PRICE_DESC'] },
  ALL: { tabs: [[{}]], sorts: ['PRICE_ASC', 'PRICE_DESC'] },
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
    const { tabs, sorts } = SCREENS[screen];

    return {
      screen,
      spec: pick(pick(tabs)),
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

function tagsOf({ screen, spec, sort }, page) {
  const tags = { screen, page: String(page), sort };

  if (spec.os === undefined) {
    return tags;
  }
  return {
    ...tags,
    os: spec.os,
    cpu_tier: spec.cpuTier,
    memory_gb: String(spec.memoryGb),
    storage_gb: String(spec.storageGb),
  };
}

// 앞 응답을 받은 뒤 다음 페이지를 요청한다. 실제 사용자처럼 다음 페이지가 없으면 멈춘다
export function runSession(session) {
  for (const [i, page] of PAGES.entries()) {
    if (i > 0) {
      sleep(session.waits[i - 1]);
    }

    const response = getProducts(
      { ...session.spec, sort: session.sort, page, size: PAGE_SIZE },
      tagsOf(session, page),
    );

    if (i === PAGES.length - 1) {
      return;
    }

    // 200이 아니면 사용자도 더 스크롤하지 못하므로 멈춘다.
    // 테스트 데이터는 조건마다 마지막 페이지보다 많아야 하므로, 검사에 걸리면 데이터가 기준과 달라진 것이다
    const hasNext = response.status === 200 && check(response, {
      '마지막 페이지 전에는 다음 페이지가 있다': (res) => jsonBody(res)?.hasNext === true,
    });

    if (!hasNext) {
      return;
    }
  }
}
