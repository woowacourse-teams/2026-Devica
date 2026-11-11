import { type KeyboardEvent, type ReactNode, useEffect, useRef, useState } from 'react';
import { ChoiceGroup } from './ChoiceGroup';
import { useFetched, usePaged } from './fetched';
import { CheckIcon, ChevronIcon, CloseIcon, FilterIcon, SearchIcon, SortIcon } from './icons';
import { ProductImage } from './ProductImage';
import {
  COLOR_GAMUT_FILTER_LABELS,
  type CpuTier,
  cpuFilterLabel,
  EMPTY_CONDITION,
  fetchBrands,
  fetchCpuTiers,
  fetchGpuTiers,
  fetchProductPage,
  formatPrice,
  formatStorage,
  type GpuTier,
  gpuFilterLabel,
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
import { currentNavState, navigateInside } from './routes';
import { SearchLoadingView } from './SearchLoadingView';
import { useCompact } from './useCompact';
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
  { value: 'PRICE_ASC', label: '가격 낮은 순' },
  { value: 'PRICE_DESC', label: '가격 높은 순' },
];

const CARD_SPECS = ['CPU', 'GPU', 'MEMORY', 'STORAGE'];

const OS_GROUP_LABELS: Record<string, string> = { MAC: 'Mac', WINDOWS: 'Windows' };

const BOTH_OS = 'BOTH';

const SKELETON_KEYS = ['first', 'second', 'third'];

export type ListMode = 'MATCHED' | 'ALL';

