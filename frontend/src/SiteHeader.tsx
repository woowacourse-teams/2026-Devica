import './SiteLayout.css';

type Props = {
  onProductList: () => void;
};

export function SiteHeader({ onProductList }: Props) {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <a className="site-logo" href="/">DEVICA</a>
        <nav className="site-nav" aria-label="주요 메뉴">
          <a className="site-nav__item" href="/guide">가이드</a>
          {/* 원본은 같은 페이지에서 화면만 바꾼다. 주소로 들어온 경우를 위해 href 는 남긴다. */}
          <a
            className="site-nav__item"
            id="nav-product-list-button"
            href="/?view=products"
            onClick={(event) => {
              event.preventDefault();
              onProductList();
            }}
          >
            제품 목록
          </a>
        </nav>
      </div>
    </header>
  );
}
