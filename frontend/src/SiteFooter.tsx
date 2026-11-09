import type { MouseEvent } from 'react';
import { navigateInside } from './routes';
import './SiteLayout.css';

type Props = {
  onGuide: () => void;
  boardHref: string;
  onBoard: () => void;
  onProductList: () => void;
};

export function SiteFooter({ onGuide, boardHref, onBoard, onProductList }: Props) {
  const links: [string, string, () => void][] = [
    ['/faq', '자주 묻는 질문', onGuide],
    [boardHref, '질문 게시판', onBoard],
    ['/products', '제품 목록', onProductList],
  ];
  return (
    <footer className="site-footer">
      <div className="site-footer__inner">
        <span className="site-footer__brand">DEVICA</span>
        <nav className="site-footer__nav" aria-label="하단 메뉴">
          {links.map(([href, label, onNavigate]) => (
            <a
              key={href}
              href={href}
              onClick={(event: MouseEvent<HTMLAnchorElement>) => navigateInside(event, onNavigate)}
            >
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
