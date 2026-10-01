import { navigate } from 'wouter/use-browser-location';
import { CPU_TIERS, EMPTY_CONDITION, osOfTier, type SearchCondition, type SortType } from './products';
import type { Answers } from './questions';
import type { ResultOs } from './ResultView';

// 화면마다 주소를 둔다(ADR 0030). 화면은 경로로, 화면이 쓰는 값은 쿼리로 나타낸다.
export const PATHS = {
  home: '/',
  category: '/category',
  purpose: '/purpose',
  intro: '/intro',
  question: '/questions/:step',
  result: '/result',
  matched: '/result/products',
  products: '/products',
  detail: '/products/:id',
  faq: '/faq',
  board: '/board',
  boardWrite: '/board/new',
  boardPost: '/board/posts/:id',
} as const;

export type Screen = keyof typeof PATHS;

const SCREEN_NAMES: Record<Screen, string> = {
  home: '처음',
  category: '제품 선택',
  purpose: '사용 목적 선택',
  intro: '기본 권장 사양',
  question: '질문',
  result: '권장 사양',
  matched: '맞춤 목록',
  products: '제품 목록',
  detail: '제품 상세',
  faq: '자주 묻는 질문',
  board: '질문 게시판',
  boardWrite: '질문 작성',
  boardPost: '질문 상세',
};

// 조사가 이름마다 달라 "← ○○으로" 를 만들지 않고 적어 둔다.
const BACK_LABELS: Record<Screen, string> = {
  home: '← 처음으로',
  category: '← 제품 선택으로',
  purpose: '← 사용 목적 선택으로',
  intro: '← 기본 권장 사양으로',
  question: '← 질문으로',
  result: '← 권장 사양으로',
  matched: '← 맞춤 목록으로',
  products: '← 제품 목록으로',
  detail: '← 제품 상세로',
  faq: '← 자주 묻는 질문으로',
  board: '← 질문 게시판으로',
  boardWrite: '← 질문 작성으로',
  boardPost: '← 질문 상세로',
};

export function screenOf(pathname: string): Screen | null {
  if (pathname === '/') {
    return 'home';
  }
  if (/^\/questions\/[^/]+$/.test(pathname)) {
    return 'question';
  }
  if (/^\/products\/[^/]+$/.test(pathname)) {
    return 'detail';
  }
  if (/^\/board\/posts\/[^/]+$/.test(pathname)) {
    return 'boardPost';
  }
  const found = (Object.keys(PATHS) as Screen[]).find((screen) => PATHS[screen] === pathname);
  return found ?? null;
}

export function titleOf(pathname: string): string {
  const screen = screenOf(pathname);
  return screen === null || screen === 'home'
    ? 'Devica — 백엔드 개발용 노트북 사양'
    : `${SCREEN_NAMES[screen]} — Devica`;
}

export function backLabelOf(screen: Screen): string {
  return BACK_LABELS[screen];
}

/**
 * 방문 기록 한 칸에 붙여 두는 값이다. 주소에 넣지 않으므로 새로고침에는 남고 공유 링크로는 넘어가지 않는다.
 * - from: 이 화면에 들어오기 전 화면. 앱 밖에서 바로 들어왔으면 없다.
 * - searching: 권장 사양에서 "제품 검색"으로 막 넘어왔다. 사양 대조 화면을 한 번만 띄운다.
 * - filterOpen: 목록의 검색 조건 패널을 펼쳐 두었다. 상세를 다녀와도 그대로 둔다.
 * - scrollY: 이 화면을 떠날 때의 스크롤 위치. 뒤로 가기로 돌아오면 되살린다.
 */
export type NavState = {
  from?: Screen;
  searching?: boolean;
  filterOpen?: boolean;
  scrollY?: number;
};

export function currentNavState(): NavState {
  const state: unknown = window.history.state;
  return typeof state === 'object' && state !== null ? (state as NavState) : {};
}

/** 방문 기록을 한 칸 쌓는다. 떠나는 화면의 스크롤 위치를 적어 두고, 새 칸에는 어디서 왔는지 적는다. */
export function go(to: string, extra: Pick<NavState, 'searching'> = {}): void {
  window.history.replaceState({ ...currentNavState(), scrollY: window.scrollY }, '');
  const from = screenOf(window.location.pathname) ?? undefined;
  navigate(to, { state: { from, ...extra } });
}

/** 기록을 쌓지 않고 주소만 바꾼다. 어디서 왔는지는 그대로 둔다. */
export function replace(to: string, state: NavState = currentNavState()): void {
  navigate(to, { replace: true, state });
}

/**
 * 화면 안의 뒤로 버튼. 앱 안에서 들어왔다면 브라우저 뒤로 가기와 같은 곳으로 간다.
 * expected 를 주면 그 화면에서 왔을 때만 기록을 되돌린다. 아니면 정해 둔 화면으로 간다.
 */
