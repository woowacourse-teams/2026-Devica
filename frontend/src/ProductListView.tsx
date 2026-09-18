import { useEffect, useState } from 'react';
import { osValueOf, type Spec } from './recommendation';
import {
  CPU_TIERS,
  EMPTY_CONDITION,
  cpuFilterLabel,
  fetchProductsFor,
  type Product,
  type ProductSpecItem,
  type SearchCondition,
  type SortType,
} from './products';
import './ProductListView.css';

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

type Props = {
  purposeCode: string;
  // 결과 화면이 보여주고 있던 OS 의 권장안이다.
  specs: Spec[];
  onBack: () => void;
};

export function ProductListView({ purposeCode, specs, onBack }: Props) {
  // 권장안이 둘이면 두 목록을 합쳐야 해서 서버 추천순을 이어갈 수 없다.
  // 원본도 견줄 기준이 없으면 추천순을 감추고 가격순으로 연다.
  const recommendable = specs.length === 1;
  const [sort, setSort] = useState<SortType>(recommendable ? 'RECOMMENDED' : 'PRICE_ASC');
  const [condition, setCondition] = useState<SearchCondition>(EMPTY_CONDITION);
  const [filterOpen, setFilterOpen] = useState(false);
  // 필터 선택지와 "N개 중 M개" 는 조건을 걸기 전 목록을 알아야 만들 수 있다.
  const [base, setBase] = useState<Product[]>([]);
  const [matched, setMatched] = useState<Product[]>([]);

  // specs 는 부모가 매번 새로 만드는 배열이라 참조로는 비교할 수 없다. OS 조합으로 본다.
  const specKey = specs.map(osValueOf).join(',');

  useEffect(() => {
    fetchProductsFor(purposeCode, specs, 'PRICE_ASC', EMPTY_CONDITION)
      .then(setBase)
      .catch(() => setBase([]));
  }, [purposeCode, specKey]);

  useEffect(() => {
    fetchProductsFor(purposeCode, specs, sort, condition)
      .then(setMatched)
      .catch(() => setMatched([]));
  }, [purposeCode, specKey, sort, condition]);

  const options = buildFilterOptions(base, specs);
  const hasCondition = Object.values(condition).some((value) => value !== null);
  const count = matched.length === base.length ? `${base.length}개` : `${base.length}개 중 ${matched.length}개`;

  const change = (field: keyof SearchCondition, value: SearchCondition[keyof SearchCondition]) => {
    setCondition((previous) => ({ ...previous, [field]: value }));
  };

  return (
    <section className="view-panel product-list-view" id="product-list-view" aria-labelledby="product-list-title">
      <button className="text-button view-back-button" id="product-list-back-button" type="button" onClick={onBack}>
        ← 권장 사양으로
      </button>
      <div className="product-list-heading">
        <div>
          <p className="eyebrow" id="product-list-eyebrow">조건에 맞는 제품</p>
          <h2 id="product-list-title">추천 노트북</h2>
          <p className="section-description" id="product-list-description">
            확정한 사양을 모두 충족하는 제품만 보여드려요.
          </p>
        </div>
        <div className="product-list-controls">
          <button
            className="product-list-control product-filter-toggle"
            id="product-filter-toggle"
            type="button"
            aria-expanded={filterOpen}
            aria-controls="product-filter-panel"
            onClick={() => setFilterOpen(!filterOpen)}
          >
            검색 조건
          </button>
          <label className="product-list-control product-list-sort" htmlFor="product-sort">
            <span className="product-list-control__label">정렬</span>
            <select id="product-sort" value={sort} onChange={(event) => setSort(event.target.value as SortType)}>
              {SORT_LABELS.map(({ value, label }) => (
                <option value={value} key={value} hidden={value === 'RECOMMENDED' && !recommendable}>
                  {label}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      {filterOpen && (
        <div className="product-filter-panel" id="product-filter-panel">
          <Filter
            field="maxPrice"
            label="가격"
            value={condition.maxPrice === null ? '' : String(condition.maxPrice)}
            choices={options.maxPrice.map((price) => ({ value: String(price), label: `${formatPrice(price)} 이하` }))}
            onChange={(raw) => change('maxPrice', raw === '' ? null : Number(raw))}
          />
          <CpuFilter
            groups={options.cpu}
            value={condition.cpu === null ? '' : `${condition.cpu.os}:${condition.cpu.tier}`}
            onChange={(raw) => {
              const [os, tier] = raw.split(':');
              change('cpu', raw === '' ? null : { os, tier });
            }}
          />
          <Filter
            field="memoryGb"
            label="RAM"
            value={condition.memoryGb === null ? '' : String(condition.memoryGb)}
            choices={options.memoryGb.map((memory) => ({ value: String(memory), label: `${memory}GB 이상` }))}
            onChange={(raw) => change('memoryGb', raw === '' ? null : Number(raw))}
          />
          <Filter
            field="storageGb"
            label="저장장치"
            value={condition.storageGb === null ? '' : String(condition.storageGb)}
            choices={options.storageGb.map((storage) => ({
              value: String(storage),
              label: `${formatStorage(storage)} 이상`,
            }))}
            onChange={(raw) => change('storageGb', raw === '' ? null : Number(raw))}
          />
        </div>
      )}

      <p className="product-list-caption" id="product-list-caption">{count}</p>

      <div className="product-list" id="product-list" aria-live="polite">
        {matched.length === 0 ? (
          <EmptyResult condition={condition} hasCondition={hasCondition} onReset={() => setCondition(EMPTY_CONDITION)}/>
        ) : (
          matched.map((product) => <ProductCard product={product} key={product.id}/>)
        )}
      </div>
    </section>
  );
}

function ProductCard({ product }: { product: Product }) {
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
        {/* 제품 상세 화면이 아직 없어 눌리지 않는다. */}
        <button className="button button--primary button--wide product-card__detail" type="button" disabled>
          상세 보기
        </button>
      </div>
    </article>
  );
}

type EmptyResultProps = {
  condition: SearchCondition;
  hasCondition: boolean;
  onReset: () => void;
};

function EmptyResult({ condition, hasCondition, onReset }: EmptyResultProps) {
  if (hasCondition) {
    return (
      <div className="empty-result">
        <p>조건에 맞는 제품이 없습니다.</p>
        <p className="empty-result__condition">적용한 검색 조건 — {describeCondition(condition)}</p>
        <button className="button button--secondary" type="button" onClick={onReset}>검색 조건 지우기</button>
      </div>
    );
  }
  return (
    <div className="empty-result">
      <p>조건에 맞는 제품이 없습니다. 사양을 직접 조정해 다시 확인해 주세요.</p>
      {/* 전체 제품 목록은 아직 갈 곳이 없어 눌리지 않는다. */}
      <button className="button button--secondary" type="button" disabled>전체 제품 보기</button>
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
              <option value={`${os}:${tier}`} key={tier}>{cpuFilterLabel(os, tier)}</option>
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
function buildFilterOptions(products: Product[], specs: Spec[]) {
  return {
    maxPrice: PRICE_STEPS.filter((step) => products.some((product) => product.minPrice !== null && product.minPrice <= step)),
    cpu: specs.map(osValueOf).map((os) => ({ os, tiers: CPU_TIERS[os] ?? [] })),
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

function describeCondition({ maxPrice, cpu, memoryGb, storageGb }: SearchCondition): string {
  return [
    maxPrice !== null ? `${formatPrice(maxPrice)} 이하` : '',
    cpu !== null ? cpuFilterLabel(cpu.os, cpu.tier) : '',
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

// 판매 중인 판매처가 없으면 최저가가 비어 온다. 원본에는 없던 상태다.
function formatPrice(value: number | null): string {
  return value === null ? '가격 정보 없음' : `${value.toLocaleString('ko-KR')}원`;
}
