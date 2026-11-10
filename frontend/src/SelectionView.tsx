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

const ICON_PATHS: Record<string, string[]> = {
  LAPTOP: ['M5.5 5h13A1.5 1.5 0 0 1 20 6.5V16H4V6.5A1.5 1.5 0 0 1 5.5 5z', 'M2 19h20'],
  MONITOR: [
    'M4.5 4h15A1.5 1.5 0 0 1 21 5.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 14.5v-9A1.5 1.5 0 0 1 4.5 4z',
    'M9 20h6M12 16v4',
  ],
  MOUSE: ['M12 3a5 5 0 0 1 5 5v8a5 5 0 0 1-10 0V8a5 5 0 0 1 5-5z', 'M12 7v4'],
  KEYBOARD: [
    'M4 6h16a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z',
    'M6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 14h10',
  ],
  BACKEND_DEVELOPMENT: [
    'M5.5 4h13A1.5 1.5 0 0 1 20 5.5v3a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 8.5v-3A1.5 1.5 0 0 1 5.5 4z',
    'M5.5 14h13a1.5 1.5 0 0 1 1.5 1.5v3a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-3A1.5 1.5 0 0 1 5.5 14z',
    'M8 7h.01M8 17h.01M12 7h4M12 17h4',
  ],
  FRONTEND_DEVELOPMENT: [
    'M5 4h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
    'M3 8h18M10 12l-2 2 2 2M14 12l2 2-2 2',
  ],
  MOBILE_DEVELOPMENT: [
    'M9.5 2.5h5A2.5 2.5 0 0 1 17 5v14a2.5 2.5 0 0 1-2.5 2.5h-5A2.5 2.5 0 0 1 7 19V5a2.5 2.5 0 0 1 2.5-2.5z',
    'M11 18.5h2',
  ],
  DESIGN: ['M12 3l7 7-7 11-7-11z', 'M12 9.4a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2z', 'M12 12.6V21'],
};

function SelectIcon({ code, size }: { code: string; size: number }) {
  const paths = ICON_PATHS[code];
  if (paths === undefined) {
    return null;
  }
  return (
    <svg
      className="select-card__icon"
      aria-hidden="true"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
    >
      {paths.map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  );
}

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
