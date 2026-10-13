import type { MouseEvent, ReactNode } from 'react';
import { go } from './routes';

// 실제 href를 두어 새 탭 열기를 지원하고, 일반 이동은 기존 스크롤·뒤로 가기 기록을 남긴다.
export function LandingLink({ href, className, children }: { href: string; className: string; children: ReactNode }) {
  function navigate(event: MouseEvent<HTMLAnchorElement>) {
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return;
    }
    event.preventDefault();
    go(href);
  }

  return (
    <a className={className} href={href} onClick={navigate}>
      {children}
    </a>
  );
}
