import { type CSSProperties, type ReactNode, useEffect, useRef, useState } from 'react';
import { GuideHeading } from './LandingGuide';
import type { LandingGuideContent } from './landingGuides';

function useScrollSteps(total: number) {
  const sectionRef = useRef<HTMLElement>(null);
  const [active, setActive] = useState(0);

  useEffect(() => {
    const section = sectionRef.current;
    if (section === null) return;
    let frame = 0;

    function update() {
      if (section === null) return;
      const rect = section.getBoundingClientRect();
      const viewport = window.innerHeight;
      const enter = Math.min(Math.max(1 - rect.top / viewport, 0), 1);
      const pinned = Math.min(Math.max(-rect.top / Math.max(rect.height - viewport, 1), 0), 1);
      section.style.setProperty('--demo-enter', enter.toFixed(3));
      section.style.setProperty('--demo-progress', pinned.toFixed(3));
      section.style.setProperty('--demo-step', (pinned * total).toFixed(3));
      setActive(Math.min(Math.floor(pinned * total), total - 1));
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
  }, [total]);

  function scrollToStep(index: number) {
    const section = sectionRef.current;
    if (section === null) return;
    const top = section.getBoundingClientRect().top + window.scrollY;
    const distance = section.offsetHeight - window.innerHeight;
    const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth';
    window.scrollTo({ top: top + ((index + 0.5) / total) * distance, behavior });
  }

  return { sectionRef, active, scrollToStep, height: `calc(100vh + ${total} * 70vh)` };
}

export function LandingDemo({ guide }: { guide: LandingGuideContent }) {
  const total = guide.steps.length;
  const { sectionRef, active, height } = useScrollSteps(total);

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
          <div className="landing-demo__frame" aria-hidden="true">
            <div className="landing-demo__bar">
              <span className="landing-demo__dots" />
              <span>devica.co.kr · 예시 화면</span>
            </div>
            <div className="landing-demo__screens">
              <DemoScreen index={0} active={active}>
                <p className="landing-demo__q">어떤 제품이 필요한가요?</p>
                <div className="landing-demo__grid">
                  <span className="landing-demo__option" data-picked="">
                    <b>노트북</b>
                    <small>개발용 노트북 제품 추천</small>
                  </span>
                  <span className="landing-demo__option" data-muted="">
                    <b>다른 제품</b>
                    <small>준비 중</small>
                  </span>
                </div>
              </DemoScreen>
              <DemoScreen index={1} active={active}>
                <p className="landing-demo__q">어떤 용도로 사용하시나요?</p>
                <div className="landing-demo__grid">
                  <span className="landing-demo__option" data-picked="">
                    <b>백엔드 개발</b>
                  </span>
                  <span className="landing-demo__option">
                    <b>프론트엔드 개발</b>
                  </span>
                  <span className="landing-demo__option">
                    <b>모바일 개발</b>
                  </span>
                  <span className="landing-demo__option">
                    <b>디자인</b>
                  </span>
                </div>
              </DemoScreen>
              <DemoScreen index={2} active={active}>
                <span className="landing-demo__meter">
                  <span />
                </span>
                <p className="landing-demo__q">어떤 IDE 를 쓰나요?</p>
                <div className="landing-demo__list">
                  <span className="landing-demo__option" data-picked="">
                    IntelliJ, PyCharm 등 JetBrains 제품
                  </span>
                  <span className="landing-demo__option">VS Code, Vim, Neovim 등</span>
                  <span className="landing-demo__option">여러 개를 동시에 함께 쓴다</span>
                </div>
              </DemoScreen>
              <DemoScreen index={3} active={active}>
                <p className="landing-demo__q">백엔드 개발 · 노트북 제품 추천 결과</p>
                <div className="landing-demo__specs">
                  <span>
                    <small>CPU</small>
                    <b>상위 등급</b>
                  </span>
                  <span data-picked="">
                    <small>메모리</small>
                    <b>32GB</b>
                  </span>
                  <span>
                    <small>저장 공간</small>
                    <b>512GB</b>
                  </span>
                </div>
                <p className="landing-demo__reason">JetBrains IDE 는 인덱싱과 코드 분석에 메모리를 많이 씁니다.</p>
              </DemoScreen>
              <DemoScreen index={4} active={active}>
                <p className="landing-demo__q">추천 결과에 맞는 노트북</p>
                <div className="landing-demo__list">
                  <span className="landing-demo__product" data-picked="">
                    <span className="landing-demo__thumb" />
                    <span>
                      <b>14형 노트북</b>
                      <small>32GB · 512GB</small>
                    </span>
                    <em>최저가</em>
                  </span>
                  <span className="landing-demo__product">
                    <span className="landing-demo__thumb" />
                    <span>
                      <b>16형 노트북</b>
                      <small>32GB · 1TB</small>
                    </span>
                    <em>최저가</em>
                  </span>
                </div>
              </DemoScreen>
            </div>
          </div>
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

function DemoScreen({ index, active, children }: { index: number; active: number; children: ReactNode }) {
  return (
    <div className="landing-demo__screen" data-state={index === active ? 'active' : index < active ? 'past' : 'next'}>
      {children}
    </div>
  );
}

export function LandingBoardDemo({ guide }: { guide: LandingGuideContent }) {
  const { sectionRef, active, scrollToStep, height } = useScrollSteps(guide.steps.length);

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
          <div className="landing-demo__frame landing-board__frame" aria-hidden="true">
            <div className="landing-demo__bar">
              <span className="landing-demo__dots" />
              <span>devica.co.kr/board · 예시 화면</span>
            </div>
            <div className="landing-demo__screens">
              <DemoScreen index={0} active={active}>
                <div className="landing-board__chips">
                  <span className="landing-board__chip" data-picked="">
                    노트북
                  </span>
                  <span className="landing-board__chip" data-picked="">
                    백엔드 개발
                  </span>
                  <span className="landing-board__chip">프론트엔드 개발</span>
                </div>
                <div className="landing-demo__list">
                  {['72%', '58%', '66%'].map((width) => (
                    <span className="landing-board__row" key={width}>
                      <span className="landing-board__line" style={{ width }} />
                      <span className="landing-board__line landing-board__line--short" />
                    </span>
                  ))}
                </div>
              </DemoScreen>
              <DemoScreen index={1} active={active}>
                <span className="landing-board__post">
                  <span className="landing-board__line" style={{ width: '64%' }} />
                  <span className="landing-board__line landing-board__line--muted" />
                  <span className="landing-board__line landing-board__line--muted" style={{ width: '82%' }} />
                </span>
                <p className="landing-board__label">답변 2</p>
                {[0, 1].map((answer) => (
                  <span className="landing-board__answer" data-picked={answer === 0 ? '' : undefined} key={answer}>
                    <span className="landing-board__avatar" />
                    <span className="landing-board__line landing-board__line--muted" />
                  </span>
                ))}
              </DemoScreen>
              <DemoScreen index={2} active={active}>
                <span className="landing-board__field">
                  <span className="landing-board__line" style={{ width: '48%' }} />
                  <span className="landing-board__caret" />
                </span>
                <span className="landing-board__field landing-board__field--body">
                  <span className="landing-board__line landing-board__line--muted" />
                  <span className="landing-board__line landing-board__line--muted" style={{ width: '70%' }} />
                </span>
                <span className="landing-board__submit" data-picked="">
                  질문 올리기
                </span>
              </DemoScreen>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
