import { useEffect, useState } from 'react';
import { BaselineCard } from './BaselineCard';
import { fetchRecommendation, type Spec } from './recommendation';
import './IntroView.css';

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
    <section className="view-panel" id="intro-view" aria-labelledby="intro-title">
      <button className="text-button view-back-button" type="button" onClick={onBack}>
        ← 사용 목적 다시 고르기
      </button>
      <h1 id="intro-title">기본 권장 사양</h1>
      <p className="section-description">백엔드 개발에 필요한 기본 사양입니다.</p>

      <section className="baseline-list" id="initial-spec-list" aria-label="OS별 기본 권장 사양">
        {specs === null && !failed && <p className="section-description">기본 권장 사양을 불러오는 중입니다…</p>}
        {specs?.map((spec) => (
          <BaselineCard key={spec.items.find((item) => item.code === 'OS')?.value ?? ''} spec={spec} />
        ))}
      </section>

      <p className="section-description">
        지금 쓰는 노트북과 개발 방식, 겪어 본 불편을 알려주시면 내 작업에 맞게 사양을 조정해 드려요. 모든 질문은 건너뛸
        수 있어요.
      </p>

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
        {questionsLoading ? '질문을 불러오는 중…' : '내 개발 환경에 맞게 조정하기'}
      </button>
      <button
        className="button button--neutral button--wide"
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
