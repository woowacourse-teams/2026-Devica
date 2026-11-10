import { type ReactNode, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { CloseIcon } from './ProductListView';
import type { SpecItem } from './recommendation';

type Props = {
  item: SpecItem;
  osLabel: string;
  children: ReactNode;
};

export function SpecRationale({ item, osLabel, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const close = () => ref.current?.close();
  const answered = item.reasons.length > 0;

  return (
    <>
      <button
        className="rationale-trigger"
        type="button"
        aria-haspopup="dialog"
        aria-label={`${item.displayName} ${item.displayValue}, 권장 이유 보기`}
        onClick={() => ref.current?.showModal()}
      >
        {children}
        <InfoIcon />
      </button>
      {createPortal(
        // biome-ignore lint/a11y/useKeyWithClickEvents: 키보드로는 Esc 로 닫는다. 이 클릭은 창 바깥의 배경을 누른 경우만 받는다.
        <dialog
          className="rationale-dialog"
          ref={ref}
          aria-labelledby={titleId}
          onClick={(event) => event.target === event.currentTarget && close()}
        >
          <div className="rationale-dialog__head">
            <div>
              <p className="rationale-dialog__eyebrow">{osLabel} · 권장 이유</p>
              <h2 id={titleId}>
                {item.displayName} {item.displayValue}
              </h2>
            </div>
            <button className="rationale-dialog__close" type="button" aria-label="닫기" onClick={close}>
              <CloseIcon size={24} />
            </button>
          </div>
          <div className="rationale-dialog__body">
            {answered && (
              <section>
                <h3>내 답변에서 반영한 점</h3>
                <ul>
                  {item.reasons.map((reason) => (
                    <li key={reason}>{reason}</li>
                  ))}
                </ul>
              </section>
            )}
            {item.basis !== null && (
              <section>
                {answered && <h3>기본 권장 사양의 근거</h3>}
                <p>{item.basis}</p>
              </section>
            )}
          </div>
        </dialog>,
        document.body,
      )}
    </>
  );
}

function InfoIcon() {
  return (
    <svg
      className="rationale-trigger__icon"
      aria-hidden="true"
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 11v5M12 8h.01" />
    </svg>
  );
}