// eyebrow 와 제목이 같은 말이 되지 않게 나눈다. 제목이 목록의 정체를, eyebrow 가 상위 분류를 맡는다.
const HEADINGS: Record<ListMode, { eyebrow: string; title: string; description: string }> = {
  MATCHED: {
    eyebrow: '조건에 맞는 제품',
    title: '추천 노트북',
    description: '제품 추천 결과를 필터로 채워 두었어요. 필터를 바꿔 더 찾아볼 수 있어요.',
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
  // 질문에 답하지 않고 기본 권장 사양으로 연 맞춤 목록이다.
  baseline?: boolean;
  bothOs?: boolean;
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
  baseline = false,
  bothOs = false,
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
  const gpuTiers = useFetched('gpu-tiers', fetchGpuTiers);
  const gpuFilterTiers = gpuTiers.data?.filter((tier) => !tier.lowest) ?? null;
  const waitingForGpuTiers = state.condition.gpuTier !== null && gpuFilterTiers === null;
  const gpuTiersFailed = waitingForGpuTiers && gpuTiers.failed;
  const condition = {
    ...state.condition,
    cpuTier: tiers === null ? state.condition.cpuTier : validTier(tiers, state.condition.os, state.condition.cpuTier),
    gpuTier:
      gpuFilterTiers === null
        ? state.condition.gpuTier
        : validTier(gpuFilterTiers, state.condition.os, state.condition.gpuTier),
  };
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
    waitingForTiers || waitingForGpuTiers || waitingForBrands ? null : `products:${categoryCode}?${listQuery}`,
    (page) => fetchProductPage(categoryCode, listQuery, page),
  );
  const items = products.data;
  // 첫 페이지를 받지 못했으면 목록 자리에 안내를 띄운다. 이어 받다 실패하면 받은 제품은 그대로 둔다.
  const failed = (products.failed || tiersFailed || gpuTiersFailed || brandsFailed) && items === null;

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
  const waiting =
    loading ||
    (waitingForTiers && !cpuTiers.failed) ||
    (waitingForGpuTiers && !gpuTiers.failed) ||
    (waitingForBrands && !brands.failed);
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

  const heading = baseline
    ? { ...HEADINGS[mode], description: '기본 권장 사양을 필터로 채워 두었어요. 필터를 바꿔 더 찾아볼 수 있어요.' }
    : HEADINGS[mode];
  // OS 는 탭이라 필터로 치지 않는다. 필터를 지워도 보던 탭은 그대로다.
  const filters = { ...condition, os: null };
  const hasCondition = Object.values(filters).some((value) => value !== null);
  const chips = conditionLabels(filters, tiers, gpuFilterTiers);
  // 바는 어디서나 위에 붙는다. 필터는 모바일에서 하단 시트, 데스크톱에서 바 아래 펼침 패널이다.
  const compact = useCompact();

  const change = (field: keyof SearchCondition, value: SearchCondition[keyof SearchCondition]) => {
    onChangeState((previous) => ({ ...previous, condition: { ...previous.condition, [field]: value } }));
  };
  const setFilterOpen = (filterOpen: boolean) => onChangeState((previous) => ({ ...previous, filterOpen }));
  const resetFilters = () =>
    onChangeState((previous) => ({ ...previous, condition: { ...EMPTY_CONDITION, os: previous.condition.os } }));

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
      const gpuTier = previous.condition.gpuTier;
      const keptGpu = gpuTier !== null && os !== null && osOfTier(gpuTiers.data ?? [], gpuTier) === os ? gpuTier : null;
      return { ...previous, condition: { ...previous.condition, os, cpuTier: kept, gpuTier: keptGpu } };
    });
  };

  if (matching) {
    return (
      <SearchLoadingView
        eyebrow={baseline ? '기본 권장 사양으로 찾는 중' : '추천 결과로 찾는 중'}
        lines={[searchingLine(condition, tiers, spec, OS_GROUP_LABELS)]}
      />
    );
  }

  // 전체 목록은 두 OS 를 함께 볼 수 있다. 맞춤 목록은 OS 마다 권장안이 달라 하나씩만 본다.
  const osChoices = matchedMode
    ? specs.map((each) => ({ value: osValueOf(each), label: OS_GROUP_LABELS[osValueOf(each)] ?? osValueOf(each) }))
    : [...OSES.map((os) => ({ value: os, label: OS_GROUP_LABELS[os] ?? os })), { value: BOTH_OS, label: '둘 다' }];

  const fields = (
    <>
      {condition.os === 'MAC' && <p className="product-filter-note">Mac 탭에서는 GPU 필터가 보이지 않아요.</p>}
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
        <button className="button" type="button" onClick={cpuTiers.retry}>
          CPU 등급 다시 시도
        </button>
      )}
      {condition.os === 'WINDOWS' && (
        <Filter
          label="GPU"
          value={condition.gpuTier ?? ''}
          choices={(gpuFilterTiers ?? [])
            .filter((tier) => tier.os === condition.os)
            .map((tier) => ({ value: tier.code, label: gpuFilterLabel(tier) }))}
          unavailable={unavailableLabel(gpuTiers)}
          onChange={(raw) => change('gpuTier', raw === '' ? null : raw)}
        />
      )}
      {condition.os === 'WINDOWS' && gpuTiers.failed && !waitingForGpuTiers && (
        <button className="button" type="button" onClick={gpuTiers.retry}>
          GPU 등급 다시 시도
        </button>
      )}
      <Filter
        label="브랜드"
        value={condition.brand ?? ''}
        choices={(brands.data ?? []).map((brand) => ({ value: brand, label: brand }))}
        unavailable={unavailableLabel(brands)}
        onChange={(raw) => change('brand', raw === '' ? null : raw)}
      />
      <Filter
        label="MEMORY"
        value={condition.memoryGb === null ? '' : String(condition.memoryGb)}
        choices={MEMORY_STEPS.map((memory) => ({ value: String(memory), label: `${memory}GB 이상` }))}
        onChange={(raw) => change('memoryGb', raw === '' ? null : Number(raw))}
      />
      <Filter
        label="STORAGE"
        value={condition.storageGb === null ? '' : String(condition.storageGb)}
        choices={STORAGE_STEPS.map((storage) => ({
          value: String(storage),
          label: `${formatStorage(storage)} 이상`,
        }))}
        onChange={(raw) => change('storageGb', raw === '' ? null : Number(raw))}
      />
      <Filter
        label="가격"
        value={condition.maxPrice === null ? '' : String(condition.maxPrice)}
        choices={PRICE_STEPS.map((price) => ({ value: String(price), label: `${formatPrice(price)} 이하` }))}
        onChange={(raw) => change('maxPrice', raw === '' ? null : Number(raw))}
      />
    </>
  );

  const osValue = condition.os ?? BOTH_OS;
  const selectOs = (os: string) => changeOs(os === BOTH_OS ? null : os);
  const changeSort = (next: string) => onChangeState((previous) => ({ ...previous, sort: next as SortType }));
  const sortLabel = SORT_LABELS.find(({ value }) => value === sort)?.label;
  const filterLabel = chips.length > 0 ? `필터, ${chips.length}개 적용됨` : '필터';
  const filterCount = chips.length > 0 && (
    <span className="product-filter-toggle__count" aria-hidden="true">
      {chips.length}
    </span>
  );
  const loadingMore = waiting && items !== null;
  const reachedEnd = items !== null && items.length > 0 && !hasNext && !waiting && !products.failed;

  return (
    <section
      className="view-panel flow-view product-list-view"
      id="product-list-view"
      aria-labelledby="product-list-title"
    >
      <button className="text-button view-back-button" id="product-list-back-button" type="button" onClick={onBack}>
        {backLabel}
      </button>
      <div className="flow-head">
        <p className="flow-head__eyebrow" id="product-list-eyebrow">
          {heading.eyebrow}
        </p>
        <h1 id="product-list-title">{heading.title}</h1>
        <p className="flow-head__description product-list-description" id="product-list-description">
          {heading.description}
        </p>
      </div>

      <div className="product-list-os" id="product-list-os">
        <ChoiceGroup
          segmented
          label="OS"
          choices={osChoices}
          value={osValue}
          // 탭은 비울 수 없다. 고른 것을 다시 눌러도 그대로 둔다.
          onChange={(os) => os !== null && selectOs(os)}
        />
        {bothOs && <p className="product-list-os__note">탭을 바꾸면 Mac과 Windows 제품을 각각 볼 수 있어요.</p>}
      </div>
      <div className="product-list-bar" id="product-list-bar">
        <label className="product-list-control product-search" htmlFor="product-filter-keyword">
          <SearchIcon />
          <input
            id="product-filter-keyword"
            type="search"
            value={typed}
            aria-label="제품 검색"
            placeholder="브랜드 · 제품명"
            onChange={(event) => setTyped(event.target.value)}
          />
        </label>
        <div className="product-list-bar__actions">
          <button
            className="product-list-control product-filter-toggle"
            id="product-filter-toggle"
            type="button"
            aria-expanded={filterOpen}
            aria-controls="product-filter-panel"
            aria-label={filterLabel}
            onClick={() => setFilterOpen(!filterOpen)}
          >
            <FilterIcon />
            {!compact && '필터'}
            {filterCount}
          </button>
          <MenuButton
            id="product-sort"
            label="정렬"
            options={SORT_LABELS}
            value={sort}
            onChange={changeSort}
            display={
              compact ? (
                <SortIcon />
              ) : (
                <>
                  <span className="product-list-control__label">정렬</span>
                  <b>{sortLabel}</b>
                </>
              )
            }
          />
        </div>
      </div>
      {/* 접어도 DOM 에 남긴다. 사라지면 aria-controls 가 없는 id 를 가리킨다. */}
      {!compact && (
        <div className="product-filter-panel" id="product-filter-panel" data-open={filterOpen} inert={!filterOpen}>
          <div className="product-filter-panel__clip">
            <div className="product-filter-panel__box">{fields}</div>
          </div>
        </div>
      )}

      {compact && (
        <FilterSheet
          open={filterOpen}
          canReset={hasCondition}
          onClose={() => setFilterOpen(false)}
          onReset={resetFilters}
        >
          {fields}
        </FilterSheet>
      )}

      {/* 패널을 접어도 무엇으로 걸렀는지 보이게 한다. */}
      {chips.length > 0 && (
        <div className="product-condition-chips" id="product-condition-chips">
          <span className="product-condition-chips__label" id="product-condition-chips-label">
            적용한 필터
          </span>
          <ul aria-labelledby="product-condition-chips-label">
            {chips.map(({ field, label }) => (
              <li key={field}>
                <button
                  className="product-condition-chip"
                  type="button"
                  aria-label={`${label} 필터 지우기`}
                  onClick={() => change(field, null)}
                >
                  {label}
                  <CloseIcon size={14} />
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* 제품이 이어 붙을 때마다 카드를 모두 읽지 않게 목록은 알리지 않는다. 받는 중인지는 아래 상태 문구만 알린다. */}
      <div className="product-list-results" id="product-list" aria-busy={waiting}>
        {failed ? (
          <p className="product-list-failure" role="alert">
            제품 목록을 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.
          </p>
        ) : items === null ? (
          <ul className="product-list" aria-hidden="true">
            {SKELETON_KEYS.map((key) => (
              <li className="product-card product-card--skeleton" key={key} />
            ))}
          </ul>
        ) : items.length === 0 ? (
          <EmptyResult
            condition={filters}
            tiers={tiers}
            gpuTiers={gpuFilterTiers}
            hasCondition={hasCondition}
            showsAll={mode === 'ALL'}
            onReset={resetFilters}
            onShowAll={onShowAll}
          />
        ) : (
          <ul className="product-list">
            {items.map((product) => (
              <li key={product.id}>
                <ProductCard product={product} onDetail={onDetail} />
              </li>
            ))}
          </ul>
        )}
      </div>
      <div ref={setSentinel} />
      <p
        className={loadingMore ? 'product-list-status product-list-status--more' : 'product-list-status'}
        role="status"
      >
        {waiting ? (items === null ? '제품을 불러오는 중입니다…' : '제품을 더 불러오는 중입니다…') : ''}
        {products.failed && items !== null ? '제품을 더 불러오지 못했습니다.' : ''}
        {reachedEnd ? `모든 제품을 봤어요 · ${items.length}개` : ''}
      </p>
      {(products.failed || tiersFailed || gpuTiersFailed || brandsFailed) && (
        <button
          className="button product-list-retry"
          type="button"
          onClick={tiersFailed ? cpuTiers.retry : gpuTiersFailed ? gpuTiers.retry : brandsFailed ? brands.retry : more}
        >
          다시 시도
        </button>
      )}
    </section>
  );
}

type MenuOption = { value: string; label: string };

type MenuButtonProps = {
  id: string;
  label: string;
  options: MenuOption[];
  value: string;
  display: ReactNode;
  onChange: (value: string) => void;
};

function MenuButton({ id, label, options, value, display, onChange }: MenuButtonProps) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const selected = options.find((option) => option.value === value);

  useEffect(() => {
    if (!open) {
      return;
    }
    rootRef.current?.querySelector<HTMLButtonElement>('[aria-checked="true"]')?.focus();
    const closeOutside = (event: PointerEvent) => {
      if (!(event.target instanceof Node) || !rootRef.current?.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', closeOutside);
    return () => document.removeEventListener('pointerdown', closeOutside);
  }, [open]);

  const close = () => {
    setOpen(false);
    triggerRef.current?.focus();
  };

  const moveFocus = (event: KeyboardEvent<HTMLDivElement>) => {
    const items = [...(rootRef.current?.querySelectorAll<HTMLButtonElement>('[role="menuitemradio"]') ?? [])];
    const index = items.indexOf(document.activeElement as HTMLButtonElement);
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const step = event.key === 'ArrowDown' ? 1 : -1;
      items[(index + step + items.length) % items.length]?.focus();
    } else if (event.key === 'Escape') {
      event.preventDefault();
      close();
    } else if (event.key === 'Tab') {
      setOpen(false);
    }
  };

  return (
    <div className="menu-button" ref={rootRef}>
      <button
        className="product-list-control menu-button__trigger"
        id={id}
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`${label}, ${selected?.label ?? ''}`}
        onClick={() => setOpen(!open)}
      >
        {display}
        <ChevronIcon className="menu-button__chevron" />
      </button>
      <div
        className="menu-button__menu"
        role="menu"
        aria-label={label}
        data-open={open}
        inert={!open}
        onKeyDown={moveFocus}
      >
        {options.map((option) => (
          <button
            className="menu-button__item"
            type="button"
            role="menuitemradio"
            aria-checked={option.value === value}
            key={option.value}
            onClick={() => {
              close();
              if (option.value !== value) {
                onChange(option.value);
              }
            }}
          >
            {option.label}
            {option.value === value && <CheckIcon />}
          </button>
        ))}
      </div>
    </div>
  );
}

function ProductCard({ product, onDetail }: { product: Product; onDetail: (productId: number) => void }) {
  const specs = CARD_SPECS.map((code) => specValue(product, code))
    .filter((value) => value !== '')
    .join(' · ');
  return (
    <a
      className="product-card"
      href={`/products/${product.id}`}
      onClick={(event) => navigateInside(event, () => onDetail(product.id))}
    >
      <ProductImage imageUrl={product.imageUrl} name={product.name} className="product-card__image-frame" />
      <div className="product-card__body">
        <span className="product-card__maker">
          {product.brand} · {specValue(product, 'OS')}
        </span>
        <h3 className="product-card__name">{product.name}</h3>
        <span className="product-card__specs">{specs}</span>
        <p className="product-card__price">
          <span>최저가</span>
          <strong>{formatPrice(product.minPrice)}</strong>
        </p>
      </div>
    </a>
  );
}

type FilterSheetProps = {
  open: boolean;
  canReset: boolean;
  onClose: () => void;
  onReset: () => void;
  children: ReactNode;
};

function FilterSheet({ open, canReset, onClose, onReset, children }: FilterSheetProps) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (dialog === null) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);
  return (
    <dialog
      className="product-filter-sheet"
      id="product-filter-panel"
      ref={ref}
      aria-labelledby="product-filter-sheet-title"
      onClose={() => open && onClose()}
    >
      <div className="product-filter-sheet__head">
        <h2 id="product-filter-sheet-title">필터</h2>
        <button className="product-filter-sheet__close" type="button" aria-label="필터 닫기" onClick={onClose}>
          <CloseIcon size={24} />
        </button>
      </div>
      <div className="product-filter-sheet__body">{children}</div>
      <div className="product-filter-sheet__actions">
        <button className="button" type="button" disabled={!canReset} onClick={onReset}>
          모두 지우기
        </button>
        <button className="button button--primary button--wide" type="button" onClick={onClose}>
          제품 보기
        </button>
      </div>
    </dialog>
  );
}

