import { type CSSProperties, useEffect, useRef, useState } from 'react';
import board1 from './assets/landing/board-1.webp';
import board1Desktop from './assets/landing/board-1-desktop.webp';
import board2 from './assets/landing/board-2.webp';
import board2Desktop from './assets/landing/board-2-desktop.webp';
import board3 from './assets/landing/board-3.webp';
import board3Desktop from './assets/landing/board-3-desktop.webp';
import rec1 from './assets/landing/rec-1.webp';
import rec1Desktop from './assets/landing/rec-1-desktop.webp';
import rec2 from './assets/landing/rec-2.webp';
import rec2Desktop from './assets/landing/rec-2-desktop.webp';
import rec3 from './assets/landing/rec-3.webp';
import rec3Desktop from './assets/landing/rec-3-desktop.webp';
import rec4 from './assets/landing/rec-4.webp';
import rec4Desktop from './assets/landing/rec-4-desktop.webp';
import rec5 from './assets/landing/rec-5.webp';
import rec5Desktop from './assets/landing/rec-5-desktop.webp';
import { GuideHeading } from './LandingGuide';
import type { LandingGuideContent } from './landingGuides';

const FIRST_STEP_CUT = 0.5;

export const RECOMMEND_WEIGHTS = [FIRST_STEP_CUT, 1, 1, 1.5, 1];
const BOARD_WEIGHTS = [1.5, 1.5, 1];

const sum = (values: readonly number[]) => values.reduce((total, value) => total + value, 0);

const RECOMMEND_SHOTS = [
  { mobile: rec1, desktop: rec1Desktop },
  { mobile: rec2, desktop: rec2Desktop },
  { mobile: rec3, desktop: rec3Desktop },
  { mobile: rec4, desktop: rec4Desktop },
  { mobile: rec5, desktop: rec5Desktop },
];
const BOARD_SHOTS = [
  { mobile: board1, desktop: board1Desktop },
  { mobile: board2, desktop: board2Desktop },
  { mobile: board3, desktop: board3Desktop },
];

const SHORT_QUERY = '(max-width: 767px) and (max-height: 699px), (min-width: 768px) and (max-height: 499px)';
const STEP_INSET = 0.04;

function scrollRangeOf(section: HTMLElement) {
  const viewport = window.innerHeight;
  const frame = section.querySelector<HTMLElement>('.landing-screen');
  if (frame !== null && window.matchMedia(SHORT_QUERY).matches) {
    const rect = frame.getBoundingClientRect();
    return { start: rect.top + window.scrollY - viewport * 0.15, length: viewport * 0.5 };
  }
  return {
    start: section.getBoundingClientRect().top + window.scrollY,
    length: section.offsetHeight - viewport,
  };
}

export function scrollToDemoStep(section: HTMLElement, index: number, weights: readonly number[]) {
  const { start, length } = scrollRangeOf(section);
  const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth';
  const position = sum(weights.slice(0, index)) + weights[index] * STEP_INSET;
  window.scrollTo({ top: start + (position / sum(weights)) * length, behavior });
}

