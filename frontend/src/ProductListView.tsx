import { useEffect, useRef, useState } from 'react';
import { ChoiceGroup } from './ChoiceGroup';
import { useFetched, usePaged } from './fetched';
import { ProductImage } from './ProductImage';
import {
  type CpuTier,
  cpuFilterLabel,
  EMPTY_CONDITION,
  fetchBrands,
  fetchCpuTiers,
  fetchProductPage,
  formatPrice,
  formatStorage,
  MEMORY_STEPS,
  OSES,
  osOfTier,
  type Product,
  type ProductSpecItem,
  productListQuery,
  type SearchCondition,
  type SortType,
  STORAGE_STEPS,
  searchingLine,
  specCondition,
  validTier,
} from './products';
import { osValueOf, type Spec } from './recommendation';
import { currentNavState } from './routes';
import { SearchLoadingView } from './SearchLoadingView';
import './ProductListView.css';

// 글자마다 조회하지 않도록 입력이 멈추길 기다린다.
const KEYWORD_DELAY_MS = 300;

// 대조는 곧 끝나지만, 답이 실제로 쓰였다는 걸 보여주려고 대조 화면을 이만큼은 띄운다.
const SEARCH_HOLD_MS = 1800;

// 목록 끝에 닿기 전에 다음 페이지를 받아, 스크롤하는 동안 기다리지 않게 한다.
const PREFETCH_MARGIN = '0px 0px 600px 0px';

// 선택지는 고정한다. 목록 API 는 전체 집합을 주지 않는다.
// 원본은 제품 가격 분포와 무관하게 눈에 익은 눈금을 쓴다.
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
    description:
      '제품 추천 결과를 검색 조건으로 채워 두었어요. 아래 조건을 눌러 풀면 사양과 가격이 더 낮은 제품도 볼 수 있어요.',
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

type Props = {
  categoryCode: string;
  mode: ListMode;
  // 맞춤 목록의 맥·윈도우 권장안이다. 전체 목록에서는 비어 있다.
  specs: Spec[];
  backLabel: string;
  // 상세를 다녀와도 정렬과 검색 조건이 풀리지 않게 부모가 들고 있는다.
  // 맞춤 목록에서는 condition.os 가 보고 있는 OS 탭이다. 부모가 늘 채워 준다.
  state: ProductListState;
  onChangeState: (update: (previous: ProductListState) => ProductListState) => void;
  // 권장 사양에서 검색으로 들어왔다. 제품을 받을 때까지 사양 대조 화면이 자리를 지킨다.
  searching: boolean;
  // 목록을 받지 못하고 끝났으면 failed 가 참이다.
  onSearched: (failed: boolean) => void;
  onBack: () => void;
  onDetail: (productId: number) => void;
  onShowAll: () => void;
};

