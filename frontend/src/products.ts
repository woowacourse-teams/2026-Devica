// 제품 목록은 서버가 확정 사양을 조건으로 걸러 준다. 프런트가 사양을 다시 대조하지 않는다.
import type { Properties } from './analytics';
import type { Page } from './fetched';
import { osValueOf, type Spec } from './recommendation';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

// 한 화면에 데스크톱은 4~5개, 모바일은 1~2개가 보인다. 한 페이지가 한 화면보다 길어야
// 들어오자마자 목록 끝이 보여 다음 페이지를 곧바로 또 받는 일이 없다.
const PAGE_SIZE = 10;

// OS 는 서버 Os 와 같다. OS 가 늘면 권장안·검색이 모두 바뀌므로 여기도 함께 고친다.
export const OSES = ['MAC', 'WINDOWS'];

// 순서가 곧 등급 순서다. OS 별로 낮은 등급부터 온다.
export type CpuTier = {
  code: string;
  name: string;
  os: string;
};

// 추천 알고리즘이 내는 값이다. 알고리즘이 바뀌면 여기도 함께 바뀐다.
export const MEMORY_STEPS = [16, 24, 32, 48];
export const STORAGE_STEPS = [256, 512, 1024];

export type SortType = 'RECOMMENDED' | 'PRICE_ASC' | 'PRICE_DESC';

