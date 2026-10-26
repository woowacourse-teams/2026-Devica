import { useState } from 'react';
import { ChoiceGroup } from './ChoiceGroup';
import { type CpuTier, editChoices, editedSpec, editProperties, type SpecEdits } from './products';
import { osValueOf, type Spec, type SpecItem } from './recommendation';
import './ResultView.css';

// 원본은 OS 행과 나머지 세 항목을 이 순서로 보여준다.
const ROWS: { code: string; label: string }[] = [
  { code: 'OS', label: 'OS' },
  { code: 'REQUIRED_CPU', label: 'PROCESSOR' },
  { code: 'MEMORY', label: 'MEMORY' },
  { code: 'STORAGE', label: 'STORAGE' },
];

const OS_LABEL: Record<string, string> = { MAC: 'Mac', WINDOWS: 'Windows' };

// 의견은 GitHub 이슈 양식으로 받는다. result_url 은 양식의 필드 id 라, 바꾸면 양식 파일도 함께 고친다.
const FEEDBACK_FORM_URL = 'https://github.com/woowacourse-teams/2026-Devica/issues/new';

// 결과 화면 주소에 답이 모두 담겨 있어, 이 주소로 같은 결과를 재현한다.
// 사용자가 제목에서 망설이지 않게 제목도 채운다.
function feedbackUrl(): string {
  const params = new URLSearchParams({
    template: 'product-recommendation-feedback.yml',
    title: '[제품 추천 의견] 결과 화면에서 남긴 의견',
    result_url: window.location.href,
  });
  return `${FEEDBACK_FORM_URL}?${params}`;
}

export type ResultOs = 'MAC' | 'WINDOWS' | 'BOTH';

type Props = {
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
  onResetEdits: () => void;
  onSearch: () => void;
};

export function ResultView({
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
}: Props) {
  const [editing, setEditing] = useState(false);
  const visible = os === 'BOTH' ? specs : specs.filter((spec) => osValueOf(spec) === os);
  const edited = specs.some((spec) => editProperties(spec, editedSpec(spec, edits, tiers)) !== null);

  const choices = [
    ...specs.map((spec) => ({ value: osValueOf(spec), label: OS_LABEL[osValueOf(spec)] ?? osValueOf(spec) })),
    { value: 'BOTH', label: '둘 다' },
  ];

  return (
    <section className="view-panel result-view" id="result-view" aria-labelledby="result-title">
      <p className="eyebrow">내 권장 사양</p>
      <h2 id="result-title">권장 사양을 확인해 보세요</h2>
      <p className="section-description">권장 사양을 확인하고 필요에 따라 직접 수정할 수 있습니다.</p>

      <div className="result-os-control">
        <span>표시할 OS</span>
        <span id="result-os-control">
          <ChoiceGroup
            segmented
            label="표시할 OS"
            choices={choices}
            value={os}
            // 표시할 OS 는 비울 수 없다. 고른 것을 다시 눌러도 그대로 둔다.
            onChange={(value) => value !== null && onChangeOs(value as ResultOs)}
          />
        </span>
      </div>

      <div className="spec-list" id="spec-list">
        {visible.map((spec) => (
          <SpecCard
            key={osValueOf(spec)}
            spec={spec}
            edited={editedSpec(spec, edits, tiers)}
            tiers={tiers}
            tiersFailed={tiersFailed}
            onRetryTiers={onRetryTiers}
            editing={editing}
            onEdit={(code, value) =>
              onEdit(
                osValueOf(spec),
                code,
                value === spec.items.find((item) => item.code === code)?.value ? null : value,
              )
            }
          />
        ))}
      </div>

      {edited && (
        <button className="button button--secondary" id="reset-spec-button" type="button" onClick={onResetEdits}>
          권장 사양으로 되돌리기
        </button>
      )}

      <p className="result-feedback">
        권장 사양이 나와 맞지 않나요?{' '}
        <a href={feedbackUrl()} target="_blank" rel="noopener noreferrer">
          의견 남기기 ↗
        </a>
        <span className="result-feedback__hint">GitHub 계정이 필요해요.</span>
      </p>

      <div className="sticky-actions">
        <button
          className="button button--secondary button--wide"
          id="edit-spec-button"
          type="button"
          aria-pressed={editing}
          onClick={() => setEditing(!editing)}
        >
          {editing ? '수정 마치기' : '사양 직접 수정'}
        </button>
        <button className="button button--primary button--wide" id="product-button" type="button" onClick={onSearch}>
          확정한 사양으로 제품 검색
        </button>
      </div>
    </section>
  );
}

