import { type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { LandingLink } from './LandingLink';
import type { LandingGuideContent } from './landingGuides';

export function LandingGuide({ guide }: { guide: LandingGuideContent }) {
  const trackRef = useRef<HTMLElement>(null);
  const [edges, setEdges] = useState({ start: true, end: false });
  const trackId = `${guide.id}-track`;

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

  return (
    <section
      className="landing-guide"
      id={guide.id}
      aria-labelledby={`${guide.id}-title`}
      aria-roledescription="캐러셀"
    >
      <div className="landing-guide__heading">
        <div>
          <p className="landing-eyebrow">{guide.eyebrow}</p>
          <h2 id={`${guide.id}-title`}>{guide.title}</h2>
          <p className="landing-description">{guide.description}</p>
        </div>
        <LandingLink className="landing-textlink" href={guide.href}>
          {guide.linkText}
        </LandingLink>
      </div>
      <section
        className="landing-guide__track"
        id={trackId}
        ref={trackRef}
        // biome-ignore lint/a11y/noNoninteractiveTabindex: 가로 스크롤 영역에 초점을 두고 방향키와 Home/End로 카드를 넘긴다.
        tabIndex={0}
        aria-label={`${guide.title} 설명 카드. 좌우 방향키로 넘길 수 있습니다.`}
        onKeyDown={onKeyDown}
      >
        {guide.cards.map((card) => (
          <article className="landing-guide__card" key={card.step} aria-label={card.step}>
            <div className="landing-guide__copy">
              <p className="landing-guide__number">{card.step}</p>
              <h3>{card.title}</h3>
              <p className="landing-description">{card.description}</p>
            </div>
            <div className="landing-guide__visual">
              <a
                className="landing-screen"
                href={card.capture.src}
                target="_blank"
                rel="noopener"
                aria-label={`${card.capture.alt} 원본 크게 보기`}
                style={{ maxWidth: `calc((var(--visual-height) - 70px) * (${card.capture.ratio}))` }}
              >
                <div className="landing-screen__bar">
                  <span className="landing-screen__dots" aria-hidden="true">
                    ● ● ●
                  </span>
                  <span>DEVICA 실제 화면 ↗</span>
                </div>
                <div className="landing-screen__crop" style={{ aspectRatio: card.capture.ratio }}>
                  <img
                    src={card.capture.src}
                    alt={card.capture.alt}
                    style={card.capture.style}
                    loading="lazy"
                    decoding="async"
                  />
                </div>
              </a>
            </div>
            <p className="landing-guide__note">{card.note}</p>
          </article>
        ))}
      </section>
      <div className="landing-guide__controls">
        <p>
          <span className="landing-guide__count">{guide.cards.length}개 카드</span>
          <span>옆으로 넘겨서 살펴보세요.</span>
        </p>
        <div className="landing-guide__arrows">
          <button
            type="button"
            aria-label={`${guide.title} 이전 카드`}
            aria-controls={trackId}
            disabled={edges.start}
            onClick={() => move('previous')}
          >
            <span aria-hidden="true">‹</span>
          </button>
          <button
            type="button"
            aria-label={`${guide.title} 다음 카드`}
            aria-controls={trackId}
            disabled={edges.end}
            onClick={() => move('next')}
          >
            <span aria-hidden="true">›</span>
          </button>
        </div>
      </div>
    </section>
  );
}
