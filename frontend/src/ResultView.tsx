import { useState } from 'react';
import { ChoiceGroup } from './ChoiceGroup';
import { type Fetched, useFetched } from './fetched';
import {
  type CpuTier,
  editChoices,
  editedSpec,
  editProperties,
  fetchMinPrice,
  formatPrice,
  type SpecEdits,
  specCondition,
} from './products';
import { osValueOf, type Spec, type SpecItem } from './recommendation';
import { boardUrl, go, navigateInside } from './routes';
import { PURPOSE_DISPLAY } from './selection';
import './ResultView.css';

// OS 는 카드 제목으로 쓰고 나머지를 이 순서로 보여준다. GPU·화면은 정의서가 요구할 때만 서버가 준다.
const ROWS: { code: string; label: string }[] = [
  { code: 'REQUIRED_CPU', label: 'PROCESSOR' },
  { code: 'MEMORY', label: 'MEMORY' },
  { code: 'STORAGE', label: 'STORAGE' },
  { code: 'REQUIRED_GPU', label: 'GPU' },
  { code: 'COLOR_GAMUT', label: 'DISPLAY' },
];

const OS_LABEL: Record<string, string> = { MAC: 'Mac', WINDOWS: 'Windows' };

// 의견은 GitHub 이슈 양식으로 받는다. result_url 은 양식의 필드 id 라, 바꾸면 양식 파일도 함께 고친다.
const FEEDBACK_FORM_URL = 'https://github.com/woowacourse-teams/2026-Devica/issues/new';

// 결과 화면 주소에 답이 모두 담겨 있어, 이 주소로 같은 결과를 재현한다.
// 사용자가 제목에서 망설이지 않게 제목도 채운다.
function feedbackUrl(): string {
  const params = new URLSearchParams({
    template: 'product-recommendation-rule-feedback.yml',
    title: '[제품 추천 의견] 결과 화면에서 남긴 의견',
    result_url: window.location.href,
  });
  return `${FEEDBACK_FORM_URL}?${params}`;
}

export type ResultOs = 'MAC' | 'WINDOWS' | 'BOTH';

type Props = {
  categoryCode: string;
  purposeCode: string;
  specs: Spec[];
  os: ResultOs;
  // 사용자가 고친 사양과, CPU 선택지를 만들 등급이다. 등급을 받지 못했으면 CPU 는 고칠 수 없다.
  edits: SpecEdits;
  tiers: CpuTier[] | null;
  tiersFailed: boolean;
  onRetryTiers: () => void;
  onChangeOs: (os: ResultOs) => void;
  // 값이 null 이면 그 항목을 권장 값으로 되돌린다.
  onEdit: (os: string, code: string, value: string | null) => void;
  // 그 OS 의 고친 값만 지운다. 보이지 않는 OS 의 고친 값은 건드리지 않는다.
  onResetEdits: (os: string) => void;
  onSearch: () => void;
  onReanswer?: () => void;
};

