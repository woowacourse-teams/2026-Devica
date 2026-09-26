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
  onBack?: () => void;
};

export function SelectionView({ categoryCode, onSelect, onBack }: Props) {
  const choosingCategory = categoryCode === null;
  // 아직 받지 못한 상태(null)와 실패를 빈 목록과 구분한다.
  const [cards, setCards] = useState<SelectionCard[] | null>(null);
  const [failed, setFailed] = useState(false);
  // 사용 목적은 와이어프레임대로 고른 뒤 "다음"으로 넘어간다. 제품 유형은 누르면 바로 넘어간다.
  const [picked, setPicked] = useState<string | null>(null);

  useEffect(() => {
    let stale = false;
    setCards(null);
    setFailed(false);
    setPicked(null);
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
  }, [categoryCode]);

  const choose = (code: string) => {
    if (choosingCategory) {
      onSelect(code);
      return;
    }
    setPicked(code);
  };

  return (
    <section className="view-panel selection" aria-labelledby="selection-title">
      {onBack && (
        <button className="text-button view-back-button" type="button" onClick={onBack}>
          ← 제품 다시 고르기
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
        <p className="notice" role="alert">
          {choosingCategory ? '제품' : '사용 목적'} 목록을 불러오지 못했습니다. 잠시 뒤에 새로고침해 주세요.
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
                aria-pressed={choosingCategory ? undefined : picked === card.code}
                onClick={() => choose(card.code)}
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

      {!choosingCategory && (
        <button
          className="button button--primary button--wide"
          type="button"
          disabled={picked === null}
          onClick={() => picked !== null && onSelect(picked)}
        >
          다음
        </button>
      )}
    </section>
  );
}
