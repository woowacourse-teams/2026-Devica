import './SiteLayout.css';

export function SiteHeader() {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <a className="site-logo" href="/">DEVICA</a>
        <nav className="site-nav" aria-label="주요 메뉴">
          <a className="site-nav__item" href="/guide">가이드</a>
          {/* 원본은 이 링크를 가로채 제품 목록 화면으로 전환한다. 그 화면을 옮기기 전까지는 링크 그대로 둔다. */}
          <a className="site-nav__item" id="nav-product-list-button" href="/?view=products">제품 목록</a>
        </nav>
      </div>
    </header>
  );
}