export function goBack(fallback: string, expected?: Screen): void {
  const { from } = currentNavState();
  if (from !== undefined && (expected === undefined || from === expected)) {
    window.history.back();
    return;
  }
  go(fallback);
}

// 쿼리 문자열을 만든다. 값이 없는 항목은 뺀다.
export function query(entries: [string, string | number | null | undefined][]): string {
  const params = new URLSearchParams();
  for (const [key, value] of entries) {
    if (value !== null && value !== undefined && value !== '') {
      params.append(key, String(value));
    }
  }
  const text = params.toString();
  return text === '' ? '' : `?${text}`;
}

export type Flow = {
  category: string | null;
  purpose: string | null;
};

export function flowOf(params: URLSearchParams): Flow {
  return { category: params.get('category'), purpose: params.get('purpose') };
}

export function flowEntries(flow: Flow): [string, string | null][] {
  return [
    ['category', flow.category],
    ['purpose', flow.purpose],
  ];
}

export function boardUrl(category: string | null = null, purpose: string | null = null, page = 0): string {
  return `${PATHS.board}${query([...flowEntries({ category, purpose }), ['page', page === 0 ? null : page]])}`;
}

// 각 화면이 실제로 쓰는 값만 이어받는다. 일반 제품 목록의 목적이나 홈·FAQ에 남은 쿼리는 무시한다.
export function boardEntryUrl(pathname: string, params: URLSearchParams): string {
  const screen = screenOf(pathname);
  if (screen === 'products' || screen === 'purpose') {
    return boardUrl(params.get('category'));
  }
  if (['intro', 'question', 'result', 'matched', 'board', 'boardWrite'].includes(screen ?? '')) {
    const { category, purpose } = flowOf(params);
    return boardUrl(category, category === null ? null : purpose);
  }
  return PATHS.board;
}

// 답은 "질문코드.선택지코드" 로 이어 answer 에 반복해 담는다. 목록 검색 조건과 이름이 겹치지 않는다.
export function answersOf(params: URLSearchParams): Answers {
  const answers: Answers = {};
  for (const pair of params.getAll('answer')) {
    const dot = pair.indexOf('.');
    if (dot > 0) {
      const code = pair.slice(0, dot);
      answers[code] = [...(answers[code] ?? []), pair.slice(dot + 1)];
    }
  }
  return answers;
}

export function answerEntries(answers: Answers): [string, string][] {
  return Object.entries(answers).flatMap(([code, values]) =>
    values.map((value): [string, string] => ['answer', `${code}.${value}`]),
  );
}

const RESULT_OS: ResultOs[] = ['MAC', 'WINDOWS', 'BOTH'];

// 주소는 누구나 고칠 수 있다. 모르는 값은 없는 것으로 본다.
export function specOsOf(params: URLSearchParams): ResultOs | null {
  const value = params.get('specOs');
  return RESULT_OS.find((os) => os === value) ?? null;
}

const SORTS: SortType[] = ['RECOMMENDED', 'PRICE_ASC', 'PRICE_DESC'];

export function sortOf(params: URLSearchParams): SortType | null {
  const value = params.get('sort');
  return SORTS.find((sort) => sort === value) ?? null;
}

function positive(value: string | null): number | null {
  const number = Number(value);
  return value !== null && Number.isInteger(number) && number > 0 ? number : null;
}

// 등급은 OS 마다 따로 매겨진다. 모르는 등급이나 고른 OS 의 등급이 아닌 것은 버린다.
function osAndTierOf(params: URLSearchParams): Pick<SearchCondition, 'os' | 'cpuTier'> {
  const rawOs = params.get('os');
  const os = rawOs !== null && Object.hasOwn(CPU_TIERS, rawOs) ? rawOs : null;
  const rawTier = params.get('cpuTier');
  const tierOs = rawTier === null ? null : osOfTier(rawTier);
  const cpuTier = tierOs !== null && (os === null || tierOs === os) ? rawTier : null;
  return { os, cpuTier };
}

export function conditionOf(params: URLSearchParams): SearchCondition {
  return {
    keyword: params.get('keyword'),
    maxPrice: positive(params.get('maxPrice')),
    ...osAndTierOf(params),
    brand: params.get('brand'),
    memoryGb: positive(params.get('memoryGb')),
    storageGb: positive(params.get('storageGb')),
  };
}

export function listEntries(sort: SortType | null, condition: SearchCondition): [string, string | number | null][] {
  return [
    ['sort', sort],
    ...(Object.keys(EMPTY_CONDITION) as (keyof SearchCondition)[]).map((key): [string, string | number | null] => [
      key,
      condition[key],
    ]),
  ];
}

export function positiveOf(value: string | undefined): number | null {
  return positive(value ?? null);
}