export function ProductListView({
  categoryCode,
  mode,
  specs,
  backLabel,
  state,
  onChangeState,
  searching,
  onSearched,
  onBack,
  onDetail,
  onShowAll,
}: Props) {
  const { sort, filterOpen } = state;
  // 주소에 CPU 조건이 있을 때만 등급을 기다린다. 조건이 없으면 필터 선택지와 목록을 독립적으로 받는다.
  const cpuTiers = useFetched('cpu-tiers', fetchCpuTiers);
  const tiers = cpuTiers.data;
  const waitingForTiers = state.condition.cpuTier !== null && tiers === null;
  const tiersFailed = waitingForTiers && cpuTiers.failed;
  const condition =
    tiers === null ? state.condition : { ...state.condition, cpuTier: validTier(tiers, state.condition) };
  const matchedMode = mode === 'MATCHED';
  // 맞춤 목록은 OS 탭 하나씩 서버 목록 하나를 본다. 두 OS 를 섞지 않으니 추천순을 늘 쓸 수 있다.
  // 사양 대조 화면이 보고 있는 OS 의 권장안을 보여 준다.
  const spec = specs.find((each) => osValueOf(each) === condition.os);

  const [typed, setTyped] = useState(condition.keyword ?? '');

  // "검색 조건 지우기" 로 조건이 비면 입력칸도 함께 비운다.
  // 입력 중에는 되돌리지 않는다. trim 된 값을 다시 넣으면 치던 공백이 사라진다.
  useEffect(() => {
    if (condition.keyword === null) {
      setTyped('');
    }
  }, [condition.keyword]);

  // 조건이 바뀌어 다시 돌아도, 입력과 조건이 같으면 곧바로 끝나 타이머를 새로 걸지 않는다.
  // change 는 렌더마다 새로 만들어져 의존성에 넣으면 렌더마다 타이머가 다시 걸린다. 늘 같은 onChangeState 를 직접 쓴다.
  useEffect(() => {
    const next = typed.trim() === '' ? null : typed.trim();
    if (next === condition.keyword) {
      return;
    }
    const timer = setTimeout(
      () => onChangeState((previous) => ({ ...previous, condition: { ...previous.condition, keyword: next } })),
      KEYWORD_DELAY_MS,
    );
    return () => clearTimeout(timer);
  }, [typed, condition.keyword, onChangeState]);

  const brands = useFetched(`brands:${categoryCode}:${condition.os ?? ''}`, () =>
    fetchBrands(categoryCode, condition.os),
  );
  // 브랜드는 OS 를 바꿀 때만 검증한다. 처음부터 지정된 검색 조건은 임의로 넓히지 않는다.
  const brandOs = useRef(condition.os);
  const waitingForBrands =
    brandOs.current !== condition.os &&
    condition.brand !== null &&
    (brands.data === null || !brands.data.includes(condition.brand));
  const brandsFailed = waitingForBrands && brands.failed;

  useEffect(() => {
    if (brandOs.current === condition.os || (condition.brand !== null && brands.data === null)) {
      return;
    }
    brandOs.current = condition.os;
    if (condition.brand === null || brands.data?.includes(condition.brand)) {
      return;
    }
    onChangeState((previous) =>
      previous.condition.os === condition.os && previous.condition.brand === condition.brand
        ? { ...previous, condition: { ...previous.condition, brand: null } }
        : previous,
    );
  }, [brands.data, condition.os, condition.brand, onChangeState]);

  // 맞춤 목록의 사양 조건은 부모가 주소에 채운 뒤 그린다. 여기서는 전체 목록과 같은 조건으로 요청한다.
  const listQuery = productListQuery(sort, condition);
  const products = usePaged(
    waitingForTiers || waitingForBrands ? null : `products:${categoryCode}?${listQuery}`,
    (page) => fetchProductPage(categoryCode, listQuery, page),
  );
  const items = products.data;
  // 첫 페이지를 받지 못했으면 목록 자리에 안내를 띄운다. 이어 받다 실패하면 받은 제품은 그대로 둔다.
  const failed = (products.failed || tiersFailed || brandsFailed) && items === null;

  const [holding, setHolding] = useState(searching);

  useEffect(() => {
    if (!searching) {
      return;
    }
    const timer = setTimeout(() => setHolding(false), SEARCH_HOLD_MS);
    return () => clearTimeout(timer);
  }, [searching]);

  // 대조 화면은 최소 시간과 첫 응답 중 늦은 쪽까지 머문다. 실패하면 목록이 안내를 맡는다.
  const matching = searching && !failed && (holding || items === null);

  useEffect(() => {
    if (searching && !matching) {
      onSearched(failed);
    }
  }, [searching, matching, failed, onSearched]);

  // 뒤로 가기로 돌아오면 받아 둔 제품을 다 그린 뒤 보던 위치로 되돌린다. 그리기 전에는 페이지가 짧아 그 위치까지 내려가지 못한다.
  const restored = useRef(false);
  useEffect(() => {
    if (items === null || restored.current) {
      return;
    }
    restored.current = true;
    const { scrollY } = currentNavState();
    if (scrollY !== undefined) {
      window.scrollTo(0, scrollY);
    }
  }, [items]);

  // 목록 끝이 가까워지면 다음 페이지를 받는다. 받을 때마다 다시 지켜봐서, 받은 뒤에도 끝이 보이면 이어 받는다.
  // 실패하면 멈추고 사용자가 다시 시도하길 기다린다. 저절로 다시 받으면 실패가 이어지는 동안 요청이 되풀이된다.
  // 요소를 상태로 받는다. 사양 대조 화면이 떠 있는 동안은 표식이 없다가, 대조가 끝나 그려질 때 effect 가 다시 돈다.
  const [sentinel, setSentinel] = useState<HTMLDivElement | null>(null);
  const { hasNext, loading, more } = products;
  // 조건을 검증하는 동안에는 목록을 요청하지 않지만, 사용자에게는 똑같이 불러오는 중이다.
  const waiting = loading || (waitingForTiers && !cpuTiers.failed) || (waitingForBrands && !brands.failed);
  useEffect(() => {
    if (sentinel === null || !hasNext || loading || products.failed) {
      return;
    }
    const observer = new IntersectionObserver((entries) => entries[0]?.isIntersecting && more(), {
      rootMargin: PREFETCH_MARGIN,
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [sentinel, hasNext, loading, products.failed, more]);

  const heading = HEADINGS[mode];
  // 맞춤 목록의 OS 는 탭이라 검색 조건으로 치지 않는다. 조건을 지워도 보던 탭은 그대로다.
  const filters = matchedMode ? { ...condition, os: null } : condition;
  const hasCondition = Object.values(filters).some((value) => value !== null);
  const chips = conditionLabels(filters, tiers).filter(({ field }) => field !== 'maxPrice');

  const change = (field: keyof SearchCondition, value: SearchCondition[keyof SearchCondition]) => {
    onChangeState((previous) => ({ ...previous, condition: { ...previous.condition, [field]: value } }));
  };

  // 등급과 브랜드는 OS 마다 다르다. OS 를 바꾸면 그 OS 에 없는 조건은 함께 지운다.
  // 맞춤 목록의 OS 탭은 그 OS 의 권장안을 보는 것이라, 사양 조건을 그 권장안으로 다시 채운다.
  const changeOs = (os: string | null) => {
    const next = specs.find((each) => osValueOf(each) === os);
    onChangeState((previous) => {
      if (matchedMode && next !== undefined) {
        return { ...previous, condition: { ...previous.condition, ...specCondition(next) } };
      }
      const tier = previous.condition.cpuTier;
      const kept = tier !== null && (os === null || osOfTier(tiers ?? [], tier) === os) ? tier : null;
      return { ...previous, condition: { ...previous.condition, os, cpuTier: kept } };
    });
  };

  if (matching) {
    return <SearchLoadingView lines={[searchingLine(condition, tiers, spec, OS_GROUP_LABELS)]} />;
  }

  return (
    <section className="view-panel product-list-view" id="product-list-view" aria-labelledby="product-list-title">
      <button className="text-button view-back-button" id="product-list-back-button" type="button" onClick={onBack}>
        {backLabel}
      </button>
      <div className="product-list-heading">
        <div>
          <p className="eyebrow" id="product-list-eyebrow">
            {heading.eyebrow}
          </p>
          <h2 id="product-list-title">{heading.title}</h2>
          <p className="section-description" id="product-list-description">
            {heading.description}
          </p>
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
          {/* 가격은 가장 먼저 찾는 조건이라 패널에 숨기지 않는다. */}
          <label className="product-list-control product-list-price" htmlFor="product-filter-price">
            <span className="product-list-control__label">가격</span>
            <select
              id="product-filter-price"
              value={condition.maxPrice === null ? '' : String(condition.maxPrice)}
              onChange={(event) => change('maxPrice', event.target.value === '' ? null : Number(event.target.value))}
            >
              <option value="">전체</option>
              {PRICE_STEPS.map((price) => (
                <option value={String(price)} key={price}>
                  {formatPrice(price)} 이하
                </option>
              ))}
            </select>
          </label>
          <label className="product-list-control product-list-sort" htmlFor="product-sort">
            <span className="product-list-control__label">정렬</span>
            <select
              id="product-sort"
              value={sort}
              onChange={(event) => onChangeState((previous) => ({ ...previous, sort: event.target.value as SortType }))}
            >
              {SORT_LABELS.map(({ value, label }) => (
                <option value={value} key={value} hidden={value === 'RECOMMENDED' && !matchedMode}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {/* 패널을 접어도 무엇으로 걸렀는지 보이게 한다. 가격은 위에서 바로 보이니 뺀다. */}
      {chips.length > 0 && (
        <ul className="product-condition-chips" id="product-condition-chips" aria-label="적용한 검색 조건">
          {chips.map(({ field, label }) => (
            <li key={field}>
              <button
                className="product-condition-chip"
                type="button"
                aria-label={`${label} 조건 지우기`}
                onClick={() => (field === 'os' ? changeOs(null) : change(field, null))}
              >
                {label}
                <span aria-hidden="true">×</span>
              </button>
            </li>
          ))}
        </ul>
      )}

      {matchedMode && (
        <div className="product-list-os" id="product-list-os">
          <ChoiceGroup
            segmented
            label="OS"
            choices={specs.map((each) => ({
              value: osValueOf(each),
              label: OS_GROUP_LABELS[osValueOf(each)] ?? osValueOf(each),
            }))}
            value={condition.os}
            // 탭은 비울 수 없다. 고른 것을 다시 눌러도 그대로 둔다.
            onChange={(os) => os !== null && changeOs(os)}
          />
        </div>
      )}

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
        {!matchedMode && (
          <Filter
            field="os"
            label="OS"
            value={condition.os ?? ''}
            choices={OSES.map((os) => ({ value: os, label: OS_GROUP_LABELS[os] ?? os }))}
            onChange={(raw) => changeOs(raw === '' ? null : raw)}
          />
        )}
        <CpuFilter
          // 고른 OS 의 등급만 연다. 고르지 않았으면 등급표를 모두 연다.
          groups={(condition.os === null ? OSES : [condition.os]).map((os) => ({
            os,
            tiers: (tiers ?? []).filter((tier) => tier.os === os),
          }))}
          value={condition.cpuTier ?? ''}
          unavailable={unavailableLabel(cpuTiers)}
          onChange={(raw) => change('cpuTier', raw === '' ? null : raw)}
        />
        {cpuTiers.failed && !waitingForTiers && (
          <button className="button button--secondary" type="button" onClick={cpuTiers.retry}>
            CPU 등급 다시 시도
          </button>
        )}
        <Filter
          field="brand"
          label="브랜드"
          value={condition.brand ?? ''}
          choices={(brands.data ?? []).map((brand) => ({ value: brand, label: brand }))}
          unavailable={unavailableLabel(brands)}
          onChange={(raw) => change('brand', raw === '' ? null : raw)}
        />
        <Filter
          field="memory"
          label="RAM"
          value={condition.memoryGb === null ? '' : String(condition.memoryGb)}
          choices={MEMORY_STEPS.map((memory) => ({ value: String(memory), label: `${memory}GB 이상` }))}
          onChange={(raw) => change('memoryGb', raw === '' ? null : Number(raw))}
        />
        <Filter
          field="storage"
          label="저장장치"
          value={condition.storageGb === null ? '' : String(condition.storageGb)}
          choices={STORAGE_STEPS.map((storage) => ({
            value: String(storage),
            label: `${formatStorage(storage)} 이상`,
          }))}
          onChange={(raw) => change('storageGb', raw === '' ? null : Number(raw))}
        />
      </div>

      {/* 제품이 이어 붙을 때마다 카드를 모두 읽지 않게 목록은 알리지 않는다. 받는 중인지는 아래 상태 문구만 알린다. */}
      <div className="product-list" id="product-list" aria-busy={waiting}>
        {failed ? (
          <p className="notice" role="alert">
            제품 목록을 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.
          </p>
        ) : items === null ? null : items.length === 0 ? (
          <EmptyResult
            condition={filters}
            tiers={tiers}
            hasCondition={hasCondition}
            showsAll={mode === 'ALL'}
            onReset={() =>
              onChangeState((previous) => ({
                ...previous,
                condition: { ...EMPTY_CONDITION, os: matchedMode ? previous.condition.os : null },
              }))
            }
            onShowAll={onShowAll}
          />
        ) : (
          items.map((product) => <ProductCard product={product} onDetail={onDetail} key={product.id} />)
        )}
      </div>
      <div ref={setSentinel} />
      <p className="product-list-status" role="status">
        {waiting ? (items === null ? '제품을 불러오는 중입니다…' : '제품을 더 불러오는 중입니다…') : ''}
        {products.failed && items !== null ? '제품을 더 불러오지 못했습니다.' : ''}
      </p>
      {(products.failed || tiersFailed || brandsFailed) && (
        <button
          className="button button--secondary"
          type="button"
          onClick={tiersFailed ? cpuTiers.retry : brandsFailed ? brands.retry : more}
        >
          다시 시도
        </button>
      )}
    </section>
  );
}

function ProductCard({ product, onDetail }: { product: Product; onDetail: (productId: number) => void }) {
  return (
    <article className="bordered-panel product-card">
      <ProductImage imageUrl={product.imageUrl} name={product.name} className="product-card__image-frame" />
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
          className="button button--neutral button--wide product-card__detail"
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
  tiers: CpuTier[] | null;
  hasCondition: boolean;
  showsAll: boolean;
  onReset: () => void;
  onShowAll: () => void;
};

function EmptyResult({ condition, tiers, hasCondition, showsAll, onReset, onShowAll }: EmptyResultProps) {
  if (hasCondition) {
    return (
      <div className="empty-result">
        <p>조건에 맞는 제품이 없습니다.</p>
        <p className="empty-result__condition">적용한 검색 조건 — {describeCondition(condition, tiers)}</p>
        <button className="button button--secondary" type="button" onClick={onReset}>
          검색 조건 지우기
        </button>
      </div>
    );
  }
  // 조건 없이 0건이면 사양이 높은 것이다. 목록을 넓혀 볼 길을 준다.
  return (
    <div className="empty-result">
      <p>조건에 맞는 제품이 없습니다. 검색 조건을 직접 조정해 다시 확인해 주세요.</p>
      {!showsAll && (
        <button className="button button--secondary" type="button" onClick={onShowAll}>
          전체 제품 보기
        </button>
      )}
    </div>
  );
}

type Choice = { value: string; label: string };

// 선택지를 받는 중이거나 받지 못했으면 "선택지 없음" 대신 그 상태를 적는다. 받았으면 null 이다.
function unavailableLabel({ data, failed }: { data: unknown; failed: boolean }): string | null {
  if (data !== null) {
    return null;
  }
  return failed ? '불러오지 못함' : '불러오는 중…';
}

type FilterProps = {
  field: string;
  label: string;
  value: string;
  choices: Choice[];
  unavailable?: string | null;
  onChange: (raw: string) => void;
};

function Filter({ field, label, value, choices, unavailable = null, onChange }: FilterProps) {
  // 선택지가 하나여도 적용한 조건을 보여 주고 해제할 수 있어야 한다.
  const usable = choices.length > 0;
  return (
    <label className="product-filter" htmlFor={`product-filter-${field}`}>
      <span className="product-filter__label">{label}</span>
      <select
        id={`product-filter-${field}`}
        value={value}
        disabled={!usable}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{usable ? '전체' : (unavailable ?? '선택지 없음')}</option>
        {usable &&
          choices.map(({ value: raw, label: text }) => (
            <option value={raw} key={raw}>
              {text}
            </option>
          ))}
      </select>
    </label>
  );
}

type CpuFilterProps = {
  groups: { os: string; tiers: CpuTier[] }[];
  value: string;
  unavailable: string | null;
  onChange: (raw: string) => void;
};

function CpuFilter({ groups, value, unavailable, onChange }: CpuFilterProps) {
  const usable = groups.reduce((total, group) => total + group.tiers.length, 0) > 1;
  return (
    <label className="product-filter" htmlFor="product-filter-cpu">
      <span className="product-filter__label">CPU</span>
      <select
        id="product-filter-cpu"
        value={value}
        disabled={!usable}
        onChange={(event) => onChange(event.target.value)}
      >
        <option value="">{usable ? '전체' : (unavailable ?? '선택지 없음')}</option>
        {usable &&
          groups.map(({ os, tiers }) => (
            <optgroup label={OS_GROUP_LABELS[os] ?? os} key={os}>
              {tiers.map((tier) => (
                <option value={tier.code} key={tier.code}>
                  {cpuFilterLabel(tier)}
                </option>
              ))}
            </optgroup>
          ))}
      </select>
    </label>
  );
}

function conditionLabels(
  { keyword, maxPrice, os, cpuTier, brand, memoryGb, storageGb }: SearchCondition,
  tiers: CpuTier[] | null,
): { field: keyof SearchCondition; label: string }[] {
  const tier = tiers?.find((each) => each.code === cpuTier);
  const labels: { field: keyof SearchCondition; label: string | null }[] = [
    { field: 'keyword', label: keyword !== null ? `검색어 "${keyword}"` : null },
    { field: 'maxPrice', label: maxPrice !== null ? `${formatPrice(maxPrice)} 이하` : null },
    { field: 'os', label: os !== null ? (OS_GROUP_LABELS[os] ?? os) : null },
    { field: 'brand', label: brand !== null ? `브랜드 ${brand}` : null },
    { field: 'cpuTier', label: tier !== undefined ? cpuFilterLabel(tier) : null },
    { field: 'memoryGb', label: memoryGb !== null ? `RAM ${memoryGb}GB 이상` : null },
    { field: 'storageGb', label: storageGb !== null ? `저장장치 ${formatStorage(storageGb)} 이상` : null },
  ];
  return labels.flatMap(({ field, label }) => (label === null ? [] : [{ field, label }]));
}

function describeCondition(condition: SearchCondition, tiers: CpuTier[] | null): string {
  return conditionLabels(condition, tiers)
    .map(({ label }) => label)
    .join(' · ');
}

function specItem(product: Product, code: string): ProductSpecItem | undefined {
  return product.specs.find((item) => item.code === code);
}

function specValue(product: Product, code: string): string {
  return specItem(product, code)?.displayValue ?? '';
}