export function ResultView({
  categoryCode,
  purposeCode,
  specs,
  os,
  edits,
  tiers,
  tiersFailed,
  onRetryTiers,
  onChangeOs,
  onEdit,
  onResetEdits,
  onSearch,
  onReanswer,
}: Props) {
  const visible = os === 'BOTH' ? specs : specs.filter((spec) => osValueOf(spec) === os);
  const edited = visible.map((spec) => editedSpec(spec, edits, tiers));
  const minPrices = useFetched(`min-price:${categoryCode}:${JSON.stringify(edited.map(specCondition))}`, () =>
    Promise.all(edited.map((spec) => fetchMinPrice(categoryCode, spec))),
  );
  const minPriceLabels = visible.map((spec) => OS_LABEL[osValueOf(spec)] ?? osValueOf(spec));
  const purposeName = PURPOSE_DISPLAY.find((purpose) => purpose.code === purposeCode)?.name;
  const board = boardUrl(categoryCode, purposeCode);

  const choices = [
    ...specs.map((spec) => ({ value: osValueOf(spec), label: OS_LABEL[osValueOf(spec)] ?? osValueOf(spec) })),
    { value: 'BOTH', label: '둘 다' },
  ];

  return (
    <section className="view-panel flow-view result-view" id="result-view" aria-labelledby="result-title">
      <div className="result-head">
        <div className="flow-head">
          <p className="flow-head__eyebrow">내 제품 추천 결과{purposeName === undefined ? '' : ` · ${purposeName}`}</p>
          <h1 id="result-title">제품 추천 결과를 확인해 보세요</h1>
        </div>
        <div className="result-os-control">
          <span className="result-os-control__label" aria-hidden="true">
            표시할 OS
          </span>
          <ChoiceGroup
            segmented
            label="표시할 OS"
            choices={choices}
            value={os}
            // 표시할 OS 는 비울 수 없다. 고른 것을 다시 눌러도 그대로 둔다.
            onChange={(value) => value !== null && onChangeOs(value as ResultOs)}
          />
        </div>
      </div>

      {onReanswer !== undefined && (
        <div className="result-outdated" role="status">
          <p className="notice">질문이 바뀌어 지금 질문에 맞춰 다시 읽었어요. 처음 본 결과와 다를 수 있어요.</p>
          <button className="button" type="button" onClick={onReanswer}>
            다시 답하기
          </button>
        </div>
      )}

      <div className="result-body">
        <div className="result-main" id="spec-list">
          {visible.map((spec, index) => (
            <SpecCard
              key={osValueOf(spec)}
              spec={spec}
              edited={edited[index]}
              tiers={tiers}
              tiersFailed={tiersFailed}
              onRetryTiers={onRetryTiers}
              onReset={() => onResetEdits(osValueOf(spec))}
              onEdit={(code, value) =>
                onEdit(
                  osValueOf(spec),
                  code,
                  value === spec.items.find((item) => item.code === code)?.value ? null : value,
                )
              }
            />
          ))}
          <p className="result-feedback">
            제품 추천 결과가 나와 맞지 않나요?{' '}
            <a href={feedbackUrl()} target="_blank" rel="noopener noreferrer">
              의견 남기기 ↗
            </a>{' '}
            <span className="result-feedback__hint">GitHub 계정이 필요해요.</span>
          </p>
        </div>

        <div className="result-side">
          <section className="sticky-actions result-price" aria-labelledby="result-price-title">
            <h2 className="result-price__title" id="result-price-title">
              이 사양으로 살 수 있는 최저가
            </h2>
            <MinPrices labels={minPriceLabels} prices={minPrices} />
            <p className="result-price__basis">추천 사양 이상, 지금 판매 중인 노트북 기준</p>
            <button
              className="button button--primary button--wide"
              id="product-button"
              type="button"
              onClick={onSearch}
            >
              추천 결과로 제품 검색 <span aria-hidden="true">→</span>
            </button>
          </section>
          <a className="result-board" href={board} onClick={(event) => navigateInside(event, () => go(board))}>
            <span className="result-board__text">
              <b>아직 고민된다면</b>
              <span>질문 게시판에서 다른 개발자와 나눠요</span>
            </span>
            <span className="result-board__arrow" aria-hidden="true">
              →
            </span>
          </a>
        </div>
      </div>
    </section>
  );
}

type MinPricesProps = {
  labels: string[];
  prices: Fetched<(number | null)[]>;
};

function MinPrices({ labels, prices }: MinPricesProps) {
  const priceText = (index: number) => {
    if (prices.data === null) {
      return '계산 중…';
    }
    const price = prices.data[index];
    return price === null ? '판매 중인 제품 없음' : `${formatPrice(price)}부터`;
  };

  if (prices.failed) {
    return (
      <p className="result-price__failed" role="status">
        최저가를 불러오지 못했어요.{' '}
        <button className="text-button" type="button" onClick={prices.retry}>
          다시 시도
        </button>
      </p>
    );
  }
  return (
    <ul className="result-price__list" role="status">
      {labels.map((label, index) => (
        <li key={label}>
          <span>
            {label}
            <span className="result-price__suffix"> 최저가</span>
          </span>
          <strong>{priceText(index)}</strong>
        </li>
      ))}
    </ul>
  );
}

type SpecCardProps = {
  spec: Spec;
  edited: Spec;
  tiers: CpuTier[] | null;
  tiersFailed: boolean;
  onRetryTiers: () => void;
  onEdit: (code: string, value: string) => void;
  onReset: () => void;
};

