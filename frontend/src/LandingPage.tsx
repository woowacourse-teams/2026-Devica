import { track } from './analytics';
import { LandingGuide } from './LandingGuide';
import { LANDING_GUIDES } from './landingGuides';
import { PATHS } from './routes';
import { ServiceEntryLink } from './ServiceEntryLink';
import { useAnalyticsView } from './useAnalyticsView';
import { useLandingReveal } from './useLandingReveal';
import './LandingPage.css';

export function LandingPage() {
  const pageRef = useLandingReveal();
  const landingView = useAnalyticsView<HTMLHeadingElement>('landing_viewed', {});

  return (
    <div className="landing-page" ref={pageRef}>
      <section className="landing-hero" aria-labelledby="landing-title">
        <div className="landing-hero__copy">
          <p className="landing-eyebrow" data-landing-reveal="">
            DEVICA 서비스 소개
          </p>
          <h1 id="landing-title" data-landing-reveal="" ref={landingView.ref}>
            내 사용 목적에 맞게,
            <br />
            구매 고민은 더 가볍게.
          </h1>
          <p className="landing-hero__lead" data-landing-reveal="">
            전자제품 정보는 많은데, 무엇을 골라야 할지 막막한가요?
            <br />
            <strong>DEVICA가 사용 목적에 맞는 제품을 추천해 어려운 구매 고민을 덜어드려요.</strong>
          </p>
          <div className="landing-hero__actions" data-landing-reveal="">
            <div className="landing-hero__buttons">
              <ServiceEntryLink
                className="landing-cta"
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
            {/* biome-ignore lint/a11y/useValidAnchor: 클릭은 측정만 하고 href의 기본 앵커 이동을 유지한다. */}
            <a
              className="landing-textlink landing-hero__guide-link"
              href="#how"
              onClick={() => track('landing_guide_link_clicked', { guide: 'overview' })}
            >
              사용법 먼저 보기 ↓
            </a>
          </div>
          <p className="landing-meta" data-landing-reveal="">
            약 2분 · 질문 9~10개 · 로그인 없이 시작
          </p>
        </div>
      </section>
      <section className="landing-how" id="how" aria-labelledby="landing-how-title">
        <div className="landing-section-heading" data-landing-reveal="">
          <div>
            <p className="landing-eyebrow">DEVICA 사용법</p>
            <h2 id="landing-how-title">필요한 도움부터 시작하세요.</h2>
          </div>
        </div>
        <nav className="landing-routes" aria-label="두 가지 서비스 사용법" data-landing-reveal="">
          {/* biome-ignore lint/a11y/useValidAnchor: 클릭은 측정만 하고 href의 기본 앵커 이동을 유지한다. */}
          <a href="#find-specs" onClick={() => track('landing_guide_link_clicked', { guide: 'find-specs' })}>
            <b>제품 추천받기</b>
            <span>내 사용 목적에 맞는 제품 추천받기</span>
          </a>
          {/* biome-ignore lint/a11y/useValidAnchor: 클릭은 측정만 하고 href의 기본 앵커 이동을 유지한다. */}
          <a href="#use-board" onClick={() => track('landing_guide_link_clicked', { guide: 'use-board' })}>
            <b>질문 게시판 이용</b>
            <span>추천 전에도, 추천받은 뒤에도 고민 나누기</span>
          </a>
        </nav>
        {LANDING_GUIDES.map((guide) => (
          <LandingGuide key={guide.id} guide={guide} />
        ))}
      </section>
      <section className="landing-faq" aria-labelledby="landing-faq-title" data-landing-reveal="">
        <h2 id="landing-faq-title">시작하기 전에 궁금한 점</h2>
        <details open>
          <summary>어떤 제품과 용도를 지원하나요?</summary>
          <p>
            현재는 노트북과 백엔드 개발 용도를 지원합니다. 내 사용 환경에 맞는 제품 추천을 받고, 제품과 질문 게시판을
            이어서 살펴볼 수 있어요.
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
      </section>
      <section className="landing-closing" aria-labelledby="landing-closing-title" data-landing-reveal="">
        <div>
          <h2 id="landing-closing-title">
            개발 장비 고민,
            <br />
            함께 풀어봐요.
          </h2>
          <p>
            내 사용 목적에 맞는 사양부터 확인해 보세요.
            <br />
            남은 고민은 다른 개발자와 나눠요.
          </p>
        </div>
        <div className="landing-closing__actions">
          <ServiceEntryLink
            className="landing-cta"
            href={PATHS.category}
            destination="spec_finder"
            entryPoint="landing_closing"
          >
            제품 추천 시작 <span aria-hidden="true">→</span>
          </ServiceEntryLink>
          <p className="landing-meta">약 2분 · 질문 9~10개</p>
          <ServiceEntryLink className="landing-cta" href={PATHS.board} destination="board" entryPoint="landing_closing">
            질문 보러 가기 <span aria-hidden="true">→</span>
          </ServiceEntryLink>
        </div>
      </section>
    </div>
  );
}
