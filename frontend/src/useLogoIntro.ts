import { type RefObject, useLayoutEffect } from 'react';

const PLAYED_KEY = 'devica:logo-intro';

function playedThisSession(): boolean {
  try {
    return window.sessionStorage.getItem(PLAYED_KEY) !== null;
  } catch {
    // 저장소를 쓸 수 없으면 매번 재생하지 않도록 이미 본 것으로 둔다.
    return true;
  }
}

function markPlayed(played: boolean): void {
  try {
    if (played) {
      window.sessionStorage.setItem(PLAYED_KEY, '1');
    } else {
      window.sessionStorage.removeItem(PLAYED_KEY);
    }
  } catch {
    // 저장하지 못해도 화면은 그대로 둔다.
  }
}

/**
 * 처음 화면을 이 세션에서 처음 열 때 헤더 로고 글자를 한 글자씩 들인다(약 0.8초).
 * 동작 줄이기 설정이면 재생하지 않는다. 끝나면 글자에 남긴 스타일을 지워 원래 모습으로 둔다.
 */
export function useLogoIntro(logoRef: RefObject<HTMLElement | null>, onHome: boolean): void {
  useLayoutEffect(() => {
    const logo = logoRef.current;
    if (!onHome || logo === null || playedThisSession()) return;
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    markPlayed(true);
    const letters = [...logo.querySelectorAll<HTMLElement>('.site-logo__letter')];
    // Anime.js 는 인트로를 재생할 때만 내려받는다. 받는 동안 글자가 보였다 사라지지 않게 먼저 숨긴다.
    for (const letter of letters) letter.style.opacity = '0';
    const show = () => {
      for (const letter of letters) letter.style.removeProperty('opacity');
    };
    let finished = false;
    let cancelled = false;
    let revert = show;
    Promise.all([import('animejs/animation'), import('animejs/utils')])
      .then(([{ animate }, { stagger }]) => {
        if (cancelled) return;
        // 글자는 숨겨 둔 채로 시작해 1까지 올린다. 끝나면 남긴 스타일을 지우고 숨김도 푼다.
        const intro = animate(letters, {
          opacity: { from: 0, to: 1 },
          y: { from: '0.5em' },
          filter: { from: 'blur(6px)', to: 'blur(0px)' },
          duration: 560,
          delay: stagger(48),
          ease: 'outQuint',
          onComplete: (self) => {
            finished = true;
            self.revert();
            show();
          },
        });
        revert = () => intro.revert();
      })
      // 받지 못하면 인트로 없이 로고를 그대로 보여 준다.
      .catch(() => {
        finished = true;
        show();
      });
    return () => {
      cancelled = true;
      revert();
      show();
      // 끝나기 전에 화면을 떠났거나 개발 모드가 효과를 한 번 더 돌리면, 다음에 다시 재생한다.
      if (!finished) markPlayed(false);
    };
  }, [onHome, logoRef]);
}