function SpecCard({ spec, edited, tiers, tiersFailed, onRetryTiers, onEdit, onReset }: SpecCardProps) {
  const [editing, setEditing] = useState(false);
  const os = osValueOf(spec);
  const titleId = `spec-card-title-${os}`;
  const itemOf = (target: Spec, code: string): SpecItem | undefined => target.items.find((item) => item.code === code);
  const rows = ROWS.flatMap(({ code, label }) => {
    const recommended = itemOf(spec, code);
    const item = itemOf(edited, code);
    return recommended === undefined || item === undefined ? [] : [{ code, label, recommended, item }];
  });
  const reasonItems = [itemOf(spec, 'OS'), ...rows.map(({ recommended }) => recommended)];
  const reasons = reasonItems.flatMap((target) =>
    (target?.reasons ?? []).map((reason, order) => ({ key: `${target?.code}-${order}`, reason })),
  );

  return (
    <article className="result-card" aria-labelledby={titleId}>
      <div className="spec-panel result-card__top">
        <div className="result-card__head">
          <h2 id={titleId}>{OS_LABEL[os] ?? os} 제품 추천 결과</h2>
          <span className="result-card__actions">
            {editProperties(spec, edited) !== null && (
              <button className="result-card__button result-card__button--plain" type="button" onClick={onReset}>
                <span aria-hidden="true">↺</span>추천 결과로 되돌리기
              </button>
            )}
            <button
              className={`result-card__button${editing ? ' result-card__button--solid' : ''}`}
              type="button"
              aria-pressed={editing}
              onClick={() => setEditing(!editing)}
            >
              {editing ? '수정 마치기' : '직접 수정'}
            </button>
          </span>
        </div>
        <dl className={editing ? 'result-edit-rows' : 'spec-tiles'}>
          {rows.map(({ code, label, recommended, item }) => {
            const choices = editChoices(code, os, tiers);
            // 권장 값이 선택지에 없어도 되돌릴 수 있게 칩으로 보여 준다.
            const chips = choices.some((choice) => choice.value === recommended.value)
              ? choices
              : [{ value: recommended.value, label: recommended.displayValue }, ...choices];
            return (
              <div className={editing ? 'result-edit-row' : 'spec-tile'} key={code} data-os={os}>
                <dt className="result-row__head">
                  {label}
                  {item !== recommended && (
                    <span className="result-row__badge">직접 수정 · 추천 {recommended.displayValue}</span>
                  )}
                </dt>
                <dd>
                  {editing && choices.length > 0 ? (
                    <ChoiceGroup
                      label={label}
                      choices={chips.map((chip) => ({
                        value: chip.value,
                        label: chip.value === recommended.value ? `${chip.label} · 추천` : chip.label,
                      }))}
                      value={item.value}
                      // 고른 칩을 다시 눌러도 그대로 둔다. 되돌리기는 추천 칩이나 카드의 되돌리기로 한다.
                      onChange={(value) => value !== null && onEdit(code, value)}
                    />
                  ) : (
                    item.displayValue
                  )}
                  {/* CPU 선택지와 고친 등급의 이름은 등급을 받아야 알 수 있다. 고치려 하거나 고친 값이 있을 때만 알린다. */}
                  {code === 'REQUIRED_CPU' && tiers === null && (editing || item !== recommended) && (
                    <span className="result-row__status" role="status">
                      {tiersFailed ? 'CPU 등급을 불러오지 못했어요. ' : 'CPU 등급을 불러오는 중이에요.'}
                      {tiersFailed && (
                        <button className="text-button" type="button" onClick={onRetryTiers}>
                          다시 시도
                        </button>
                      )}
                    </span>
                  )}
                </dd>
              </div>
            );
          })}
        </dl>
      </div>
      <div className="result-card__bottom">
        {editing ? (
          <p className="result-card__hint">값을 바꾸면 최저가를 바로 다시 계산해요.</p>
        ) : (
          <>
            {spec.notes.length > 0 && (
              <section className="result-card__notes" aria-label="안내">
                {spec.notes.map((note) => (
                  <p className="result-card__note" key={note}>
                    {note}
                  </p>
                ))}
              </section>
            )}
            {reasons.length > 0 && (
              <>
                <h3>이렇게 추천한 이유</h3>
                <ul className="result-reasons">
                  {reasons.map(({ key, reason }) => (
                    <li key={key}>
                      <svg
                        aria-hidden="true"
                        width="18"
                        height="18"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="3"
                        strokeLinecap="round"
                        strokeLinejoin="round"
                      >
                        <path d="M5 12l5 5L20 7" />
                      </svg>
                      <span>{reason}</span>
                    </li>
                  ))}
                </ul>
              </>
            )}
          </>
        )}
      </div>
    </article>
  );
}
