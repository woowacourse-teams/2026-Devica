import { useEffect, useState } from 'react';
import { BaselineCard } from './BaselineCard';
import { fetchBaselineSpecs, type Spec } from './recommendation';
import './IntroView.css';

// V1 은 백엔드 개발 목적 하나만 다룬다.
const PURPOSE_CODE = 'BACKEND_DEVELOPMENT';

const STEPS = [
  { index: '1', title: '질문에 답하기', meta: '9~10개 · 약 2분' },
  { index: '2', title: '권장 사양 확인', meta: '직접 수정 가능' },
  { index: '3', title: '맞는 제품 보기', meta: '20개 중에서' },
];

export function IntroView() {
  const [specs, setSpecs] = useState<Spec[]>([]);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    fetchBaselineSpecs(PURPOSE_CODE)
      .then(setSpecs)
      .catch(() => setFailed(true));
  }, []);

  return (
    <section className="view-panel hero" id="intro-view" aria-labelledby="hero-title">
      <p className="eyebrow">백엔드 개발용 노트북 사양 찾기</p>
      <h1 id="hero-title">내 개발 환경에 맞는<br/>노트북 사양을 찾아보세요</h1>
      <p className="hero__description">
        질문에 답하면 사양을 정리하고, 그 사양에 맞는 노트북까지 찾아드려요.
      </p>

      <section className="bordered-panel intro-steps" aria-labelledby="intro-steps-heading">
        <h2 className="intro-steps__heading" id="intro-steps-heading">이렇게 진행됩니다</h2>
        <ol className="intro-steps__list">
          {STEPS.map((step) => (
            <li className="intro-steps__step" key={step.index}>
              <span className="intro-steps__index">{step.index}</span>
              <span className="intro-steps__title">{step.title}</span>
              <span className="intro-steps__meta">{step.meta}</span>
            </li>
          ))}
        </ol>
      </section>

      <p className="section-description">
        아래는 백엔드 개발용 기본 권장 사양입니다. 질문에 답하면 사용 목적에 맞게 조정됩니다.
      </p>

      <div className="baseline-list" id="initial-spec-list" aria-label="OS별 기본 권장 사양">
        {specs.map((spec) => (
          <BaselineCard key={spec.items.find((item) => item.code === 'OS')?.value ?? ''} spec={spec}/>
        ))}
      </div>

      <button className="button button--primary button--wide" id="start-button" type="button" disabled={failed}>
        질문 시작하고 내 사양 찾기
      </button>
      <button className="button button--primary button--wide" id="baseline-product-button" type="button" disabled={failed}>
        기본 권장 사양으로 제품 보기
      </button>
    </section>
  );
}
