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
        {/* 원본은 같은 페이지에서 화면만 바꾼다. 주소로 들어온 경우를 위해 href 는 남긴다. */}
        <nav className="site-nav" aria-label="주요 메뉴">
          <a
            className="site-nav__item"
            id="nav-guide-button"
            href="/?view=faq"
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
