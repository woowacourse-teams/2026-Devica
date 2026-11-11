import { useEffect, useState } from 'react';
import { SelectIcon } from './icons';
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
  eyebrow?: string;
  onSelect: (code: string) => void;
  // 뒤로 가기는 들어온 경로마다 돌아갈 곳이 달라 문구와 함께 받는다.
  back?: { label: string; onClick: () => void };
};

export function SelectionView({ categoryCode, eyebrow, onSelect, back }: Props) {
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

  const categoryName = CATEGORY_DISPLAY.find((category) => category.code === categoryCode)?.name ?? '';

  return (
    <section className="view-panel flow-view" aria-labelledby="selection-title">
      {back && (
        <button className="text-button view-back-button" type="button" onClick={back.onClick}>
          {back.label}
        </button>
      )}
      <div className="flow-head">
        <p className="flow-head__eyebrow">{eyebrow ?? (choosingCategory ? '제품 추천 시작' : categoryName)}</p>
        <h1 id="selection-title">{choosingCategory ? '어떤 제품을 찾으시나요?' : '어떤 용도로 사용하시나요?'}</h1>
      </div>

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
        <ul className={`select-list select-list--${choosingCategory ? 'category' : 'purpose'}`}>
          {cards.map((card) => (
            <li key={card.code}>
              <button
                className="select-card"
                type="button"
                disabled={!card.available}
                // 항상 하나만 고르므로 누르는 즉시 넘어간다 (#177).
                onClick={() => onSelect(card.code)}
              >
                {choosingCategory ? (
                  <SelectIcon code={card.code} size={40} />
                ) : (
                  <span className="select-card__tile" aria-hidden="true">
                    <SelectIcon code={card.code} size={32} />
                  </span>
                )}
                <span className="select-card__text">
                  <b className="select-card__name">{card.name}</b>
                  {!choosingCategory && card.description !== '' && (
                    <span className="select-card__description">{card.description}</span>
                  )}
                </span>
                {card.available ? (
                  <span className="select-card__arrow" aria-hidden="true">
                    →
                  </span>
                ) : (
                  <span className="select-card__badge">준비 중</span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
