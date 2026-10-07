import type { MouseEvent } from 'react';
import './SiteLayout.css';

type Props = {
  onGuide: () => void;
  onProductList: () => void;
  boardHref: string;
  onBoard: () => void;
  boardActive: boolean;
  home?: boolean;
};

function navigateInside(event: MouseEvent<HTMLAnchorElement>, navigate: () => void) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return;
  }
  event.preventDefault();
  navigate();
}

export function SiteHeader({ onGuide, onProductList, boardHref, onBoard, boardActive, home = false }: Props) {
  return (
    <header className={home ? 'site-header site-header--home' : 'site-header'}>
      <div className="site-header__inner">
        <a className="site-logo" href="/">
          DEVICA
        </a>
        {/* 누르면 앱 안에서 화면을 옮긴다. 새 탭으로 열 때를 위해 href 는 실제 주소를 둔다. */}
        <nav className="site-nav" aria-label="주요 메뉴">
          <a
            className="site-nav__item"
            id="nav-board-button"
            href={boardHref}
            aria-current={boardActive ? 'page' : undefined}
            onClick={(event) => navigateInside(event, onBoard)}
          >
            질문 게시판
          </a>
          <a
            className="site-nav__item"
            id="nav-product-list-button"
            href="/products"
            onClick={(event) => navigateInside(event, onProductList)}
          >
            제품 목록
          </a>
          <a
            className="site-nav__item"
            id="nav-guide-button"
            href="/faq"
            onClick={(event) => navigateInside(event, onGuide)}
          >
            FAQ
          </a>
        </nav>
      </div>
    </header>
  );
}