type EmptyResultProps = {
  condition: SearchCondition;
  tiers: CpuTier[] | null;
  gpuTiers: GpuTier[] | null;
  hasCondition: boolean;
  showsAll: boolean;
  onReset: () => void;
  onShowAll: () => void;
};

function EmptyResult({ condition, tiers, gpuTiers, hasCondition, showsAll, onReset, onShowAll }: EmptyResultProps) {
  if (hasCondition) {
    return (
      <div className="empty-result">
        <h2>조건에 맞는 제품이 없습니다.</h2>
        <p className="empty-result__condition">
          적용한 필터 — {describeCondition(condition, tiers, gpuTiers)}
          <br />
          칩의 ×로 필터를 하나씩 빼 보거나 모두 지워 보세요.
        </p>
        <button className="button" type="button" onClick={onReset}>
          필터 모두 지우기
        </button>
      </div>
    );
  }
  // 필터 없이 0건이면 사양이 높은 것이다. 목록을 넓혀 볼 길을 준다.
  return (
    <div className="empty-result">
      <h2>조건에 맞는 제품이 없습니다.</h2>
      <p className="empty-result__condition">필터를 직접 조정해 다시 확인해 주세요.</p>
      {!showsAll && (
        <button className="button" type="button" onClick={onShowAll}>
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
  label: string;
  value: string;
  choices: Choice[];
  unavailable?: string | null;
  onChange: (raw: string) => void;
};

// 선택지가 하나여도 적용한 조건을 보여 주고 해제할 수 있어야 한다.
function Filter({ label, value, choices, unavailable = null, onChange }: FilterProps) {
  return (
    <div className="product-filter-group">
      <span className="product-filter__label">{label}</span>
      {choices.length > 0 ? (
        <ChoiceGroup
          label={label}
          choices={[{ value: '', label: '전체' }, ...choices]}
          value={value}
          onChange={(raw) => onChange(raw ?? '')}
        />
      ) : (
        <p className="product-filter-group__empty">{unavailable ?? '선택지 없음'}</p>
      )}
    </div>
  );
}

type CpuFilterProps = {
  groups: { os: string; tiers: CpuTier[] }[];
  value: string;
  unavailable: string | null;
  onChange: (raw: string) => void;
};

function CpuFilter({ groups, value, unavailable, onChange }: CpuFilterProps) {
  const choices = groups.flatMap(({ os, tiers }) =>
    tiers.map((tier) => ({
      value: tier.code,
      label: groups.length > 1 ? `${OS_GROUP_LABELS[os] ?? os} ${cpuFilterLabel(tier)}` : cpuFilterLabel(tier),
    })),
  );
  return (
    <Filter
      label="CPU"
      value={value}
      choices={choices.length > 1 ? choices : []}
      unavailable={unavailable}
      onChange={onChange}
    />
  );
}

function conditionLabels(
  { keyword, maxPrice, os, cpuTier, colorGamut, gpuTier, brand, memoryGb, storageGb }: SearchCondition,
  tiers: CpuTier[] | null,
  gpuTiers: GpuTier[] | null,
): { field: keyof SearchCondition; label: string }[] {
  const tier = tiers?.find((each) => each.code === cpuTier);
  const gpu = gpuTiers?.find((each) => each.code === gpuTier);
  const labels: { field: keyof SearchCondition; label: string | null }[] = [
    { field: 'keyword', label: keyword !== null ? `검색어 "${keyword}"` : null },
    { field: 'maxPrice', label: maxPrice !== null ? `${formatPrice(maxPrice)} 이하` : null },
    { field: 'os', label: os !== null ? (OS_GROUP_LABELS[os] ?? os) : null },
    { field: 'brand', label: brand !== null ? `브랜드 ${brand}` : null },
    { field: 'cpuTier', label: tier !== undefined ? cpuFilterLabel(tier) : null },
    { field: 'gpuTier', label: gpu !== undefined ? gpuFilterLabel(gpu) : null },
    { field: 'colorGamut', label: colorGamut !== null ? COLOR_GAMUT_FILTER_LABELS[colorGamut] : null },
    { field: 'memoryGb', label: memoryGb !== null ? `MEMORY ${memoryGb}GB 이상` : null },
    { field: 'storageGb', label: storageGb !== null ? `STORAGE ${formatStorage(storageGb)} 이상` : null },
  ];
  return labels.flatMap(({ field, label }) => (label === null ? [] : [{ field, label }]));
}

function describeCondition(condition: SearchCondition, tiers: CpuTier[] | null, gpuTiers: GpuTier[] | null): string {
  return conditionLabels(condition, tiers, gpuTiers)
    .map(({ label }) => label)
    .join(' · ');
}

function specItem(product: Product, code: string): ProductSpecItem | undefined {
  return product.specs.find((item) => item.code === code);
}

function specValue(product: Product, code: string): string {
  return specItem(product, code)?.displayValue ?? '';
}