function useScrollSteps(weights: readonly number[]) {
  const sectionRef = useRef<HTMLElement>(null);
  const [active, setActive] = useState(0);
  const [short, setShort] = useState(false);

  useEffect(() => {
    const query = window.matchMedia(SHORT_QUERY);
    const sync = () => setShort(query.matches);
    sync();
    query.addEventListener('change', sync);
    return () => query.removeEventListener('change', sync);
  }, []);

  useEffect(() => {
    const section = sectionRef.current;
    if (section === null) return;
    let frame = 0;

    function update() {
      if (section === null) return;
      const rect = section.getBoundingClientRect();
      const viewport = window.innerHeight;
      const enter = Math.min(Math.max(1 - rect.top / viewport, 0), 1);
      const { start, length } = scrollRangeOf(section);
      const pinned = Math.min(Math.max((window.scrollY - start) / Math.max(length, 1), 0), 1);
      section.style.setProperty('--demo-enter', enter.toFixed(3));
      section.style.setProperty('--demo-progress', pinned.toFixed(3));
      let position = pinned * sum(weights);
      let index = 0;
      while (index < weights.length - 1 && position >= weights[index]) {
        position -= weights[index];
        index += 1;
      }
      section.style.setProperty('--demo-step', (index + Math.min(position / weights[index], 1)).toFixed(3));
      setActive(index);
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
  }, [weights]);

  function scrollToStep(index: number) {
    if (sectionRef.current !== null) scrollToDemoStep(sectionRef.current, index, weights);
  }

  return {
    sectionRef,
    active,
    scrollToStep,
    height: short ? undefined : `calc(100vh + ${sum(weights)} * 70vh)`,
  };
}

export function LandingDemo({ guide }: { guide: LandingGuideContent }) {
  const total = guide.steps.length;
  const { sectionRef, active, height } = useScrollSteps(RECOMMEND_WEIGHTS);

  const step = guide.steps[active];

  return (
    <section
      className="landing-guide landing-demo"
      id={guide.id}
      ref={sectionRef}
      aria-labelledby={`${guide.id}-title`}
      style={{ height }}
    >
      <div className="landing-demo__sticky">
        <GuideHeading guide={guide} />
        <div className="landing-demo__stage">
          <ScreenFrame shots={RECOMMEND_SHOTS} active={active} />
          <div className="landing-demo__status">
            <p className="landing-demo__count" aria-hidden="true">
              <span>{String(active + 1).padStart(2, '0')}</span> / {String(total).padStart(2, '0')}
            </p>
            <p className="landing-demo__title" aria-hidden="true">
              {step?.title}
            </p>
            <p className="landing-demo__description" aria-hidden="true">
              {step?.description}
            </p>
            <span className="landing-demo__progress" aria-hidden="true">
              <span />
            </span>
            <ol className="landing-sr-only">
              {guide.steps.map((item) => (
                <li key={item.title}>
                  {item.title}: {item.description}
                </li>
              ))}
            </ol>
          </div>
        </div>
      </div>
    </section>
  );
}

function ScreenFrame({
  shots,
  active,
  light = false,
}: {
  shots: readonly { mobile: string; desktop: string }[];
  active: number;
  light?: boolean;
}) {
  return (
    <div className="landing-screen" data-light={light ? '' : undefined} aria-hidden="true">
      <div className="landing-screen__view">
        <div className="landing-screen__stack">
          {shots.map((shot, index) => (
            <picture key={shot.mobile}>
              <source media="(min-width: 768px)" srcSet={shot.desktop} />
              <img
                className="landing-screen__shot"
                src={shot.mobile}
                alt=""
                loading="lazy"
                decoding="async"
                data-state={index === active ? 'active' : index < active ? 'past' : 'next'}
                style={{ '--shot-index': index } as CSSProperties}
              />
            </picture>
          ))}
        </div>
      </div>
    </div>
  );
}

export function LandingBoardDemo({ guide }: { guide: LandingGuideContent }) {
  const { sectionRef, active, scrollToStep, height } = useScrollSteps(BOARD_WEIGHTS);

  return (
    <section
      className="landing-guide landing-demo landing-board"
      id={guide.id}
      ref={sectionRef}
      aria-labelledby={`${guide.id}-title`}
      style={{ height }}
    >
      <div className="landing-demo__sticky">
        <GuideHeading guide={guide} />
        <div className="landing-board__stage">
          <ol className="landing-board__steps">
            {guide.steps.map((step, index) => (
              <li key={step.title}>
                <button
                  type="button"
                  className="landing-board__step"
                  aria-pressed={index === active}
                  onClick={() => scrollToStep(index)}
                >
                  <span className="landing-board__number">{String(index + 1).padStart(2, '0')}</span>
                  <span className="landing-board__copy">
                    <b>{step.title}</b>
                    <span className="landing-board__description">{step.description}</span>
                  </span>
                  {index === active && (
                    <span
                      className="landing-board__timer"
                      aria-hidden="true"
                      style={{ '--step-index': index } as CSSProperties}
                    >
                      <span className="landing-board__timer-fill" />
                    </span>
                  )}
                </button>
              </li>
            ))}
          </ol>
          <ScreenFrame shots={BOARD_SHOTS} active={active} light />
        </div>
      </div>
    </section>
  );
}
