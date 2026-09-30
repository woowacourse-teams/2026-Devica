import { useEffect, useState } from 'react';
import { BaselineCard } from './BaselineCard';
import { fetchRecommendation, type Spec } from './recommendation';
import './IntroView.css';

const STEPS = [
  { index: '1', title: '질문에 답하기', meta: '9~10개 · 약 2분' },
  { index: '2', title: '권장 사양 확인', meta: '직접 수정 가능' },
  { index: '3', title: '맞는 제품 보기', meta: '20개 중에서' },
];

type Props = {
  purposeCode: string;
  onBack: () => void;
  onStart: () => void;
  canStart: boolean;
  questionsFailed: boolean;
  questionsLoading: boolean;
  onBaseline: () => void;
};

export function IntroView({
  purposeCode,
  onBack,
  onStart,
  canStart,
  questionsFailed,
  questionsLoading,
  onBaseline,
}: Props) {
  // 아직 받지 못한 상태(null)를 빈 목록과 구분한다.
  const [specs, setSpecs] = useState<Spec[] | null>(null);
  const [failed, setFailed] = useState(false);
  // 기본 권장 사양과 질문 중 하나라도 못 받으면 시작할 수 없다.
  const unavailable = failed || questionsFailed;

  useEffect(() => {
    let stale = false;
    setSpecs(null);
    setFailed(false);
    fetchRecommendation(purposeCode)
      .then((received) => {
        if (!stale) {
          setSpecs(received);
        }
      })
      .catch(() => {
        if (!stale) {
          setFailed(true);
        }
      });
    return () => {
      stale = true;
    };
  }, [purposeCode]);

  return (
    <section className="view-panel hero" id="intro-view" aria-labelledby="hero-title">
      <button className="text-button view-back-button" type="button" onClick={onBack}>
        ← 사용 목적 다시 고르기
      </button>
      <p className="eyebrow">백엔드 개발용 노트북 사양 찾기</p>
      <h1 id="hero-title">
        내 개발 환경에 맞는
        <br />
        노트북 사양을 찾아보세요
      </h1>
      <p className="hero__description">질문에 답하면 사양을 정리하고, 그 사양에 맞는 노트북까지 찾아드려요.</p>

      <section className="bordered-panel intro-steps" aria-labelledby="intro-steps-heading">
        <h2 className="intro-steps__heading" id="intro-steps-heading">
          이렇게 진행됩니다
        </h2>
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

      <section className="baseline-list" id="initial-spec-list" aria-label="OS별 기본 권장 사양">
        {specs === null && !failed && <p className="section-description">기본 권장 사양을 불러오는 중입니다…</p>}
        {specs?.map((spec) => (
          <BaselineCard key={spec.items.find((item) => item.code === 'OS')?.value ?? ''} spec={spec} />
        ))}
      </section>

      {unavailable && (
        <p className="notice" role="alert">
          지금은 사양 정보를 불러오지 못했습니다. 잠시 뒤에 새로고침해 주세요.
        </p>
      )}

      <button
        className="button button--primary button--wide"
        id="start-button"
        type="button"
        disabled={unavailable || !canStart}
        onClick={onStart}
      >
        {questionsLoading ? '질문을 불러오는 중…' : '질문 시작하고 내 사양 찾기'}
      </button>
      <button
        className="button button--primary button--wide"
        id="baseline-product-button"
        type="button"
        disabled={unavailable}
        onClick={onBaseline}
      >
        기본 권장 사양으로 제품 보기
      </button>
    </section>
  );
}
