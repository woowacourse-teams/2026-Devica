import { type CSSProperties, type MouseEvent, useRef } from 'react';
import { track } from './analytics';
import { BOARD_WEIGHTS, LandingBoardDemo, LandingDemo, RECOMMEND_WEIGHTS, scrollToDemoStep } from './LandingDemo';
import { CarouselArrows, useCarousel } from './LandingGuide';
import { CountUp, useHorizontalPin, usePointerTilt } from './LandingMotion';
import { LANDING_GUIDES } from './landingGuides';
import { PATHS } from './routes';
import { ServiceEntryLink } from './ServiceEntryLink';
import { useAnalyticsView } from './useAnalyticsView';
import { useLandingReveal } from './useLandingReveal';
import './LandingPage.css';

const PRODUCT_LIST_METRICS = [
  {
    name: 'p95 응답 시간',
    hint: '요청 95%가 이 안에 응답',
    before: 7523,
    after: 85,
    max: 7523,
    unit: 'ms',
  },
  {
    name: '응답 실패',
    hint: '5초 안에 응답 못 함',
    before: 23,
    after: 0,
    max: 100,
    unit: '%',
  },
  { name: 'DB 사용률', hint: '몰릴 때 최대', before: 99, after: 28, max: 100, unit: '%' },
];

// 개선 전 대비 줄어든 비율. 0으로 줄었으면 100%다.
function reduction({ before, after }: { before: number; after: number }): number {
  return Math.round((1 - after / before) * 100);
}

