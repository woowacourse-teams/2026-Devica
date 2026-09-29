import { useEffect, useState } from 'react';
import { CATEGORY_DISPLAY, fetchCategories, type SelectionCard, toCards } from './selection';
import './CategoryTabs.css';

type Props = {
  // 추천 흐름에서 유형을 고르기 전에 들어왔으면 null 이다. 대신 고르지 않고 사용자가 탭을 누르게 한다.
  selected: string | null;
  onSelect: (code: string) => void;
};

/** 전체 목록 위의 제품 유형 탭(UC-07). 추천 흐름의 제품 선택 화면과 겹치지 않도록 카드가 아니라 탭으로 둔다. */
export function CategoryTabs({ selected, onSelect }: Props) {
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

  if (failed) {
    return (
      <p className="notice" role="alert">
        제품 유형을 불러오지 못했습니다. 잠시 뒤에 새로고침해 주세요.
      </p>
    );
  }
  if (cards === null) {
    return <p className="section-description">제품 유형을 불러오는 중입니다…</p>;
  }
  return (
    <nav className="category-tabs" aria-label="제품 유형">
      {cards.map((card) => (
        <button
          className="category-tabs__tab"
          type="button"
          key={card.code}
          disabled={!card.available}
          aria-pressed={selected === card.code}
          onClick={() => onSelect(card.code)}
        >
          {card.name}
          {!card.available && <span className="category-tabs__badge">준비 중</span>}
        </button>
      ))}
    </nav>
  );
}
