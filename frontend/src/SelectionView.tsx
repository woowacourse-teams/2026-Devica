import { useEffect, useState } from 'react';
import {
  CATEGORY_DISPLAY,
  fetchCategories,
  fetchPurposes,
  PURPOSE_DISPLAY,
  type SelectionCard,
  toCards,
} from './selection';
import './SelectionView.css';

type Props = {
  // 제품 유형을 아직 고르지 않았으면 제품 선택, 골랐으면 그 유형의 사용 목적 선택을 보여준다.
  categoryCode: string | null;
  onSelect: (code: string) => void;
  // 뒤로 가기는 들어온 경로마다 돌아갈 곳이 달라 문구와 함께 받는다.
  back?: { label: string; onClick: () => void };
};

export function SelectionView({ categoryCode, onSelect, back }: Props) {
  const choosingCategory = categoryCode === null;
  // 아직 받지 못한 상태(null)와 실패를 빈 목록과 구분한다.
  const [cards, setCards] = useState<SelectionCard[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [round, setRound] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: round는 실패 후 같은 목록을 다시 요청할 때 바뀐다.
  useEffect(() => {
    let stale = false;
    setCards(null);
    setFailed(false);
    const request = categoryCode === null ? fetchCategories() : fetchPurposes(categoryCode);
    const display = categoryCode === null ? CATEGORY_DISPLAY : PURPOSE_DISPLAY;
    request
      .then((served) => {
        if (!stale) {
          setCards(toCards(served, display));
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
  }, [categoryCode, round]);

  return (
    <section className="view-panel selection" aria-labelledby="selection-title">
      {back && (
        <button className="text-button view-back-button" type="button" onClick={back.onClick}>
          {back.label}
        </button>
      )}
      <h1 className="selection__title" id="selection-title">
        {choosingCategory ? '어떤 제품을 찾으시나요?' : '어떤 용도로 사용하시나요?'}
      </h1>
      <p className="section-description">
        {choosingCategory
          ? '찾으시는 제품을 골라 주세요.'
          : '자주 사용하는 용도를 선택해 주시면 가장 적합한 모델을 추천해 드립니다.'}
      </p>

      {cards === null && !failed && <p className="section-description">불러오는 중입니다…</p>}
      {failed && (
        <div>
          <p className="notice" role="alert">
            {choosingCategory ? '제품' : '사용 목적'} 목록을 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.
          </p>
          <button className="text-button" type="button" onClick={() => setRound((previous) => previous + 1)}>
            다시 시도
          </button>
        </div>
      )}

      {cards !== null && (
        <ul className="selection__list">
          {cards.map((card) => (
            <li key={card.code}>
              <button
                className="selection__card"
                type="button"
                disabled={!card.available}
                // 항상 하나만 고르므로 누르는 즉시 넘어간다 (#177).
                onClick={() => onSelect(card.code)}
              >
                <span className="selection__name">
                  {card.name}
                  {!card.available && <span className="selection__badge">준비 중</span>}
                </span>
                {card.description !== '' && <span className="selection__description">{card.description}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