export function LandingPage() {
  const pageRef = useLandingReveal();
  const landingView = useAnalyticsView<HTMLHeadingElement>('landing_viewed', {});
  const techPin = useHorizontalPin();
  const tech = useCarousel(techPin.trackRef, techPin.scrollToLeft);
  const heroRef = useRef<HTMLElement>(null);
  usePointerTilt(heroRef);

  return (
    <div className="landing-page" ref={pageRef}>
      <section className="landing-hero" aria-labelledby="landing-title" ref={heroRef}>
        <div className="landing-hero__orb" aria-hidden="true" />
        <HeroDevice />
        <div className="landing-hero__copy">
          <h1 id="landing-title" ref={landingView.ref}>
            <span className="landing-hero__word">내</span> <span className="landing-hero__word">사용</span>{' '}
            <span className="landing-hero__word">목적에</span> <span className="landing-hero__word">맞게,</span>
            <br />
            <span className="landing-hero__word">구매</span> <span className="landing-hero__word">고민은</span>{' '}
            <span className="landing-hero__word">더</span>{' '}
            <span className="landing-hero__word landing-hero__highlight">가볍게.</span>
          </h1>
          <p className="landing-hero__lead" data-landing-reveal="">
            전자제품 정보는 많은데, 무엇을 골라야 할지 막막한가요?{' '}
            <strong>DEVICA가 사용 목적에 맞는 제품을 추천해 어려운 구매 고민을 덜어드려요.</strong>
          </p>
          <div className="landing-buttons" data-landing-reveal="">
            <ServiceEntryLink
              className="landing-cta landing-cta--lime"
              href={PATHS.category}
              destination="spec_finder"
              entryPoint="landing_hero"
            >
              제품 추천 시작 <span aria-hidden="true">→</span>
            </ServiceEntryLink>
            <ServiceEntryLink
              className="landing-cta landing-cta--outline"
              href={PATHS.board}
              destination="board"
              entryPoint="landing_hero"
            >
              질문 보러 가기 <span aria-hidden="true">→</span>
            </ServiceEntryLink>
          </div>
        </div>
      </section>
      <section className="landing-section landing-how" id="how" aria-labelledby="landing-how-title">
        <div className="landing-section-heading" data-landing-reveal="">
          <h2 id="landing-how-title">필요한 도움부터 시작하세요.</h2>
        </div>
        <nav className="landing-routes" aria-label="서비스 바로가기" data-landing-reveal="">
          {/* biome-ignore lint/a11y/useValidAnchor: 앵커 이동을 시연이 화면에 고정되는 첫 단계 위치로 바꾼다. */}
          <a
            href="#find-specs"
            onClick={(event) => moveToDemo(event, 'find-specs', 'find-specs', 0, RECOMMEND_WEIGHTS)}
          >
            <span className="landing-routes__icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M12 3c.6 4.4 2.6 6.4 7 7-4.4.6-6.4 2.6-7 7-.6-4.4-2.6-6.4-7-7 4.4-.6 6.4-2.6 7-7Z" />
              </svg>
            </span>
            <b>제품 추천받기</b>
            <span className="landing-routes__text">
              사용하는 도구와 작업 환경만 알려 주세요. 필요한 사양과 그 이유를 정리해 드려요.
            </span>
            <span className="landing-routes__cta">
              사용법 보기 <span aria-hidden="true">↓</span>
            </span>
          </a>
          {/* biome-ignore lint/a11y/useValidAnchor: 제품 추천받기 소개로 가는 앵커 이동을 그 소개의 마지막 단계 위치로 바꾼다. */}
          <a
            href="#find-specs"
            onClick={(event) =>
              moveToDemo(event, 'product-list', 'find-specs', RECOMMEND_WEIGHTS.length - 1, RECOMMEND_WEIGHTS)
            }
          >
            <span className="landing-routes__icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <rect x="5" y="5" width="14" height="10" rx="1.5" />
                <path d="M3 18.5h18" />
              </svg>
            </span>
            <b>제품 목록</b>
            <span className="landing-routes__text">
              가격과 사양으로 노트북을 골라 보세요. 최저가를 기준으로 한눈에 비교할 수 있어요.
            </span>
            <span className="landing-routes__cta">
              사용법 보기 <span aria-hidden="true">↓</span>
            </span>
          </a>
          {/* biome-ignore lint/a11y/useValidAnchor: 앵커 이동을 시연이 화면에 고정되는 첫 단계 위치로 바꾼다. */}
          <a href="#use-board" onClick={(event) => moveToDemo(event, 'use-board', 'use-board', 0, BOARD_WEIGHTS)}>
            <span className="landing-routes__icon">
              <svg viewBox="0 0 24 24" aria-hidden="true">
                <path d="M5 5h14v10H10l-4 4v-4H5Z" />
                <path d="M9 9h6M9 12h4" />
              </svg>
            </span>
            <b>질문 게시판</b>
            <span className="landing-routes__text">
              혼자 결정하기 어렵다면 비슷한 고민을 나누고 다른 개발자의 답을 찾아보세요.
            </span>
            <span className="landing-routes__cta">
              사용법 보기 <span aria-hidden="true">↓</span>
            </span>
          </a>
        </nav>
        <LandingDemo guide={LANDING_GUIDES[0]} />
        <LandingBoardDemo guide={LANDING_GUIDES[1]} />
      </section>
      <section className="landing-section landing-tech" aria-labelledby="landing-tech-title">
        <div
          className="landing-tech__scroller"
          ref={techPin.scrollerRef}
          data-compact={techPin.compact ? '' : undefined}
          data-pinned={techPin.pinned ? '' : undefined}
          style={techPin.pinned ? { height: 'calc(100vh + 2 * 60vh)' } : undefined}
        >
          <div className="landing-tech__pin">
            <div className="landing-section-heading" data-landing-reveal="">
              <h2 id="landing-tech-title">써 볼수록 더 잘 맞는 제품 추천</h2>
            </div>
            <section
              className="landing-tech__track"
              id="landing-tech-track"
              ref={techPin.trackRef}
              data-landing-reveal=""
              // biome-ignore lint/a11y/noNoninteractiveTabindex: 가로 스크롤 영역에 초점을 두고 방향키와 Home/End로 카드를 넘긴다.
              tabIndex={0}
              aria-label="기술 소개 카드. 좌우 방향키로 넘길 수 있습니다."
              onKeyDown={tech.onKeyDown}
            >
              <article className="landing-tech__card">
                <div className="landing-tech__copy">
                  <h3>사용자가 서비스를 이용할수록 쌓이는 의견과 지표로 제품 추천이 개선돼요</h3>
                  <p className="landing-tech__label">문제</p>
                  <p className="landing-description">
                    제품 추천이 맞는지는 사용자만 알 수 있지만, 서비스를 이용하며 쌓이는 의견과 지표를 팀원이 하나하나
                    읽고 질문과 규칙을 고치기는 어려웠어요.
                  </p>
                  <p className="landing-tech__label">해결</p>
                  <p className="landing-description">
                    AI가 의견과 지표에서 원인을 찾아 제품 추천 규칙이나 설문 질문을 고친 PR을 올려요. 질문을 지워도 예전
                    결과 링크는 계속 열려요. 팀원이 답 조합 430개의 바꾸기 전과 후 결과를 확인해 승인하면 반영되고, 바뀐
                    제품 추천에 쌓이는 의견과 지표가 다시 다음 개선으로 이어져요.
                  </p>
                  <div className="landing-tech__result">
                    <strong>
                      약 <CountUp to={20} />
                      시간
                    </strong>
                    <p className="landing-tech__note">
                      메모리가 부족하다는 사용자 의견이 들어온 지 약 20시간 만에 개선안이 승인됐어요.
                    </p>
                  </div>
                  <a
                    className="landing-textlink"
                    href="https://github.com/woowacourse-teams/2026-Devica/pull/347"
                    target="_blank"
                    rel="noopener"
                  >
                    실제 개선 PR 보기 ↗
                  </a>
                </div>
                <div className="landing-tech__visual">
                  <div className="landing-loop" data-landing-reveal="">
                    <svg className="landing-loop__ring" viewBox="0 0 100 100" aria-hidden="true">
                      <circle cx="50" cy="50" r="38" />
                    </svg>
                    <span className="landing-loop__orbit" aria-hidden="true">
                      <span className="landing-loop__dot" />
                    </span>
                    <ol className="landing-loop__steps" aria-label="제품 추천 개선 루프와 실제 사례">
                      <li className="landing-loop__step landing-loop__step--1">
                        <b className="landing-loop__title">① 의견·지표</b>
                        <span className="landing-loop__desc">“32GB인데 자주 멈춰요”</span>
                      </li>
                      <li className="landing-loop__step landing-loop__step--2">
                        <b className="landing-loop__title">② AI 개선안</b>
                        <span className="landing-loop__desc">메모리 규칙 추가 PR</span>
                      </li>
                      <li className="landing-loop__step landing-loop__step--3">
                        <b className="landing-loop__title">③ 팀원 승인</b>
                        <span className="landing-loop__desc">약 20시간 만에</span>
                      </li>
                      <li className="landing-loop__step landing-loop__step--4">
                        <b className="landing-loop__title">④ 반영</b>
                        <span className="landing-loop__desc">권장 메모리 32GB → 48GB</span>
                      </li>
                    </ol>
                  </div>
                </div>
              </article>
              <article className="landing-tech__card">
                <div className="landing-tech__copy">
                  <h3>사용자가 몰려도 제품 목록이 빠르게 열려요</h3>
                  <p className="landing-tech__label">문제</p>
                  <p className="landing-description">
                    10분 동안 약 260명이 몰리는 트래픽(최대 초당 약 20건 요청)으로 성능 테스트를 했더니, 제품 목록의 p95
                    응답 시간이 7.5초였고 요청의 약 23%가 5초 안에 응답을 받지 못했어요.
                  </p>
                  <p className="landing-tech__label">해결</p>
                  <p className="landing-description">
                    요청이 올 때마다 모든 노트북의 최저가를 새로 계산하던 방식을, 가격이 바뀔 때 미리 계산해 두는
                    방식으로 바꿨어요. 가격순 목록은 미리 계산한 최저가를 가격 순서대로 정리해 두고 한 페이지 분량만
                    읽어요.
                  </p>
                  <div className="landing-tech__result">
                    <strong>
                      7.5초 → <CountUp from={7.5} to={0.09} decimals={2} />초
                    </strong>
                    <p className="landing-tech__note">
                      같은 트래픽으로 다시 측정했을 때 p95 응답 시간이 7.5초에서 0.09초로 줄었고, 응답하지 못한 요청도
                      없어졌어요.
                    </p>
                  </div>
                  <a
                    className="landing-textlink"
                    href="https://github.com/woowacourse-teams/2026-Devica/blob/develop/docs/adr/0043-제품-목록-최저가-사전-계산.md"
                    target="_blank"
                    rel="noopener"
                  >
                    개선 과정 보기 ↗
                  </a>
                </div>
                <div className="landing-tech__visual">
                  <figure className="landing-chart" data-landing-reveal="" aria-label="개선 전후 비교 그래프">
                    {PRODUCT_LIST_METRICS.map((metric) => (
                      <div className="landing-chart__metric" key={metric.name}>
                        <p className="landing-chart__name">
                          <span>
                            {metric.name} <span className="landing-chart__hint">{metric.hint}</span>
                          </span>
                          <span className="landing-chart__delta">{reduction(metric)}% 줄었어요</span>
                        </p>
                        {(['before', 'after'] as const).map((when) => (
                          <div className={`landing-chart__row landing-chart__row--${when}`} key={when}>
                            <span className="landing-chart__when">{when === 'before' ? '전' : '후'}</span>
                            <span className="landing-chart__track">
                              <span
                                className="landing-chart__bar"
                                style={
                                  {
                                    '--bar': `${(metric[when] / metric.max) * 100}%`,
                                    minWidth: metric[when] === 0 ? 0 : undefined,
                                  } as CSSProperties
                                }
                              />
                            </span>
                            <span className="landing-chart__value">
                              {/* 막대가 자라는 동안 값도 0에서 같은 속도로 올라간다. 지연은 막대 CSS 와 같다. */}
                              <CountUp to={metric[when]} suffix={metric.unit} delay={when === 'before' ? 250 : 650} />
                            </span>
                          </div>
                        ))}
                      </div>
                    ))}
                  </figure>
                </div>
              </article>
              <article className="landing-tech__card">
                <div className="landing-tech__copy">
                  <h3>판매처 가격이 바뀌어도 매일 자동으로 확인해요</h3>
                  <p className="landing-tech__label">문제</p>
                  <p className="landing-description">
                    판매처 가격은 계속 바뀌는데 Devica의 가격이 그대로면, 서비스에서 본 가격과 구매 페이지의 가격이
                    달라져요. 사람이 매일 모든 판매처를 확인하기는 어려웠어요.
                  </p>
                  <p className="landing-tech__label">해결</p>
                  <p className="landing-description">
                    매일 새벽 4시에 쿠팡에서 등록해 둔 판매 옵션과 같은 상품을 찾아 가격을 확인해요. 가격이 바뀌었으면
                    목록 최저가까지 함께 고치고, 찾지 못한 상품은 기존 가격을 그대로 두고 검수 대상으로 남겨요.
                  </p>
                  <div className="landing-tech__result">
                    <strong>매일 새벽 4시</strong>
                    <p className="landing-tech__note">
                      사람이 확인하지 않아도 매일 가격을 다시 확인하고, 확인하지 못한 상품은 대시보드에서 바로 찾아
                      검수할 수 있어요.
                    </p>
                  </div>
                </div>
                <div className="landing-tech__visual">
                  <ol className="landing-flow" data-landing-reveal="" aria-label="가격 확인 흐름">
                    <li className="landing-flow__step">
                      <b className="landing-flow__title">매일 새벽 4시</b>
                      <span className="landing-flow__desc">가격 확인 시작</span>
                    </li>
                    <li className="landing-flow__step">
                      <b className="landing-flow__title">쿠팡에서 같은 판매 옵션 찾기</b>
                      <span className="landing-flow__desc">저장해 둔 판매 옵션 식별자로 비교</span>
                    </li>
                    <li className="landing-flow__branches">
                      <div className="landing-flow__step landing-flow__step--ok">
                        <b className="landing-flow__title">찾음</b>
                        <span className="landing-flow__desc">가격과 목록 최저가 갱신</span>
                      </div>
                      <div className="landing-flow__step landing-flow__step--hold">
                        <b className="landing-flow__title">못 찾음</b>
                        <span className="landing-flow__desc">기존 가격 유지, 검수 대상으로 표시</span>
                      </div>
                    </li>
                  </ol>
                </div>
              </article>
            </section>
            <div className="landing-tech__controls" data-landing-reveal="">
              <p>
                <strong>{techPin.pinned ? '개선 사례' : '개선 사례 3가지'}</strong>
                {techPin.pinned ? (
                  <span className="landing-tech__count" aria-hidden="true">
                    <span className="landing-tech__count-current">{String(tech.index + 1).padStart(2, '0')}</span> / 03
                  </span>
                ) : (
                  ' 옆으로 넘겨 보세요.'
                )}
              </p>
              <CarouselArrows label="기술 소개" trackId="landing-tech-track" edges={tech.edges} move={tech.move} />
            </div>
          </div>
        </div>
        <div className="landing-tech__action" data-landing-reveal="">
          <ServiceEntryLink
            className="landing-cta landing-cta--primary"
            href={PATHS.category}
            destination="spec_finder"
            entryPoint="landing_tech"
          >
            제품 추천 시작 <span aria-hidden="true">→</span>
          </ServiceEntryLink>
        </div>
      </section>
      <section className="landing-section landing-faq" aria-labelledby="landing-faq-title" data-landing-reveal="">
        <h2 id="landing-faq-title">
          시작하기 전에
          <br />
          궁금한 점
        </h2>
        <div className="landing-faq__list">
          <details open>
            <summary>어떤 제품과 용도를 지원하나요?</summary>
            <p>
              현재는 노트북을 지원하고, 백엔드·프론트엔드 개발 등 직군별 용도를 고를 수 있습니다. 내 사용 환경에 맞는
              제품 추천을 받고, 제품과 질문 게시판을 이어서 살펴볼 수 있어요.
            </p>
          </details>
          <details>
            <summary>사양을 잘 몰라도 사용할 수 있나요?</summary>
            <p>
              사용하는 언어와 개발 도구, 작업 환경에 대한 질문에 답해 보세요. 현재 노트북 사양은 입력하지 않고 넘어갈 수
              있어요.
            </p>
          </details>
          <details>
            <summary>제품 추천 결과로 결정하기 어렵다면요?</summary>
            <p>질문 게시판에서 다른 개발자들의 고민과 답변을 살펴보거나, 궁금한 점을 직접 질문할 수 있어요.</p>
          </details>
        </div>
      </section>
      <section
        className="landing-section landing-closing"
        aria-labelledby="landing-closing-title"
        data-landing-reveal=""
      >
        <div className="landing-closing__panel">
          <div className="landing-closing__copy">
            <h2 id="landing-closing-title">
              개발 장비 고민,
              <br />
              함께 풀어봐요.
            </h2>
            <p className="landing-closing__lead">
              내 사용 목적에 맞는 사양부터 확인해 보세요. <br />
              남은 고민은 다른 개발자와 나눠요.
            </p>
          </div>
          <div className="landing-buttons landing-buttons--stack">
            <ServiceEntryLink
              className="landing-cta landing-cta--lime"
              href={PATHS.category}
              destination="spec_finder"
              entryPoint="landing_closing"
            >
              제품 추천 시작 <span aria-hidden="true">→</span>
            </ServiceEntryLink>
            <ServiceEntryLink
              className="landing-cta landing-cta--outline"
              href={PATHS.board}
              destination="board"
              entryPoint="landing_closing"
            >
              질문 보러 가기 <span aria-hidden="true">→</span>
            </ServiceEntryLink>
          </div>
        </div>
      </section>
    </div>
  );
}

