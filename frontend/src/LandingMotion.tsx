import { type RefObject, useEffect, useRef, useState } from 'react';

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export function CountUp({ to, from = 0, decimals = 0 }: { to: number; from?: number; decimals?: number }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [value, setValue] = useState(to);

  useEffect(() => {
    const element = ref.current;
    if (element === null || typeof IntersectionObserver === 'undefined' || prefersReducedMotion()) return;
    setValue(from);
    let frame = 0;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry?.isIntersecting) return;
        observer.disconnect();
        const start = performance.now();
        const step = (now: number) => {
          const progress = Math.min((now - start) / 1400, 1);
          const eased = 1 - (1 - progress) ** 3;
          setValue(from + (to - from) * eased);
          if (progress < 1) frame = requestAnimationFrame(step);
        };
        frame = requestAnimationFrame(step);
      },
      { threshold: 0.6 },
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
      cancelAnimationFrame(frame);
    };
  }, [to, from]);

  return (
    <>
      <span ref={ref} aria-hidden="true">
        {value.toFixed(decimals)}
      </span>
      <span className="landing-sr-only">{to.toFixed(decimals)}</span>
    </>
  );
}

export function usePointerTilt(targetRef: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const target = targetRef.current;
    if (target === null || !window.matchMedia('(pointer: fine)').matches || prefersReducedMotion()) return;
    let frame = 0;

    function onMove(event: PointerEvent) {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        if (target === null) return;
        const rect = target.getBoundingClientRect();
        target.style.setProperty('--tilt-x', (((event.clientX - rect.left) / rect.width) * 2 - 1).toFixed(3));
        target.style.setProperty('--tilt-y', (((event.clientY - rect.top) / rect.height) * 2 - 1).toFixed(3));
      });
    }

    function onLeave() {
      cancelAnimationFrame(frame);
      target?.style.setProperty('--tilt-x', '0');
      target?.style.setProperty('--tilt-y', '0');
    }

    target.addEventListener('pointermove', onMove);
    target.addEventListener('pointerleave', onLeave);
    return () => {
      cancelAnimationFrame(frame);
      target.removeEventListener('pointermove', onMove);
      target.removeEventListener('pointerleave', onLeave);
    };
  }, [targetRef]);
}

const PIN_QUERY = '(min-width: 768px) and (min-height: 720px) and (prefers-reduced-motion: no-preference)';

export function useHorizontalPin() {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const trackRef = useRef<HTMLElement>(null);
  const [pinned, setPinned] = useState(false);

  useEffect(() => {
    const query = window.matchMedia(PIN_QUERY);
    const sync = () => setPinned(query.matches);
    sync();
    query.addEventListener('change', sync);
    return () => query.removeEventListener('change', sync);
  }, []);

  useEffect(() => {
    const scroller = scrollerRef.current;
    const track = trackRef.current;
    if (!pinned || scroller === null || track === null) return;
    let frame = 0;

    function update() {
      if (scroller === null || track === null) return;
      const rect = scroller.getBoundingClientRect();
      const progress = Math.min(Math.max(-rect.top / Math.max(rect.height - window.innerHeight, 1), 0), 1);
      track.scrollLeft = progress * (track.scrollWidth - track.clientWidth);
      const cards = [...track.children].filter((card): card is HTMLElement => card instanceof HTMLElement);
      const [first, second] = cards;
      if (first === undefined || second === undefined) return;
      const distance = second.offsetLeft - first.offsetLeft;
      for (const card of cards) {
        const offset = Math.abs(card.offsetLeft - first.offsetLeft - track.scrollLeft) / distance;
        card.style.setProperty('--card-focus', (1 - Math.min(offset, 1)).toFixed(3));
      }
    }

    function onScroll() {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(update);
    }

    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
    };
  }, [pinned]);

  function scrollToLeft(left: number) {
    const scroller = scrollerRef.current;
    const track = trackRef.current;
    if (scroller === null || track === null) return;
    const max = track.scrollWidth - track.clientWidth;
    const top = scroller.getBoundingClientRect().top + window.scrollY;
    window.scrollTo({
      top: top + (max > 0 ? left / max : 0) * (scroller.offsetHeight - window.innerHeight),
      behavior: 'smooth',
    });
  }

  return { scrollerRef, trackRef, pinned, scrollToLeft: pinned ? scrollToLeft : undefined };
}
