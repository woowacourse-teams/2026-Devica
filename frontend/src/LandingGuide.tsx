import { type KeyboardEvent, useEffect, useRef, useState } from 'react';
import type { LandingGuideContent } from './landingGuides';
import { ServiceEntryLink } from './ServiceEntryLink';
import { useAnalyticsView } from './useAnalyticsView';

export function useCarousel() {
  const trackRef = useRef<HTMLElement>(null);
  const [edges, setEdges] = useState({ start: true, end: false });

  useEffect(() => {
    const track = trackRef.current;
    if (track === null) return;

    function updateEdges() {
      if (track === null) return;
      const start = track.scrollLeft <= 3;
      const end = track.scrollLeft >= track.scrollWidth - track.clientWidth - 2;
      setEdges((previous) => (previous.start === start && previous.end === end ? previous : { start, end }));
    }

    const observer = new ResizeObserver(updateEdges);
    observer.observe(track);
    track.addEventListener('scroll', updateEdges, { passive: true });
    updateEdges();
    return () => {
      observer.disconnect();
      track.removeEventListener('scroll', updateEdges);
    };
  }, []);

  function move(destination: 'previous' | 'next' | 'first' | 'last') {
    const track = trackRef.current;
    if (track === null) return;
    const first = track.children[0];
    const second = track.children[1];
    if (!(first instanceof HTMLElement) || !(second instanceof HTMLElement)) return;

    const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth';
    if (destination === 'first' || destination === 'last') {
      track.scrollTo({ left: destination === 'first' ? 0 : track.scrollWidth, behavior });
    } else {
      const distance = second.offsetLeft - first.offsetLeft;
      track.scrollBy({ left: destination === 'previous' ? -distance : distance, behavior });
    }
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

  return { trackRef, edges, move, onKeyDown };
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

export function LandingGuide({ guide }: { guide: LandingGuideContent }) {
  const guideView = useAnalyticsView<HTMLHeadingElement>('landing_guide_viewed', { guide: guide.id });

  return (
    <section className="landing-guide" id={guide.id} aria-labelledby={`${guide.id}-title`} data-landing-reveal="">
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
      <ol className="landing-guide__steps">
        {guide.steps.map((step, index) => (
          <li className="landing-guide__step" key={step.title}>
            <span className="landing-guide__number">{String(index + 1).padStart(2, '0')}</span>
            <span className="landing-guide__copy">
              <b>{step.title}</b>
              <span className="landing-guide__description">{step.description}</span>
            </span>
          </li>
        ))}
      </ol>
    </section>
  );
}