function moveToDemo(
  event: MouseEvent<HTMLAnchorElement>,
  guide: string,
  id: string,
  step: number,
  weights: readonly number[],
) {
  track('landing_guide_link_clicked', { guide });
  const section = document.getElementById(id);
  if (section === null) return;
  event.preventDefault();
  scrollToDemoStep(section, step, weights);
  document.getElementById(`${id}-title`)?.focus({ preventScroll: true });
}

function HeroDevice() {
  return (
    <div className="landing-device" aria-hidden="true">
      <svg className="landing-device__svg" viewBox="0 0 560 400" role="presentation">
        <defs>
          <linearGradient id="landing-device-screen" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" className="landing-device__stop-a" />
            <stop offset="100%" className="landing-device__stop-b" />
          </linearGradient>
        </defs>
        <ellipse className="landing-device__shadow" cx="280" cy="372" rx="250" ry="16" />
        <rect className="landing-device__lid" x="56" y="24" width="448" height="300" rx="20" />
        <rect className="landing-device__screen" x="72" y="40" width="416" height="268" rx="10" />
        <g className="landing-device__ui">
          <circle className="landing-device__dot landing-device__dot--1" cx="94" cy="60" r="5" />
          <circle className="landing-device__dot landing-device__dot--2" cx="110" cy="60" r="5" />
          <circle className="landing-device__dot landing-device__dot--3" cx="126" cy="60" r="5" />
          <text className="landing-device__title" x="94" y="104">
            백엔드 개발 · 제품 추천 결과
          </text>
          <g className="landing-device__spec">
            <rect x="94" y="124" width="118" height="84" rx="12" />
            <text className="landing-device__label" x="110" y="152">
              CPU
            </text>
            <text className="landing-device__value" x="110" y="186">
              상위 등급
            </text>
          </g>
          <g className="landing-device__spec landing-device__spec--picked">
            <rect x="221" y="124" width="118" height="84" rx="12" />
            <text className="landing-device__label" x="237" y="152">
              메모리
            </text>
            <text className="landing-device__value" x="237" y="186">
              32GB
            </text>
          </g>
          <g className="landing-device__spec">
            <rect x="348" y="124" width="118" height="84" rx="12" />
            <text className="landing-device__label" x="364" y="152">
              저장 공간
            </text>
            <text className="landing-device__value" x="364" y="186">
              512GB
            </text>
          </g>
          <rect className="landing-device__reason" x="94" y="224" width="372" height="58" rx="10" />
          <rect className="landing-device__line" x="112" y="242" width="250" height="8" rx="4" />
          <rect
            className="landing-device__line landing-device__line--muted"
            x="112"
            y="258"
            width="180"
            height="8"
            rx="4"
          />
        </g>
        <path className="landing-device__base" d="M14 330 H546 L560 350 Q560 362 548 362 H12 Q0 362 0 350 Z" />
        <rect className="landing-device__notch" x="236" y="330" width="88" height="8" rx="4" />
      </svg>
      <span className="landing-device__chip landing-device__chip--ide">IntelliJ · Docker</span>
      <span className="landing-device__chip landing-device__chip--price">최저가 비교</span>
    </div>
  );
}
