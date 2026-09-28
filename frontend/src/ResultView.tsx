import { ChoiceGroup } from './ChoiceGroup';
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

export type ResultOs = 'MAC' | 'WINDOWS' | 'BOTH';

type Props = {
  specs: Spec[];
  os: ResultOs;
  onChangeOs: (os: ResultOs) => void;
  onSearch: () => void;
};

export function ResultView({ specs, os, onChangeOs, onSearch }: Props) {
  const visible = os === 'BOTH' ? specs : specs.filter((spec) => osValueOf(spec) === os);

  // 원본은 두 OS 권장안을 늘 들고 있어 토글이 언제나 동작한다. 서버는 답에 맞는 OS 만 주므로
  // 받은 것만 고를 수 있게 한다. 하나뿐이면 고를 게 없어 토글을 감춘다.
  const choices = specs.map((spec) => ({
    value: osValueOf(spec),
    label: OS_LABEL[osValueOf(spec)] ?? osValueOf(spec),
  }));
  if (choices.length > 1) {
    choices.push({ value: 'BOTH', label: '둘 다' });
  }

  return (
    <section className="view-panel result-view" id="result-view" aria-labelledby="result-title">
      <p className="eyebrow">내 권장 사양</p>
      <h2 id="result-title">권장 사양을 확인해 보세요</h2>
      <p className="section-description">권장 사양을 확인하고 필요에 따라 직접 수정할 수 있습니다.</p>

      {choices.length > 1 && (
        <div className="result-os-control" role="group" aria-label="표시할 OS">
          <span>표시할 OS</span>
          <span id="result-os-control">
            <ChoiceGroup
              segmented
              choices={choices}
              value={os}
              // 표시할 OS 는 비울 수 없다. 고른 것을 다시 눌러도 그대로 둔다.
              onChange={(value) => value !== null && onChangeOs(value as ResultOs)}
            />
          </span>
        </div>
      )}

      <div className="spec-list" id="spec-list">
        {visible.map((spec) => (
          <SpecCard key={osValueOf(spec)} spec={spec} />
        ))}
      </div>

      {/* 사양 직접 수정은 아직 갈 곳이 없어 눌리지 않는다. */}
      <button className="button button--secondary button--wide" id="edit-spec-button" type="button" disabled>
        사양 직접 수정
      </button>
      <button className="button button--primary button--wide" id="product-button" type="button" onClick={onSearch}>
        확정한 사양으로 제품 검색
      </button>
    </section>
  );
}

function SpecCard({ spec }: { spec: Spec }) {
  const itemOf = (code: string): SpecItem | undefined => spec.items.find((item) => item.code === code);

  return (
    <article className="bordered-panel spec-card">
      <h3>{OS_LABEL[osValueOf(spec)] ?? osValueOf(spec)} 권장 사양</h3>
      {ROWS.map(({ code, label }) => {
        const item = itemOf(code);
        if (item === undefined) {
          return null;
        }
        return (
          <section className="bordered-panel spec-row" key={code} data-os={osValueOf(spec)}>
            <span className="spec-row__label">{label}</span>
            <strong className="spec-row__value">{item.displayValue}</strong>
            {/* 한 항목에 조건이 여럿 걸리면 근거도 여럿이라 줄을 나눠 적는다. */}
            {item.reasons.map((reason) => (
              <p className="spec-row__reason" key={reason}>
                {reason}
              </p>
            ))}
          </section>
        );
      })}
    </article>
  );
}
