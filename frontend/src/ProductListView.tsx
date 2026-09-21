import { useEffect, useState } from 'react';
import { osValueOf, type Spec } from './recommendation';
import {
  CPU_TIERS,
  EMPTY_CONDITION,
  cpuFilterLabel,
  fetchProductsFor,
  formatPrice,
  osOfTier,
  type Product,
  type ProductSpecItem,
  type SearchCondition,
  type SortType,
} from './products';
import './ProductListView.css';

// 글자마다 조회하지 않도록 입력이 멈추길 기다린다.
const KEYWORD_DELAY_MS = 300;

// 원본은 제품 가격 분포와 무관하게 눈에 익은 눈금을 쓴다. 후보가 없는 눈금은 감춘다.
const PRICE_STEPS = [1500000, 2000000, 2500000, 3000000, 4000000, 5000000];

const SORT_LABELS: { value: SortType; label: string }[] = [
  { value: 'RECOMMENDED', label: '추천순' },
  { value: 'PRICE_ASC', label: '가격 낮은 순' },
  { value: 'PRICE_DESC', label: '가격 높은 순' },
];

// 카드 사양은 3행만 쓴다. OS 는 브랜드 줄에 붙인다.
const CARD_SPECS: { code: string; label: string }[] = [
  { code: 'CPU', label: 'PROCESSOR' },
  { code: 'MEMORY', label: 'MEMORY' },
  { code: 'STORAGE', label: 'STORAGE' },
];

const OS_GROUP_LABELS: Record<string, string> = { MAC: '맥', WINDOWS: '윈도우' };

export type ListMode = 'MATCHED' | 'ALL';

// eyebrow 와 제목이 같은 말이 되지 않게 나눈다. 제목이 목록의 정체를, eyebrow 가 상위 분류를 맡는다.
const HEADINGS: Record<ListMode, { eyebrow: string; title: string; description: string }> = {
  MATCHED: {
    eyebrow: '조건에 맞는 제품',
    title: '추천 노트북',
    description: '확정한 사양을 모두 충족하는 제품만 보여드려요.',
  },
  ALL: {
    eyebrow: '제품 목록',
    title: '전체 제품',
    description: '지금 비교할 수 있는 노트북 전체입니다.',
  },
};

export type ProductListState = {
  sort: SortType;
  condition: SearchCondition;
  filterOpen: boolean;
};

/**
 * 권장안이 둘이면 두 목록을 합쳐야 해서 서버 추천순을 이어갈 수 없다.
 * 원본도 견줄 기준이 없으면 추천순을 감추고 가격순으로 연다.
 */
export function initialListState(specs: Spec[]): ProductListState {
  return {
    sort: specs.length === 1 ? 'RECOMMENDED' : 'PRICE_ASC',
    condition: EMPTY_CONDITION,
    filterOpen: false,
  };
}

type Props = {
  purposeCode: string;
  mode: ListMode;
  // 결과 화면이 보여주고 있던 OS 의 권장안이다. 전체 목록에서는 비어 있다.
  specs: Spec[];
  backLabel: string;
  // 상세를 다녀와도 정렬과 검색 조건이 풀리지 않게 부모가 들고 있는다.
  state: ProductListState;
  onChangeState: (update: (previous: ProductListState) => ProductListState) => void;
  onBack: () => void;
  onDetail: (productId: number) => void;
  onShowAll: () => void;
};

