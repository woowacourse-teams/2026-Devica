import NumberFlow from '@number-flow/react';
import { type RefObject, useEffect, useRef, useState } from 'react';

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

// 화면에 들어오면 from 에서 to 로 자릿수를 굴린다(NumberFlow). 동작 줄이기 설정이면 처음부터 to 를 보여 준다.
export function CountUp({
  to,
  from = 0,
  decimals = 0,
  suffix,
  delay = 0,
}: {
  to: number;
  from?: number;
  decimals?: number;
  suffix?: string;
  delay?: number;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const [value, setValue] = useState(to);

  useEffect(() => {
    const element = ref.current;
    if (element === null || typeof IntersectionObserver === 'undefined' || prefersReducedMotion()) return;
    setValue(from);
    let timer = 0;
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry?.isIntersecting) return;
        observer.disconnect();
        timer = window.setTimeout(() => setValue(to), delay);
      },
      { threshold: 0.6 },
    );
    observer.observe(element);
    return () => {
      observer.disconnect();
      window.clearTimeout(timer);
    };
  }, [to, from, delay]);

  return (
    <>
      <span ref={ref} aria-hidden="true">
        <NumberFlow
          value={value}
          format={{ minimumFractionDigits: decimals, maximumFractionDigits: decimals }}
          suffix={suffix}
          spinTiming={COUNT_SPIN}
          transformTiming={COUNT_SPIN}
        />
      </span>
      <span className="landing-sr-only">
        {to.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals })}
        {suffix}
      </span>
    </>
  );
}

// 지표가 바뀌었다는 것을 보여 주는 한 번의 동작이다. 결과 최저가보다 길게, 끝은 천천히 멈춘다.
const COUNT_SPIN = { duration: 1100, easing: 'cubic-bezier(0.23, 1, 0.32, 1)' };

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

function markCardFocus(track: HTMLElement) {
  const cards = [...track.children].filter((card): card is HTMLElement => card instanceof HTMLElement);
  const [first, second] = cards;
  if (first === undefined || second === undefined) return;
  const distance = second.offsetLeft - first.offsetLeft;
  for (const card of cards) {
    const offset = Math.abs(card.offsetLeft - first.offsetLeft - track.scrollLeft) / distance;
    card.style.setProperty('--card-focus', (1 - Math.min(offset, 1)).toFixed(3));
  }
}

function scrollPageToCard(scroller: HTMLElement, track: HTMLElement, left: number) {
  const max = track.scrollWidth - track.clientWidth;
  const top = scroller.getBoundingClientRect().top + window.scrollY;
  window.scrollTo({
    top: top + (max > 0 ? left / max : 0) * (scroller.offsetHeight - window.innerHeight),
    behavior: 'smooth',
  });
}

const COMPACT_QUERY = '(min-width: 768px) and (prefers-reduced-motion: no-preference)';

export function useHorizontalPin() {
  const scrollerRef = useRef<HTMLDivElement>(null);
  const trackRef = useRef<HTMLElement>(null);
  const [compact, setCompact] = useState(false);
  const [fits, setFits] = useState(false);
  const pinned = compact && fits;

  useEffect(() => {
    const query = window.matchMedia(COMPACT_QUERY);
    const sync = () => setCompact(query.matches);
    sync();
    query.addEventListener('change', sync);
    return () => query.removeEventListener('change', sync);
  }, []);

  useEffect(() => {
    const pin = scrollerRef.current?.firstElementChild;
    const track = trackRef.current;
    if (!compact || !(pin instanceof HTMLElement) || track === null) return;

    function measure() {
      if (!(pin instanceof HTMLElement)) return;
      const children = [...pin.children].filter((child): child is HTMLElement => child instanceof HTMLElement);
      const gap = Number.parseFloat(getComputedStyle(pin).rowGap) || 0;
      const content = children.reduce((sum, child) => sum + child.offsetHeight, 0) + gap * (children.length - 1);
      setFits(content + 48 <= window.innerHeight);
    }

    const observer = new ResizeObserver(measure);
    observer.observe(track);
    window.addEventListener('resize', measure);
    measure();
    return () => {
      observer.disconnect();
      window.removeEventListener('resize', measure);
    };
  }, [compact]);

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
      markCardFocus(track);
    }

    function onScroll() {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(update);
    }

    function onWheel(event: WheelEvent) {
      if (scroller === null || track === null || Math.abs(event.deltaX) <= Math.abs(event.deltaY)) return;
      event.preventDefault();
      const max = Math.max(track.scrollWidth - track.clientWidth, 1);
      window.scrollBy({
        top: (event.deltaX * (scroller.offsetHeight - window.innerHeight)) / max,
        behavior: 'instant',
      });
    }

    function onFocusIn(event: FocusEvent) {
      if (scroller === null || track === null || !(event.target instanceof Element)) return;
      const card = [...track.children].find((child) => child.contains(event.target as Node));
      const first = track.firstElementChild;
      if (!(card instanceof HTMLElement) || !(first instanceof HTMLElement)) return;
      scrollPageToCard(scroller, track, card.offsetLeft - first.offsetLeft);
    }

    update();
    window.addEventListener('scroll', onScroll, { passive: true });
    window.addEventListener('resize', onScroll);
    track.addEventListener('wheel', onWheel, { passive: false });
    track.addEventListener('focusin', onFocusIn);
    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('scroll', onScroll);
      window.removeEventListener('resize', onScroll);
      track.removeEventListener('wheel', onWheel);
      track.removeEventListener('focusin', onFocusIn);
    };
  }, [pinned]);

  useEffect(() => {
    const track = trackRef.current;
    if (pinned || track === null) return;
    let frame = 0;
    const onScroll = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => markCardFocus(track));
    };
    markCardFocus(track);
    track.addEventListener('scroll', onScroll, { passive: true });

    let nudgeTimer = 0;
    const observer =
      typeof IntersectionObserver === 'undefined' || prefersReducedMotion()
        ? null
        : new IntersectionObserver(
            ([entry]) => {
              if (!entry?.isIntersecting) return;
              observer?.disconnect();
              if (track.scrollLeft > 0) return;
              track.dataset.nudge = '';
              nudgeTimer = window.setTimeout(() => delete track.dataset.nudge, 1200);
            },
            { threshold: 0.6 },
          );
    observer?.observe(track);

    return () => {
      cancelAnimationFrame(frame);
      track.removeEventListener('scroll', onScroll);
      observer?.disconnect();
      window.clearTimeout(nudgeTimer);
    };
  }, [pinned]);

  function scrollToLeft(left: number) {
    const scroller = scrollerRef.current;
    const track = trackRef.current;
    if (scroller !== null && track !== null) scrollPageToCard(scroller, track, left);
  }

  return { scrollerRef, trackRef, compact, pinned, scrollToLeft: pinned ? scrollToLeft : undefined };
}
