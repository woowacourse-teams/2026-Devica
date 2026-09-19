import { useEffect } from 'react';
import type { Spec } from './recommendation';
import './SearchLoadingView.css';

// 대조는 곧 끝나지만, 답이 실제로 쓰였다는 걸 보여주려고 화면을 붙잡아 둔다.
const HOLD_MS = 1800;

// CPU 라벨 안에 "/" 가 들어가는 경우가 있어("… 258V / … 445급") 구분자는 "·" 를 쓴다.
const LINE_CODES = ['REQUIRED_CPU', 'MEMORY', 'STORAGE', 'OS'];

type Props = {
  specs: Spec[];
  onDone: () => void;
};

export function SearchLoadingView({ specs, onDone }: Props) {
  useEffect(() => {
    const timer = setTimeout(onDone, HOLD_MS);
    // 도중에 다른 화면으로 빠져나가면 예약된 전환이 나중에 그 화면을 덮어쓴다.
    return () => clearTimeout(timer);
  }, [onDone]);

  return (
    <section className="view-panel search-loading-view" id="search-loading-view" aria-labelledby="search-loading-title">
      <div className="bordered-panel search-loading" aria-live="polite" aria-busy="true">
        <span className="spinner" aria-hidden="true"/>
        <h2 id="search-loading-title">조건에 맞는 제품을 찾고 있습니다</h2>
        <p className="eyebrow">사양 대조 중</p>
        <div className="search-loading__specs" id="search-loading-specs">
          {specs.map((spec) => (
            <p className="search-loading__spec" key={spec.items.find((item) => item.code === 'OS')?.value ?? ''}>
              {LINE_CODES
                .map((code) => spec.items.find((item) => item.code === code)?.displayValue)
                .filter((value) => value !== undefined)
                .join(' · ')}
            </p>
          ))}
        </div>
      </div>
    </section>
  );
}
