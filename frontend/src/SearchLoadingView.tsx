import type { Spec } from './recommendation';
import './SearchLoadingView.css';

// CPU 라벨 안에 "/" 가 들어가는 경우가 있어("… 258V / … 445급") 구분자는 "·" 를 쓴다.
const LINE_CODES = ['REQUIRED_CPU', 'MEMORY', 'STORAGE', 'OS'];

type Props = {
  specs: Spec[];
};

export function SearchLoadingView({ specs }: Props) {
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
