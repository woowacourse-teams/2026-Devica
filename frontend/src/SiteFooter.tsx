import { go } from './routes';
import { navigateInside } from './SiteHeader';
import './SiteLayout.css';

const LINKS = [
  ['/faq', '자주 묻는 질문'],
  ['/board', '질문 게시판'],
  ['/products', '제품 목록'],
] as const;

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <span className="site-footer__brand">DEVICA</span>
        <nav className="site-footer__nav" aria-label="하단 메뉴">
          {LINKS.map(([href, label]) => (
            <a key={href} href={href} onClick={(event) => navigateInside(event, () => go(href))}>
              {label}
            </a>
          ))}
        </nav>
        <p className="site-footer__contact">
          문의 <a href="mailto:devica.team@gmail.com">devica.team@gmail.com</a> · © 2026 Devica
        </p>
      </div>
    </footer>
  );
}