type SpecCardProps = {
  spec: Spec;
  edited: Spec;
  tiers: CpuTier[] | null;
  tiersFailed: boolean;
  onRetryTiers: () => void;
  editing: boolean;
  onEdit: (code: string, value: string) => void;
};

function SpecCard({ spec, edited, tiers, tiersFailed, onRetryTiers, editing, onEdit }: SpecCardProps) {
  const os = osValueOf(spec);
  const itemOf = (target: Spec, code: string): SpecItem | undefined => target.items.find((item) => item.code === code);

  return (
    <article className="bordered-panel spec-card">
      <h3>{OS_LABEL[osValueOf(spec)] ?? osValueOf(spec)} 권장 사양</h3>
      {ROWS.map(({ code, label }) => {
        const recommended = itemOf(spec, code);
        const item = itemOf(edited, code);
        if (recommended === undefined || item === undefined) {
          return null;
        }
        const choices = editChoices(code, os, tiers);
        const id = `spec-edit-${os}-${code}`;
        return (
          <section className="bordered-panel spec-row" key={code} data-os={os}>
            {editing && choices.length > 0 ? (
              <label className="spec-row__edit" htmlFor={id}>
                <span className="spec-row__label">{label}</span>
                <select id={id} value={item.value} onChange={(event) => onEdit(code, event.target.value)}>
                  {/* 권장 값이 선택지에 없어도 지금 값을 보여 준다. */}
                  {!choices.some((choice) => choice.value === recommended.value) && (
                    <option value={recommended.value}>{recommended.displayValue}</option>
                  )}
                  {choices.map((choice) => (
                    <option value={choice.value} key={choice.value}>
                      {choice.value === recommended.value ? `${choice.label} (권장)` : choice.label}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <>
                <span className="spec-row__label">{label}</span>
                <strong className="spec-row__value">{item.displayValue}</strong>
              </>
            )}
            {item !== recommended && (
              <p className="spec-row__reason">직접 고친 값이에요. (권장 사양: {recommended.displayValue})</p>
            )}
            {/* CPU 선택지와 고친 등급의 이름은 등급을 받아야 알 수 있다. 고치려 하거나 고친 값이 있을 때만 알린다. */}
            {code === 'REQUIRED_CPU' && tiers === null && (editing || item !== recommended) && (
              <p className="spec-row__reason" role="status">
                {tiersFailed ? 'CPU 등급을 불러오지 못했어요. ' : 'CPU 등급을 불러오는 중이에요.'}
                {tiersFailed && (
                  <button className="text-button" type="button" onClick={onRetryTiers}>
                    다시 시도
                  </button>
                )}
              </p>
            )}
            {/* 근거가 한 줄이면 그대로 보여주고, 여럿이면 기본으로 접어 둔다. 한 항목에 조건이 여럿 걸리면 근거도 여럿이다. */}
            {item.reasons.length === 1 && <p className="spec-row__reason">{item.reasons[0]}</p>}
            {item.reasons.length > 1 && (
              <details className="spec-row__details">
                <summary>근거 보기</summary>
                {item.reasons.map((reason) => (
                  <p className="spec-row__reason" key={reason}>
                    {reason}
                  </p>
                ))}
              </details>
            )}
          </section>
        );
      })}
    </article>
  );
}
