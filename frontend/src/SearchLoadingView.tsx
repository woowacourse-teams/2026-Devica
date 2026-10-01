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
        <span className="spinner" aria-hidden="true" />
        <h2 id="search-loading-title">조건에 맞는 제품을 찾고 있습니다</h2>
        <p className="eyebrow">사양 대조 중</p>
        <div className="search-loading__specs" id="search-loading-specs">
          {specs.map((spec) => (
            <p className="search-loading__spec" key={spec.items.find((item) => item.code === 'OS')?.value ?? ''}>
              <ChipIcon />
              <span>
                {LINE_CODES.map((code) => spec.items.find((item) => item.code === code)?.displayValue)
                  .filter((value) => value !== undefined)
                  .join(' · ')}
              </span>
            </p>
          ))}
        </div>
      </div>
    </section>
  );
}

// 와이어프레임의 칩 아이콘. 글자 크기를 따라가고 색은 글자색을 쓴다.
function ChipIcon() {
  return (
    <svg className="search-loading__icon" viewBox="0 0 16 16" aria-hidden="true">
      <rect x="4" y="4" width="8" height="8" fill="none" stroke="currentColor" />
      <rect x="6.5" y="6.5" width="3" height="3" fill="currentColor" />
      <path d="M6 1v3M10 1v3M6 12v3M10 12v3M1 6h3M1 10h3M12 6h3M12 10h3" stroke="currentColor" />
    </svg>
  );
}
