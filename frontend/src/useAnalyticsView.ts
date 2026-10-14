import { useEffect, useEffectEvent, useRef } from 'react';
import { type EventName, type Properties, track } from './analytics';

// 절반 이상이 300ms 동안 보이면 노출로 센다. 애니메이션을 다시 재생해도 같은 화면에서는 한 번만 보낸다.
export function useAnalyticsView<T extends HTMLElement>(
  event: EventName,
  properties: Properties,
  viewKey = '',
  enabled = true,
) {
  const ref = useRef<T>(null);
  const view = useRef({ key: viewKey, recorded: false });

  function record() {
    if (!enabled || (view.current.key === viewKey && view.current.recorded)) return;
    view.current = { key: viewKey, recorded: true };
    track(event, properties);
  }

  const onVisible = useEffectEvent(record);

  useEffect(() => {
    if (view.current.key !== viewKey) view.current = { key: viewKey, recorded: false };
    const element = ref.current;
    if (!enabled || element === null || typeof IntersectionObserver === 'undefined') return;
    let inView = false;
    let timer: ReturnType<typeof setTimeout> | undefined;

    function check() {
      clearTimeout(timer);
      if (!inView || document.visibilityState !== 'visible' || view.current.recorded) return;
      for (let parent: HTMLElement | null = element; parent !== null; parent = parent.parentElement) {
        const style = getComputedStyle(parent);
        if (style.opacity === '0' || style.visibility === 'hidden' || style.display === 'none') return;
      }
      timer = setTimeout(onVisible, 300);
    }

    const observer = new IntersectionObserver(
      (entries) => {
        inView = entries.some((entry) => entry.isIntersecting && entry.intersectionRatio >= 0.5);
        check();
      },
      { threshold: 0.5 },
    );
    // 화면 안에 있어도 등장 애니메이션 전에는 투명하다. 표시로 전환될 때 노출 측정을 시작한다.
    const reveal = element.closest('[data-landing-reveal]');
    const animationObserver = new MutationObserver(check);
    if (reveal !== null)
      animationObserver.observe(reveal, { attributes: true, attributeFilter: ['data-landing-reveal'] });
    element.addEventListener('transitionend', check);
    if (reveal !== element) reveal?.addEventListener('transitionend', check);
    document.addEventListener('visibilitychange', check);
    observer.observe(element);
    return () => {
      clearTimeout(timer);
      observer.disconnect();
      animationObserver.disconnect();
      element.removeEventListener('transitionend', check);
      if (reveal !== element) reveal?.removeEventListener('transitionend', check);
      document.removeEventListener('visibilitychange', check);
    };
  }, [enabled, viewKey]);

  return { ref, record };
}
