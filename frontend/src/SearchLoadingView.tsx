import './SearchLoadingView.css';

type Props = {
  eyebrow: string;
  // 지금 찾고 있는 조건을 한 줄씩 보여 준다. 제품 추천 결과를 고쳤으면 고친 값이다.
  lines: string[];
};

export function SearchLoadingView({ eyebrow, lines }: Props) {
  return (
    <section className="view-panel search-loading-view" id="search-loading-view" aria-labelledby="search-loading-title">
      <div className="search-loading" aria-live="polite" aria-busy="true">
        <span className="search-loading__mark" aria-hidden="true">
          <span className="spinner" />
        </span>
        <p className="search-loading__eyebrow">{eyebrow}</p>
        <h1 id="search-loading-title">조건에 맞는 제품을 찾고 있습니다</h1>
        <div className="search-loading__specs" id="search-loading-specs">
          {lines.map((line) => (
            <p className="search-loading__spec" key={line}>
              {line}
            </p>
          ))}
        </div>
      </div>
    </section>
  );
}
