import { osValueOf, type Spec } from './recommendation';

// 원본은 PROCESSOR · MEMORY · STORAGE 만 보여주고 OS 는 카드 제목으로 쓴다.
// 권장 사양의 CPU 는 제품이 탑재한 CPU 와 코드가 다르다. 등급을 요구하는 쪽이 REQUIRED_CPU 다.
const ROWS = [
  { code: 'REQUIRED_CPU', label: 'PROCESSOR' },
  { code: 'MEMORY', label: 'MEMORY' },
  { code: 'STORAGE', label: 'STORAGE' },
];

const OS_ICON_PATHS: Record<string, string[]> = {
  MAC: ['M15 6v12a3 3 0 1 0 3-3H6a3 3 0 1 0 3 3V6a3 3 0 1 0-3 3h12a3 3 0 1 0-3-3'],
  WINDOWS: ['M4 4h7v7H4z', 'M13 4h7v7h-7z', 'M4 13h7v7H4z', 'M13 13h7v7h-7z'],
};

export function OsIcon({ spec }: { spec: Spec }) {
  const paths = OS_ICON_PATHS[osValueOf(spec)];
  if (paths === undefined) {
    return null;
  }
  return (
    <span className="os-icon" aria-hidden="true">
      <svg
        aria-hidden="true"
        width="20"
        height="20"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        {paths.map((d) => (
          <path d={d} key={d} />
        ))}
      </svg>
    </span>
  );
}

type Props = {
  spec: Spec;
};

export function BaselineCard({ spec }: Props) {
  const itemOf = (code: string) => spec.items.find((item) => item.code === code);
  const os = itemOf('OS');

  return (
    <article className="spec-panel baseline-card">
      <h2 className="os-title">
        <OsIcon spec={spec} />
        {os?.displayValue ?? ''}
      </h2>
      <dl className="spec-tiles">
        {ROWS.map(({ code, label }) => {
          const item = itemOf(code);
          if (item === undefined) {
            return null;
          }
          return (
            <div className="spec-tile" key={code}>
              <dt>{label}</dt>
              <dd>{item.displayValue}</dd>
            </div>
          );
        })}
      </dl>
    </article>
  );
}
