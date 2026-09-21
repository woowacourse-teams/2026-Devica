// 제품 목록은 서버가 확정 사양을 조건으로 걸러 준다. 프런트가 사양을 다시 대조하지 않는다.
import { osValueOf, type Spec } from './recommendation';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

// 원본 목록은 한 화면에 전부 담고 페이지를 나누지 않는다. 100 은 서버가 받는 상한이다.
const PAGE_SIZE = 100;

// 권장 사양의 항목 코드를 제품 검색 조건 이름으로 옮긴다.
// 권장 CPU 는 REQUIRED_CPU 로 등급 코드가 실려 오고, 검색은 그 등급 코드를 그대로 받는다.
const CONDITION_OF: Record<string, string> = {
  OS: 'os',
  REQUIRED_CPU: 'cpuTier',
  MEMORY: 'memoryGb',
  STORAGE: 'storageGb',
};

/**
 * 서버 CpuTier 의 사본이다. 등급 목록을 주는 API 가 없어 여기 적어 둔다.
 * 순서는 서버의 minScore 오름차순이고, 서버가 등급이나 점수대를 바꾸면 여기도 함께 고쳐야 한다.
 */
export const CPU_TIERS: Record<string, string[]> = {
  MAC: ['BASIC', 'PRO', 'MAX'],
  WINDOWS: ['U', 'P_HS', 'H', 'HX'],
};

const CPU_TIER_LABELS: Record<string, Record<string, string>> = {
  MAC: { BASIC: 'M 칩', PRO: 'M Pro 칩', MAX: 'M Max 칩' },
  WINDOWS: {
    U: '저전력 Core Ultra 5 / Ryzen 5',
    P_HS: '고효율 Core Ultra 7 / Ryzen 7',
    H: '고성능 Core Ultra 7 / Ryzen 7',
    HX: '최고성능 Core Ultra 9 / Ryzen 9',
  },
};

export type SortType = 'RECOMMENDED' | 'PRICE_ASC' | 'PRICE_DESC';

export type SearchCondition = {
  // 서버가 브랜드와 제품명을 함께 훑는다.
  keyword: string | null;
  maxPrice: number | null;
  // OS 와 CPU 등급은 따로 걸고 따로 지운다. 등급만 고르면 OS 는 등급에서 따라온다.
  os: string | null;
  cpuTier: string | null;
  brand: string | null;
  memoryGb: number | null;
  storageGb: number | null;
};

export const EMPTY_CONDITION: SearchCondition = {
  keyword: null,
  maxPrice: null,
  os: null,
  cpuTier: null,
  brand: null,
  memoryGb: null,
  storageGb: null,
};

// 등급 코드는 OS 마다 겹치지 않아 등급 하나로 OS 를 되짚을 수 있다.
export function osOfTier(tier: string): string | null {
  const found = Object.entries(CPU_TIERS).find(([, tiers]) => tiers.includes(tier));
  return found === undefined ? null : found[0];
}

export type ProductSpecItem = {
  code: string;
  displayName: string;
  value: string;
  displayValue: string;
};

export type Product = {
  id: number;
  brand: string;
  name: string;
  // 판매 중인 판매처가 없으면 비어 온다.
  minPrice: number | null;
  specs: ProductSpecItem[];
};

export type Offer = {
  name: string;
  price: number;
  purchaseUrl: string;
};

export type ProductDetail = {
  id: number;
  brand: string;
  name: string;
  code: string;
  // 설명이 없는 제품이 있다.
  description: string | null;
  specs: ProductSpecItem[];
  // 판매 중인 판매처가 없으면 비어 온다.
  offers: Offer[];
};

type ProductListResponse = {
  content: Product[];
  page: number;
  size: number;
  hasNext: boolean;
};

/**
 * 권장안이 둘이면 OS 마다 따로 받아 합친다 — 목록 API 가 OS 를 하나만 받는다.
 * 제품은 OS 를 하나만 가지므로 합쳐도 겹치지 않는다.
 * 권장안을 주지 않으면 사양 조건 없이 전체를 받는다.
 */
export async function fetchProductsFor(
  purposeCode: string,
  specs: Spec[],
  sort: SortType,
  condition: SearchCondition,
): Promise<Product[]> {
  if (specs.length === 0) {
    return fetchProducts(purposeCode, null, sort, condition);
  }

  // OS 를 골랐으면 그 OS 의 권장안만 대상이 된다. 등급만 골라도 OS 가 정해진다.
  const chosenOs = condition.os ?? (condition.cpuTier === null ? null : osOfTier(condition.cpuTier));
  const targets = chosenOs === null ? specs : specs.filter((spec) => osValueOf(spec) === chosenOs);

  const lists = await Promise.all(targets.map((spec) => fetchProducts(purposeCode, spec, sort, condition)));
  if (targets.length <= 1) {
    return lists.flat();
  }
  // 서버 정렬은 한 호출 안에서만 유효하다. 합친 목록은 가격으로만 다시 줄 세울 수 있다 —
  // 추천순의 기준인 CPU 점수가 응답에 없다. 그래서 화면이 둘일 때 추천순을 고르지 못하게 한다.
  return lists.flat().sort(sort === 'PRICE_DESC' ? byPriceDesc : byPriceAsc);
}

