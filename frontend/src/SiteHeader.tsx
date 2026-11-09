import { type MouseEvent, useEffect, useState } from 'react';
import { usePathname } from 'wouter/use-browser-location';
import { go, PATHS } from './routes';
import { ServiceEntryLink } from './ServiceEntryLink';
import './SiteLayout.css';

type Props = {
  onGuide: () => void;
  onProductList: () => void;
  boardHref: string;
  onBoard: () => void;
  boardActive: boolean;
  productListActive: boolean;
  guideActive: boolean;
};

export function navigateInside(event: MouseEvent<HTMLAnchorElement>, navigate: () => void) {
  if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return;
  }
  event.preventDefault();
  navigate();
}

export function SiteHeader({
  onGuide,
  onProductList,
  boardHref,
  onBoard,
  boardActive,
  productListActive,
  guideActive,
}: Props) {
  const pathname = usePathname();
  const [menuOpen, setMenuOpen] = useState(false);

  // biome-ignore lint/correctness/useExhaustiveDependencies: 화면이 바뀌면 메뉴를 닫는다.
  useEffect(() => {
    setMenuOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!menuOpen) return;
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false);
    };
    document.addEventListener('keydown', closeOnEscape);
    return () => document.removeEventListener('keydown', closeOnEscape);
  }, [menuOpen]);

  return (
    <header className="site-header" data-menu-open={menuOpen}>
      <div className="site-header__inner">
        <a className="site-logo" href="/">
          DEVICA
        </a>
        <button
          className="site-menu-button"
          type="button"
          aria-label={menuOpen ? '메뉴 닫기' : '메뉴 열기'}
          aria-expanded={menuOpen}
          aria-controls="site-nav"
          onClick={() => setMenuOpen((open) => !open)}
        >
          <svg
            aria-hidden="true"
            width="24"
            height="24"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          >
            <path d={menuOpen ? 'M6 6l12 12M18 6L6 18' : 'M4 7h16M4 12h16M4 17h16'} />
          </svg>
        </button>
        {/* 누르면 앱 안에서 화면을 옮긴다. 새 탭으로 열 때를 위해 href 는 실제 주소를 둔다. */}
        <nav className="site-nav" id="site-nav" aria-label="주요 메뉴" onClickCapture={() => setMenuOpen(false)}>
          <a
            className="site-nav__item"
            id="nav-guide-button"
            href="/faq"
            aria-current={guideActive ? 'page' : undefined}
            onClick={(event) => navigateInside(event, onGuide)}
          >
            자주 묻는 질문
          </a>
          <ServiceEntryLink
            className="site-nav__item"
            id="nav-board-button"
            href={boardHref}
            destination="board"
            entryPoint="header"
            measure={!boardActive}
            aria-current={boardActive ? 'page' : undefined}
            onNavigate={onBoard}
          >
            질문 게시판
          </ServiceEntryLink>
          <a
            className="site-nav__item"
            id="nav-product-list-button"
            href="/products"
            aria-current={productListActive ? 'page' : undefined}
            onClick={(event) => navigateInside(event, onProductList)}
          >
            제품 목록
          </a>
          <div className="site-menu-start">
            <b>내게 맞는 사양 알아보기</b>
            <span>약 2분 · 로그인 없이 시작</span>
            <a
              className="button button--primary button--wide"
              href={PATHS.category}
              onClick={(event) => navigateInside(event, () => go(PATHS.category))}
            >
              제품 추천 시작하기 <span aria-hidden="true">→</span>
            </a>
          </div>
        </nav>
      </div>
    </header>
  );
}
