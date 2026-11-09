import { type KeyboardEvent, type RefObject, useEffect, useState } from 'react';
import type { LandingGuideContent } from './landingGuides';
import { ServiceEntryLink } from './ServiceEntryLink';
import { useAnalyticsView } from './useAnalyticsView';

export function useCarousel(trackRef: RefObject<HTMLElement | null>, navigate?: (left: number) => void) {
  const [edges, setEdges] = useState({ start: true, end: false });
  const [index, setIndex] = useState(0);

  useEffect(() => {
    const track = trackRef.current;
    if (track === null) return;

    function updateEdges() {
      if (track === null) return;
      const start = track.scrollLeft <= 3;
      const end = track.scrollLeft >= track.scrollWidth - track.clientWidth - 2;
      setEdges((previous) => (previous.start === start && previous.end === end ? previous : { start, end }));
      const first = track.children[0];
      const second = track.children[1];
      if (first instanceof HTMLElement && second instanceof HTMLElement) {
        setIndex(Math.round(track.scrollLeft / (second.offsetLeft - first.offsetLeft)));
      }
    }

    const observer = new ResizeObserver(updateEdges);
    observer.observe(track);
    track.addEventListener('scroll', updateEdges, { passive: true });
    updateEdges();
    return () => {
      observer.disconnect();
      track.removeEventListener('scroll', updateEdges);
    };
  }, [trackRef]);

  function move(destination: 'previous' | 'next' | 'first' | 'last') {
    const track = trackRef.current;
    if (track === null) return;
    const first = track.children[0];
    const second = track.children[1];
    if (!(first instanceof HTMLElement) || !(second instanceof HTMLElement)) return;

    const max = track.scrollWidth - track.clientWidth;
    const distance = second.offsetLeft - first.offsetLeft;
    const current = Math.round(track.scrollLeft / distance) * distance;
    const targets = { first: 0, last: max, previous: current - distance, next: current + distance };
    const left = Math.min(Math.max(targets[destination], 0), max);
    if (navigate) {
      navigate(left);
      return;
    }
    const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth';
    track.scrollTo({ left, behavior });
  }

  function onKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.target !== event.currentTarget) return;
    switch (event.key) {
      case 'ArrowLeft':
        event.preventDefault();
        move('previous');
        break;
      case 'ArrowRight':
        event.preventDefault();
        move('next');
        break;
      case 'Home':
        event.preventDefault();
        move('first');
        break;
      case 'End':
        event.preventDefault();
        move('last');
        break;
    }
  }

  return { edges, index, move, onKeyDown };
}

export function CarouselArrows({
  label,
  trackId,
  edges,
  move,
}: Pick<ReturnType<typeof useCarousel>, 'edges' | 'move'> & { label: string; trackId: string }) {
  return (
    <div className="landing-arrows">
      <button
        type="button"
        aria-label={`${label} 이전 카드`}
        aria-controls={trackId}
        disabled={edges.start}
        onClick={() => move('previous')}
      >
        <span aria-hidden="true">←</span>
      </button>
      <button
        type="button"
        aria-label={`${label} 다음 카드`}
        aria-controls={trackId}
        disabled={edges.end}
        onClick={() => move('next')}
      >
        <span aria-hidden="true">→</span>
      </button>
    </div>
  );
}

export function GuideHeading({ guide }: { guide: LandingGuideContent }) {
  const guideView = useAnalyticsView<HTMLHeadingElement>('landing_guide_viewed', { guide: guide.id });

  return (
    <div className="landing-guide__heading">
      <div>
        <p className="landing-guide__eyebrow">{guide.eyebrow}</p>
        <h3 id={`${guide.id}-title`} ref={guideView.ref}>
          {guide.title}
        </h3>
      </div>
      <ServiceEntryLink
        className="landing-textlink"
        href={guide.href}
        destination={guide.id === 'find-specs' ? 'spec_finder' : 'board'}
        entryPoint="landing_guide"
      >
        {guide.linkText} <span aria-hidden="true">→</span>
      </ServiceEntryLink>
    </div>
  );
}