async function fetchProducts(
  purposeCode: string,
  spec: Spec | null,
  sort: SortType,
  condition: SearchCondition,
): Promise<Product[]> {
  const query = toQuery(spec, sort, condition);
  const response = await fetch(`${BASE_URL}/api/usage-purposes/${purposeCode}/products?${query}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`제품 목록을 불러오지 못했습니다 (${response.status})`);
  }

  const body: ProductListResponse = await response.json();
  return body.content;
}

export async function fetchProduct(id: number): Promise<ProductDetail> {
  const response = await fetch(`${BASE_URL}/api/products/${id}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`제품 정보를 불러오지 못했습니다 (${response.status})`);
  }
  return response.json();
}

function toQuery(spec: Spec | null, sort: SortType, condition: SearchCondition): URLSearchParams {
  const query = new URLSearchParams();
  spec?.items.forEach((item) => {
    const name = CONDITION_OF[item.code];
    if (name !== undefined && item.value !== '') {
      query.set(name, item.value);
    }
  });

  // 검색 조건은 권장 사양 위에 겹쳐 걸린다. 서버의 사양 조건이 모두 "이상"이라
  // 높은 쪽만 남기면 권장 사양과 검색 조건을 함께 만족한다.
  if (condition.os !== null) {
    query.set('os', condition.os);
  }
  if (condition.cpuTier !== null) {
    // 등급은 OS 마다 따로 매겨진다. 권장 등급과 견주려면 어느 OS 의 등급인지 알아야 한다.
    query.set('cpuTier', higherTier(query.get('os') ?? osOfTier(condition.cpuTier), query.get('cpuTier'), condition.cpuTier));
  }
  if (condition.memoryGb !== null) {
    query.set('memoryGb', String(atLeast(query.get('memoryGb'), condition.memoryGb)));
  }
  if (condition.storageGb !== null) {
    query.set('storageGb', String(atLeast(query.get('storageGb'), condition.storageGb)));
  }
  if (condition.maxPrice !== null) {
    query.set('maxPrice', String(condition.maxPrice));
  }
  if (condition.keyword !== null) {
    query.set('keyword', condition.keyword);
  }
  if (condition.brand !== null) {
    query.set('brand', condition.brand);
  }

  query.set('sort', sort);
  query.set('size', String(PAGE_SIZE));
  return query;
}

function higherTier(os: string | null, recommended: string | null, chosen: string): string {
  if (recommended === null || os === null) {
    return chosen;
  }
  const tiers = CPU_TIERS[os] ?? [];
  return tiers.indexOf(chosen) > tiers.indexOf(recommended) ? chosen : recommended;
}

function atLeast(recommended: string | null, chosen: number): number {
  return Math.max(Number(recommended ?? 0), chosen);
}

// Windows 등급 라벨은 "A / B급"처럼 두 계열을 함께 적어 좁은 드롭다운에서 잘린다. 필터에서는 앞쪽 하나만 쓴다.
export function cpuFilterLabel(os: string, tier: string): string {
  const label = CPU_TIER_LABELS[os]?.[tier] ?? tier;
  return label.includes(' / ') ? `${label.split(' / ')[0]}급 이상` : `${label} 이상`;
}

// 최저가가 없는 제품은 가격을 비교할 수 없어 뒤로 보낸다.
function byPriceAsc(one: Product, other: Product): number {
  if (one.minPrice === null || other.minPrice === null) {
    return unpricedLast(one, other);
  }
  return one.minPrice - other.minPrice;
}

function byPriceDesc(one: Product, other: Product): number {
  if (one.minPrice === null || other.minPrice === null) {
    return unpricedLast(one, other);
  }
  return other.minPrice - one.minPrice;
}

function unpricedLast(one: Product, other: Product): number {
  if (one.minPrice === other.minPrice) {
    return 0;
  }
  return one.minPrice === null ? 1 : -1;
}

// 판매 중인 판매처가 없으면 최저가가 비어 온다. 원본에는 없던 상태다.
export function formatPrice(value: number | null): string {
  return value === null ? '가격 정보 없음' : `${value.toLocaleString('ko-KR')}원`;
}
