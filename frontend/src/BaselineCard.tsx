import { osValueOf, type Spec } from './recommendation';
import { SpecRationale } from './SpecRationale';

// OS 는 카드 제목으로 쓰고 나머지를 이 순서로 보여준다. GPU·화면은 정의서가 요구할 때만 서버가 준다.
// 권장 사양의 CPU 는 제품이 탑재한 CPU 와 코드가 다르다. 등급을 요구하는 쪽이 REQUIRED_CPU 다.
export const SPEC_ROWS: { code: string; label: string }[] = [
  { code: 'REQUIRED_CPU', label: 'PROCESSOR' },
  { code: 'MEMORY', label: 'MEMORY' },
  { code: 'STORAGE', label: 'STORAGE' },
  { code: 'REQUIRED_GPU', label: 'GPU' },
  { code: 'COLOR_GAMUT', label: 'DISPLAY' },
];

const OS_ICON_PATHS: Record<string, string> = {
  MAC: 'M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701',
  WINDOWS: 'M0 0h11.377v11.372H0zm12.623 0H24v11.372H12.623zM0 12.623h11.377V24H0zm12.623 0H24V24H12.623z',
};

export function OsIcon({ spec }: { spec: Spec }) {
  const path = OS_ICON_PATHS[osValueOf(spec)];
  if (path === undefined) {
    return null;
  }
  return (
    <span className="os-icon" aria-hidden="true">
      <svg aria-hidden="true" width="18" height="18" viewBox="0 0 24 24" fill="currentColor">
        <path d={path} />
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
  const osLabel = os?.displayValue ?? '';

  return (
    <article className="spec-panel baseline-card">
      <div className="rationale-heading">
        <h2 className="os-title">
          <OsIcon spec={spec} />
          {osLabel}
        </h2>
        {os !== undefined && <SpecRationale item={os} osLabel={osLabel} />}
      </div>
      <dl className="spec-tiles">
        {SPEC_ROWS.map(({ code, label }) => {
          const item = itemOf(code);
          if (item === undefined) {
            return null;
          }
          return (
            <div className="spec-tile" key={code}>
              <dt>{label}</dt>
              <dd>
                {item.displayValue}
                <SpecRationale item={item} osLabel={osLabel} />
              </dd>
            </div>
          );
        })}
      </dl>
    </article>
  );
}
