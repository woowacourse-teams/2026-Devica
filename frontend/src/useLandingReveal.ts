import { useLayoutEffect, useRef } from 'react';

export function useLandingReveal() {
  const pageRef = useRef<HTMLDivElement>(null);

  useLayoutEffect(() => {
    const page = pageRef.current;
    if (page === null || typeof IntersectionObserver === 'undefined') return;
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (reducedMotion.matches) return;

    const targets = page.querySelectorAll<HTMLElement>('[data-landing-reveal]');
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && entry.target instanceof HTMLElement) reveal(entry.target);
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -48px 0px' },
    );
    // 등장 위치와 초기화 위치를 떨어뜨려 이동 중 화면 가장자리에서 깜빡이지 않게 한다.
    const resetObserver = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (!entry.isIntersecting && entry.target instanceof HTMLElement) {
            entry.target.dataset.landingReveal = 'pending';
          }
        }
      },
      { rootMargin: '96px 0px' },
    );

    function reveal(target: HTMLElement) {
      target.dataset.landingReveal = 'visible';
    }

    function onFocus(event: FocusEvent) {
      if (!(event.target instanceof HTMLElement)) return;
      const target = event.target.closest<HTMLElement>('[data-landing-reveal]');
      if (target !== null) reveal(target);
    }

    function onMotionChange(event: MediaQueryListEvent) {
      if (!event.matches) return;
      for (const target of targets) reveal(target);
      observer.disconnect();
      resetObserver.disconnect();
    }

    // 관찰을 지원할 때만 첫 페인트 전에 숨기고, 화면에 다시 들어올 때마다 재생한다.
    for (const target of targets) {
      target.dataset.landingReveal = 'pending';
      observer.observe(target);
      resetObserver.observe(target);
    }
    page.addEventListener('focusin', onFocus);
    reducedMotion.addEventListener('change', onMotionChange);

    return () => {
      observer.disconnect();
      resetObserver.disconnect();
      page.removeEventListener('focusin', onFocus);
      reducedMotion.removeEventListener('change', onMotionChange);
      for (const target of targets) target.dataset.landingReveal = '';
    };
  }, []);

  return pageRef;
}