export type SearchCondition = {
  // 서버가 브랜드와 제품명을 함께 훑는다.
  keyword: string | null;
  maxPrice: number | null;
  // OS 와 CPU 등급은 따로 걸고 따로 지운다. 등급은 OS 마다 따로 매겨진다.
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
export function osOfTier(tiers: CpuTier[], code: string): string | null {
  return tiers.find((tier) => tier.code === code)?.os ?? null;
}

// 주소는 누구나 고칠 수 있다. 모르는 등급이나 고른 OS 의 등급이 아닌 것은 없는 것으로 본다.
export function validTier(tiers: CpuTier[], condition: SearchCondition): string | null {
  const os = condition.cpuTier === null ? null : osOfTier(tiers, condition.cpuTier);
  return os !== null && (condition.os === null || os === condition.os) ? condition.cpuTier : null;
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
  imageUrl: string | null;
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
  imageUrl: string | null;
  // 설명이 없는 제품이 있다.
  description: string | null;
  specs: ProductSpecItem[];
  // 판매 중인 판매처가 없으면 비어 온다.
  offers: Offer[];
};

export async function fetchProductPage(
  categoryCode: string,
  listQuery: URLSearchParams,
  page: number,
): Promise<Page<Product>> {
  const query = new URLSearchParams(listQuery);
  query.set('page', String(page));
  query.set('size', String(PAGE_SIZE));
  const response = await fetch(`${BASE_URL}/api/product-categories/${categoryCode}/products?${query}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`제품 목록을 불러오지 못했습니다 (${response.status})`);
  }
  return response.json();
}

/** 등급을 서버에서 받는다. 등급이나 표시명을 바꿔도 화면을 고치지 않는다. */
export async function fetchCpuTiers(): Promise<CpuTier[]> {
  const response = await fetch(`${BASE_URL}/api/cpu-tiers`, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`CPU 등급을 불러오지 못했습니다 (${response.status})`);
  }
  return response.json();
}

/** OS 를 주면 그 OS 의 브랜드만 온다. */
export async function fetchBrands(categoryCode: string, os: string | null): Promise<string[]> {
  const suffix = os === null ? '' : `?os=${os}`;
  const response = await fetch(`${BASE_URL}/api/product-categories/${categoryCode}/brands${suffix}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`브랜드를 불러오지 못했습니다 (${response.status})`);
  }
  return response.json();
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

/**
 * 권장 사양을 검색 조건으로 옮긴다. 맞춤 목록은 이 조건을 채운 채로 열고, 사용자는 필터로 이어서 고친다.
 * 권장 사양을 따로 깔지 않아 전체 목록과 같은 규칙으로 걸린다. 고른 값 그대로라 권장보다 낮게도 찾는다.
 */
export function specCondition(spec: Spec): Pick<SearchCondition, 'os' | 'cpuTier' | 'memoryGb' | 'storageGb'> {
  const itemValue = (code: string) => spec.items.find((item) => item.code === code)?.value || null;
  const memory = itemValue('MEMORY');
  const storage = itemValue('STORAGE');
  return {
    os: itemValue('OS'),
    cpuTier: itemValue('REQUIRED_CPU'),
    memoryGb: memory === null ? null : Number(memory),
    storageGb: storage === null ? null : Number(storage),
  };
}

/** 거르기와 정렬은 서버가 한다. 같은 조건이면 같은 목록이고, 페이지만 달리해 이어 받는다. */
export function productListQuery(sort: SortType, condition: SearchCondition): URLSearchParams {
  const query = new URLSearchParams();
  if (condition.os !== null) {
    query.set('os', condition.os);
  }
  if (condition.cpuTier !== null) {
    query.set('cpuTier', condition.cpuTier);
  }
  if (condition.memoryGb !== null) {
    query.set('memoryGb', String(condition.memoryGb));
  }
  if (condition.storageGb !== null) {
    query.set('storageGb', String(condition.storageGb));
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
  return query;
}

// Windows 등급 라벨은 "A / B급"처럼 두 계열을 함께 적어 좁은 드롭다운에서 잘린다. 필터에서는 앞쪽 하나만 쓴다.
export function cpuFilterLabel(tier: CpuTier): string {
  const label = tier.name;
  return label.includes(' / ') ? `${label.split(' / ')[0]}급 이상` : `${label} 이상`;
}

// 판매 중인 판매처가 없으면 최저가가 비어 온다. 원본에는 없던 상태다.
export function formatPrice(value: number | null): string {
  return value === null ? '가격 정보 없음' : `${value.toLocaleString('ko-KR')}원`;
}

export function formatStorage(value: number): string {
  return value >= 1024 ? `${value / 1024}TB` : `${value}GB`;
}

/**
 * 결과 화면에서 사용자가 고친 권장 사양이다. OS 마다 항목 코드 → 고른 값이다.
 * 맞춤 목록은 고친 사양으로 검색 조건을 채운다(specCondition).
 */
export type SpecEdits = Record<string, Record<string, string>>;

// 고칠 수 있는 항목과 이벤트 속성 이름이다. 속성 이름은 제품 추천 개선 루프가 읽으므로 바꾸면 함께 고친다.
export const EDITABLE_ITEMS: { code: string; property: string }[] = [
  { code: 'REQUIRED_CPU', property: 'cpu_tier' },
  { code: 'MEMORY', property: 'memory_gb' },
  { code: 'STORAGE', property: 'storage_gb' },
];

export type EditChoice = { value: string; label: string };

/** 고를 수 있는 값은 목록 필터의 선택지와 같다. 등급을 아직 받지 못했으면 CPU 는 고를 수 없다. */
export function editChoices(code: string, os: string, tiers: CpuTier[] | null): EditChoice[] {
  if (code === 'REQUIRED_CPU') {
    return (tiers ?? []).filter((tier) => tier.os === os).map((tier) => ({ value: tier.code, label: tier.name }));
  }
  if (code === 'MEMORY') {
    return MEMORY_STEPS.map((memory) => ({ value: String(memory), label: `${memory}GB` }));
  }
  if (code === 'STORAGE') {
    return STORAGE_STEPS.map((storage) => ({ value: String(storage), label: formatStorage(storage) }));
  }
  return [];
}

/** 고친 값을 권장 사양에 덮는다. 주소는 누구나 고칠 수 있어, 고를 수 없는 값이나 권장 값과 같은 값은 버린다. */
export function editedSpec(spec: Spec, edits: SpecEdits, tiers: CpuTier[] | null): Spec {
  const os = osValueOf(spec);
  return {
    ...spec,
    items: spec.items.map((item) => {
      const value = edits[os]?.[item.code];
      // 등급을 받기 전이거나 받지 못했으면 고친 CPU 를 확인할 수 없다. 버리면 권장 등급으로 바뀌어 보이니 코드 그대로 둔다.
      // 목록은 등급을 받은 뒤 모르는 등급을 가리고(validTier), 받지 못하면 그 실패를 알린다.
      if (item.code === 'REQUIRED_CPU' && tiers === null && value !== undefined && value !== item.value) {
        return { ...item, value, displayValue: value, reasons: [] };
      }
      const choice = editChoices(item.code, os, tiers).find((each) => each.value === value);
      // 근거는 권장 값의 것이라 고친 값에는 붙이지 않는다.
      return choice === undefined || choice.value === item.value
        ? item
        : { ...item, value: choice.value, displayValue: choice.label, reasons: [] };
    }),
  };
}

/** 고친 항목이 있으면 이벤트 속성을, 없으면 null 을 준다. 고치지 않은 항목의 고친 값은 null 이다. */
export function editProperties(spec: Spec, edited: Spec): Properties | null {
  const itemValue = (target: Spec, code: string) => target.items.find((item) => item.code === code)?.value ?? null;
  const properties: Properties = { os: osValueOf(spec) };
  let changed = false;
  for (const { code, property } of EDITABLE_ITEMS) {
    const before = itemValue(spec, code);
    const after = itemValue(edited, code);
    properties[`${property}_recommended`] = before;
    properties[`${property}_edited`] = after === before ? null : after;
    changed ||= after !== before;
  }
  return changed ? properties : null;
}

/**
 * 검색 중 화면에 보여 줄 조건이다. 실제로 요청하는 검색 조건으로 만들어, 제품 추천 결과를 고쳤으면 고친 값이 보인다.
 * CPU 라벨 안에 "/" 가 들어가는 경우가 있어("… 258V / … 445급") 구분자는 "·" 를 쓴다.
 */
export function searchingLine(
  { os, cpuTier, memoryGb, storageGb }: SearchCondition,
  tiers: CpuTier[] | null,
  spec: Spec | undefined,
  osLabels: Record<string, string>,
): string {
  // 등급을 아직 받지 못했으면 제품 추천 결과의 이름을 빌리고, 그것과도 다르면 코드를 그대로 쓴다.
  const recommended = spec?.items.find((item) => item.code === 'REQUIRED_CPU');
  const cpu =
    tiers?.find((tier) => tier.code === cpuTier)?.name ??
    (recommended?.value === cpuTier ? recommended?.displayValue : cpuTier);
  return [
    cpu,
    memoryGb === null ? null : `${memoryGb}GB`,
    storageGb === null ? null : formatStorage(storageGb),
    os === null ? null : (spec?.items.find((item) => item.code === 'OS')?.displayValue ?? osLabels[os] ?? os),
  ]
    .filter((text) => text !== null && text !== undefined)
    .join(' · ');
}
