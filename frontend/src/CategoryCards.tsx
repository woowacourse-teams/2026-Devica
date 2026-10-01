import { useEffect, useState } from 'react';
import { CATEGORY_DISPLAY, fetchCategories, type SelectionCard, toCards } from './selection';
import './SelectionView.css';

type Props = {
  onSelect: (code: string) => void;
  // 뒤로 가기는 들어온 경로마다 돌아갈 곳이 달라 문구와 함께 받는다.
  back: { label: string; onClick: () => void };
};

/** 전체 목록에 들어가기 전의 제품 유형 선택(UC-07). 추천 흐름의 제품 선택과 같은 모양이지만, "다음" 없이 누르면 바로 넘어간다. */
export function CategoryCards({ onSelect, back }: Props) {
  // 아직 받지 못한 상태(null)와 실패를 빈 목록과 구분한다.
  const [cards, setCards] = useState<SelectionCard[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let stale = false;
    fetchCategories()
      .then((served) => {
        if (!stale) {
          setCards(toCards(served, CATEGORY_DISPLAY));
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
  }, []);

  return (
    <section className="view-panel selection" aria-labelledby="category-cards-title">
      <button className="text-button view-back-button" type="button" onClick={back.onClick}>
        {back.label}
      </button>
      <h1 className="selection__title" id="category-cards-title">
        어떤 제품을 찾으시나요?
      </h1>
      <p className="section-description">찾으시는 제품을 골라 주세요.</p>

      {cards === null && !failed && <p className="section-description">불러오는 중입니다…</p>}
      {failed && (
        <p className="notice" role="alert">
          제품 목록을 불러오지 못했습니다. 잠시 뒤에 새로고침해 주세요.
        </p>
      )}

      {cards !== null && (
        <ul className="selection__list">
          {cards.map((card) => (
            <li key={card.code}>
              <button
                className="selection__card"
                type="button"
                disabled={!card.available}
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