export function ProductListView({
  purposeCode,
  mode,
  specs,
  backLabel,
  state,
  onChangeState,
  onBack,
  onDetail,
  onShowAll,
}: Props) {
  const { sort, condition, filterOpen } = state;
  const recommendable = specs.length === 1;
  // 필터 선택지와 "N개 중 M개" 는 조건을 걸기 전 목록을 알아야 만들 수 있다.
  // 아직 받지 못한 상태(null)와 0건을 구분한다. 응답 전에 "제품이 없습니다" 를 보이면 안 된다.
  const [base, setBase] = useState<Product[] | null>(null);
  const [matched, setMatched] = useState<Product[] | null>(null);
  const [failed, setFailed] = useState(false);

  const [typed, setTyped] = useState(condition.keyword ?? '');

  // "검색 조건 지우기" 로 조건이 비면 입력칸도 함께 비운다.
  // 입력 중에는 되돌리지 않는다. trim 된 값을 다시 넣으면 치던 공백이 사라진다.
  useEffect(() => {
    if (condition.keyword === null) {
      setTyped('');
    }
  }, [condition.keyword]);

  useEffect(() => {
    const next = typed.trim() === '' ? null : typed.trim();
    if (next === condition.keyword) {
      return;
    }
    const timer = setTimeout(() => change('keyword', next), KEYWORD_DELAY_MS);
    return () => clearTimeout(timer);
  }, [typed]);

  // specs 는 부모가 매번 새로 만드는 배열이라 참조로는 비교할 수 없다. OS 조합으로 본다.
  const specKey = specs.map(osValueOf).join(',');

  // 조건을 빠르게 바꾸면 요청이 겹친다. 늦게 온 옛 응답이 새 조건의 목록을 덮지 않게 막는다.
  useEffect(() => {
    let stale = false;
    fetchProductsFor(purposeCode, specs, 'PRICE_ASC', EMPTY_CONDITION)
      .then((products) => !stale && setBase(products))
      .catch(() => !stale && setFailed(true));
    return () => {
      stale = true;
    };
  }, [purposeCode, specKey]);

  useEffect(() => {
    let stale = false;
    setFailed(false);
    fetchProductsFor(purposeCode, specs, sort, condition)
      .then((products) => !stale && setMatched(products))
      .catch(() => !stale && setFailed(true));
    return () => {
      stale = true;
    };
  }, [purposeCode, specKey, sort, condition]);

  const heading = HEADINGS[mode];
  const options = buildFilterOptions(base ?? [], specs, condition.os);
  const hasCondition = Object.values(condition).some((value) => value !== null);
  const loading = base === null || matched === null;

  const change = (field: keyof SearchCondition, value: SearchCondition[keyof SearchCondition]) => {
    onChangeState((previous) => ({ ...previous, condition: { ...previous.condition, [field]: value } }));
  };

  // 등급은 OS 마다 따로 매겨진다. OS 를 바꾸면 그 OS 의 등급이 아닌 조건은 함께 지운다.
  const changeOs = (os: string | null) => {
    onChangeState((previous) => {
      const tier = previous.condition.cpuTier;
      const kept = tier !== null && (os === null || osOfTier(tier) === os) ? tier : null;
      return { ...previous, condition: { ...previous.condition, os, cpuTier: kept } };
    });
  };

  return (
    <section className="view-panel product-list-view" id="product-list-view" aria-labelledby="product-list-title">
      <button className="text-button view-back-button" id="product-list-back-button" type="button" onClick={onBack}>
        {backLabel}
      </button>
      <div className="product-list-heading">
        <div>
          <p className="eyebrow" id="product-list-eyebrow">{heading.eyebrow}</p>
          <h2 id="product-list-title">{heading.title}</h2>
          <p className="section-description" id="product-list-description">{heading.description}</p>
        </div>
        <div className="product-list-controls">
          <button
            className="product-list-control product-filter-toggle"
            id="product-filter-toggle"
            type="button"
            aria-expanded={filterOpen}
            aria-controls="product-filter-panel"
            onClick={() => onChangeState((previous) => ({ ...previous, filterOpen: !previous.filterOpen }))}
          >
            검색 조건
          </button>
          <label className="product-list-control product-list-sort" htmlFor="product-sort">
            <span className="product-list-control__label">정렬</span>
            <select id="product-sort" value={sort} onChange={(event) => onChangeState((previous) => ({ ...previous, sort: event.target.value as SortType }))}>
              {SORT_LABELS.map(({ value, label }) => (
                <option value={value} key={value} hidden={value === 'RECOMMENDED' && !recommendable}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {/* 접어도 DOM 에 남긴다. 사라지면 aria-controls 가 없는 id 를 가리킨다. */}
      <div className="product-filter-panel" id="product-filter-panel" hidden={!filterOpen}>
          <label className="product-filter product-filter--wide" htmlFor="product-filter-keyword">
            <span className="product-filter__label">검색어</span>
            <input
              id="product-filter-keyword"
              type="search"
              value={typed}
              placeholder="브랜드 · 제품명"
              onChange={(event) => setTyped(event.target.value)}
            />
          </label>
          <Filter
            field="price"
            label="가격"
            value={condition.maxPrice === null ? '' : String(condition.maxPrice)}
            choices={options.maxPrice.map((price) => ({ value: String(price), label: `${formatPrice(price)} 이하` }))}
            onChange={(raw) => change('maxPrice', raw === '' ? null : Number(raw))}
          />
          <Filter
            field="os"
            label="OS"
            value={condition.os ?? ''}
            choices={options.os.map((os) => ({ value: os, label: OS_GROUP_LABELS[os] ?? os }))}
            onChange={(raw) => changeOs(raw === '' ? null : raw)}
          />
          <CpuFilter
            groups={options.cpu}
            value={condition.cpuTier ?? ''}
            onChange={(raw) => change('cpuTier', raw === '' ? null : raw)}
          />
          <Filter
            field="brand"
            label="브랜드"
            value={condition.brand ?? ''}
            choices={options.brand.map((brand) => ({ value: brand, label: brand }))}
            onChange={(raw) => change('brand', raw === '' ? null : raw)}
          />
          <Filter
            field="memory"
            label="RAM"
            value={condition.memoryGb === null ? '' : String(condition.memoryGb)}
            choices={options.memoryGb.map((memory) => ({ value: String(memory), label: `${memory}GB 이상` }))}
            onChange={(raw) => change('memoryGb', raw === '' ? null : Number(raw))}
          />
          <Filter
            field="storage"
            label="저장장치"
            value={condition.storageGb === null ? '' : String(condition.storageGb)}
            choices={options.storageGb.map((storage) => ({
              value: String(storage),
              label: `${formatStorage(storage)} 이상`,
            }))}
            onChange={(raw) => change('storageGb', raw === '' ? null : Number(raw))}
          />
      </div>

      {!failed && base !== null && matched !== null && (
        <p className="product-list-caption" id="product-list-caption">
          {matched.length === base.length ? `${base.length}개` : `${base.length}개 중 ${matched.length}개`}
        </p>
      )}

      <div className="product-list" id="product-list" aria-live="polite" aria-busy={loading}>
        {failed ? (
          <p className="notice" role="alert">제품 목록을 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.</p>
        ) : matched === null ? (
          <p className="product-list-status">제품을 불러오는 중입니다…</p>
        ) : matched.length === 0 ? (
          <EmptyResult
            condition={condition}
            hasCondition={hasCondition}
            showsAll={mode === 'ALL'}
            onReset={() => onChangeState((previous) => ({ ...previous, condition: EMPTY_CONDITION }))}
            onShowAll={onShowAll}
          />
        ) : (
          matched.map((product) => <ProductCard product={product} onDetail={onDetail} key={product.id}/>)
        )}
      </div>
    </section>
  );
}

function ProductCard({ product, onDetail }: { product: Product; onDetail: (productId: number) => void }) {
  return (
    <article className="bordered-panel product-card">
      {/* 제품 이미지 필드가 아직 응답에 없다. 틀만 두고 사진이 생기면 안을 채운다. */}
      <div className="product-card__image-frame"/>
      <div className="product-card__body">
        <div className="product-card__heading-row">
          <div>
            <span className="eyebrow product-card__maker">
              {product.brand} · {specValue(product, 'OS')}
            </span>
            <h3>{product.name}</h3>
          </div>
          <strong className="product-card__price">{formatPrice(product.minPrice)}</strong>
        </div>
        <dl className="product-card__specs">
          {CARD_SPECS.map(({ code, label }) => (
            <div className="product-spec-item" key={code}>
              <dt>{label}</dt>
              <dd>{specValue(product, code)}</dd>
            </div>
          ))}
        </dl>
      </div>
      <div className="product-card__action">
        <button
          className="button button--primary button--wide product-card__detail"
          type="button"
          onClick={() => onDetail(product.id)}
        >
          상세 보기
        </button>
      </div>
    </article>
  );
}

type EmptyResultProps = {
  condition: SearchCondition;
  hasCondition: boolean;
  showsAll: boolean;
  onReset: () => void;
  onShowAll: () => void;
};

function EmptyResult({ condition, hasCondition, showsAll, onReset, onShowAll }: EmptyResultProps) {
  if (hasCondition) {
    return (
      <div className="empty-result">
        <p>조건에 맞는 제품이 없습니다.</p>
        <p className="empty-result__condition">적용한 검색 조건 — {describeCondition(condition)}</p>
        <button className="button button--secondary" type="button" onClick={onReset}>검색 조건 지우기</button>
      </div>
    );
  }
  // 조건 없이 0건이면 사양이 높은 것이다. 목록을 넓혀 볼 길을 준다.
  return (
    <div className="empty-result">
      <p>조건에 맞는 제품이 없습니다. 사양을 직접 조정해 다시 확인해 주세요.</p>
      {!showsAll && (
        <button className="button button--secondary" type="button" onClick={onShowAll}>전체 제품 보기</button>
      )}
    </div>
  );
}

type Choice = { value: string; label: string };

type FilterProps = {
  field: string;
  label: string;
  value: string;
  choices: Choice[];
  onChange: (raw: string) => void;
};

function Filter({ field, label, value, choices, onChange }: FilterProps) {
  // 고를 값이 하나뿐이면 거는 의미가 없다. 흐리게만 두지 않고 왜 못 고르는지 적는다.
  const usable = choices.length > 1;
  return (
    <label className="product-filter" htmlFor={`product-filter-${field}`}>
      <span className="product-filter__label">{label}</span>
      <select
        id={`product-filter-${field}`}
        value={value}
        disabled={!usable}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{usable ? '전체' : '선택지 없음'}</option>
        {usable && choices.map(({ value: raw, label: text }) => (
          <option value={raw} key={raw}>{text}</option>
        ))}
      </select>
    </label>
  );
}

type CpuFilterProps = {
  groups: { os: string; tiers: string[] }[];
  value: string;
  onChange: (raw: string) => void;
};

function CpuFilter({ groups, value, onChange }: CpuFilterProps) {
  const usable = groups.reduce((total, group) => total + group.tiers.length, 0) > 1;
  return (
    <label className="product-filter" htmlFor="product-filter-cpu">
      <span className="product-filter__label">CPU</span>
      <select id="product-filter-cpu" value={value} disabled={!usable} onChange={(event) => onChange(event.target.value)}>
        <option value="">{usable ? '전체' : '선택지 없음'}</option>
        {usable && groups.map(({ os, tiers }) => (
          <optgroup label={OS_GROUP_LABELS[os] ?? os} key={os}>
            {tiers.map((tier) => (
              <option value={tier} key={tier}>{cpuFilterLabel(os, tier)}</option>
            ))}
          </optgroup>
        ))}
      </select>
    </label>
  );
}

/**
 * 선택지를 목록에 실제로 있는 값으로만 만든다. 그래야 조건 하나만 걸어서 0건이 되는 일이 없다.
 * CPU 만은 그럴 수 없다 — 목록 응답이 CPU 모델명만 주고 등급을 주지 않아 가진 등급을 셀 수 없다.
 */
function buildFilterOptions(products: Product[], specs: Spec[], os: string | null) {
  return {
    maxPrice: PRICE_STEPS.filter((step) => products.some((product) => product.minPrice !== null && product.minPrice <= step)),
    os: [...new Set(products.map((product) => specItem(product, 'OS')?.value ?? ''))].filter((value) => value !== '').sort(),
    // 고른 OS 의 등급만 연다. 고르지 않았고 권장안도 없으면 등급표를 모두 연다.
    cpu: (os !== null ? [os] : specs.length === 0 ? Object.keys(CPU_TIERS) : specs.map(osValueOf))
      .map((value) => ({ os: value, tiers: CPU_TIERS[value] ?? [] })),
    brand: [...new Set(products.map((product) => product.brand))].sort((one, other) => one.localeCompare(other)),
    memoryGb: uniqueAscending(products, 'MEMORY'),
    storageGb: uniqueAscending(products, 'STORAGE'),
  };
}

function uniqueAscending(products: Product[], code: string): number[] {
  const values = products
    .map((product) => Number(specItem(product, code)?.value))
    .filter((value) => !Number.isNaN(value));
  return [...new Set(values)].sort((one, other) => one - other);
}

function describeCondition({ keyword, maxPrice, os, cpuTier, brand, memoryGb, storageGb }: SearchCondition): string {
  return [
    keyword !== null ? `검색어 "${keyword}"` : '',
    maxPrice !== null ? `${formatPrice(maxPrice)} 이하` : '',
    os !== null ? (OS_GROUP_LABELS[os] ?? os) : '',
    brand !== null ? `브랜드 ${brand}` : '',
    cpuTier !== null ? cpuFilterLabel(osOfTier(cpuTier) ?? '', cpuTier) : '',
    memoryGb !== null ? `RAM ${memoryGb}GB 이상` : '',
    storageGb !== null ? `저장장치 ${formatStorage(storageGb)} 이상` : '',
  ].filter((text) => text !== '').join(' · ');
}

function specItem(product: Product, code: string): ProductSpecItem | undefined {
  return product.specs.find((item) => item.code === code);
}

function specValue(product: Product, code: string): string {
  return specItem(product, code)?.displayValue ?? '';
}

function formatStorage(value: number): string {
  return value >= 1024 ? `${value / 1024}TB` : `${value}GB`;
}
