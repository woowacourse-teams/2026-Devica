import './SiteLayout.css';

type Props = {
  onGuide: () => void;
  onProductList: () => void;
};

export function SiteHeader({ onGuide, onProductList }: Props) {
  return (
    <header className="site-header">
      <div className="site-header__inner">
        <a className="site-logo" href="/">
          DEVICA
        </a>
        {/* 누르면 앱 안에서 화면을 옮긴다. 새 탭으로 열 때를 위해 href 는 실제 주소를 둔다. */}
        <nav className="site-nav" aria-label="주요 메뉴">
          <a
            className="site-nav__item"
            id="nav-guide-button"
            href="/faq"
            onClick={(event) => {
              event.preventDefault();
              onGuide();
            }}
          >
            가이드
          </a>
          <a
            className="site-nav__item"
            id="nav-product-list-button"
            href="/products"
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
