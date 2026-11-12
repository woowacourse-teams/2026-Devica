import { useEffect, useState } from 'react';
import { BaselineCard } from './BaselineCard';
import { fetchRecommendation, type Spec } from './recommendation';
import { CATEGORY_DISPLAY, PURPOSE_DISPLAY } from './selection';
import './IntroView.css';

type Props = {
  categoryCode: string;
  purposeCode: string;
  onBack: () => void;
  onStart: () => void;
  canStart: boolean;
  questionsFailed: boolean;
  questionsLoading: boolean;
  onBaseline: () => void;
};

export function IntroView({
  categoryCode,
  purposeCode,
  onBack,
  onStart,
  canStart,
  questionsFailed,
  questionsLoading,
  onBaseline,
}: Props) {
  const categoryName = CATEGORY_DISPLAY.find((category) => category.code === categoryCode)?.name;
  const purposeName = PURPOSE_DISPLAY.find((purpose) => purpose.code === purposeCode)?.name ?? '선택한 사용 목적';
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
          setSpecs(received.specs);
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
    <section className="view-panel flow-view" id="intro-view" aria-labelledby="intro-title">
      <button className="text-button view-back-button" type="button" onClick={onBack}>
        ← 사용 목적 다시 고르기
      </button>
      <div className="flow-head">
        <p className="flow-head__eyebrow">{categoryName ? `${categoryName} · ${purposeName}` : purposeName}</p>
        <h1 id="intro-title">기본 권장 사양</h1>
        <p className="flow-head__description">
          질문에 답하기 전, 가볍게 쓰는 경우를 기준으로 잡은 사양입니다. 항목을 누르면 그렇게 권하는 이유를 볼 수
          있어요.
        </p>
      </div>

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

      <div className="intro-next">
        <p className="intro-next__text">
          지금 쓰는 노트북과 작업 방식, 겪어 본 불편을 알려주시면 내 작업에 맞게 사양을 조정해 드려요.
        </p>
        <div className="sticky-actions intro-next__actions">
          <button
            className="button button--primary"
            id="start-button"
            type="button"
            disabled={unavailable || !canStart}
            onClick={onStart}
          >
            {questionsLoading ? '질문을 불러오는 중…' : '내 작업 환경에 맞게 조정하기'}
            <span aria-hidden="true">→</span>
          </button>
          <button
            className="button"
            id="baseline-product-button"
            type="button"
            disabled={unavailable}
            onClick={onBaseline}
          >
            기본 권장 사양으로 제품 보기
            <span aria-hidden="true">→</span>
          </button>
        </div>
      </div>
    </section>
  );
}
