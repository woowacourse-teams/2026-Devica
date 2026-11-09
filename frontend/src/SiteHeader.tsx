import { useEffect, useState } from 'react';
import { usePathname } from 'wouter/use-browser-location';
import { track } from './analytics';
import { DESKTOP_MEDIA, go, navigateInside, PATHS } from './routes';
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
    const close = () => setMenuOpen(false);
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') close();
    };
    const closeOnDesktop = () => {
      if (window.matchMedia(DESKTOP_MEDIA).matches) close();
    };
    const covered = document.querySelectorAll<HTMLElement>('#main-content, .site-footer');
    for (const element of covered) element.inert = true;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', closeOnEscape);
    window.addEventListener('resize', closeOnDesktop);
    return () => {
      for (const element of covered) element.inert = false;
      document.body.style.overflow = '';
      document.removeEventListener('keydown', closeOnEscape);
      window.removeEventListener('resize', closeOnDesktop);
    };
  }, [menuOpen]);

  function toggleMenu() {
    if (!menuOpen && !boardActive) {
      track('board_entry_viewed', {});
    }
    setMenuOpen(!menuOpen);
  }

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
          onClick={toggleMenu}
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
        <nav
          className="site-nav"
          id="site-nav"
          aria-label="주요 메뉴"
          onClickCapture={(event) => {
            if (event.target instanceof Element && event.target.closest('a')) setMenuOpen(false);
          }}
        >
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
