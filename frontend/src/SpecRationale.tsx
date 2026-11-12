import { useId, useRef } from 'react';
import { createPortal } from 'react-dom';
import { CloseIcon } from './icons';
import type { SpecItem } from './recommendation';

type Props = {
  item: SpecItem;
  osLabel: string;
};

export function SpecRationale({ item, osLabel }: Props) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  const close = () => ref.current?.close();
  const answered = item.reasons.length > 0;

  const { basis } = item;
  const Subheading = answered ? 'h4' : 'h3';

  if (basis === null && !answered) {
    return null;
  }

  return (
    <>
      <button
        className="rationale-trigger"
        type="button"
        aria-haspopup="dialog"
        onClick={() => ref.current?.showModal()}
      >
        <span className="rationale-trigger__hint">
          {item.displayName} {item.displayValue}{' '}
        </span>
        설명 보기
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
            {basis !== null && (
              <section>
                {answered && <h3>기본 권장 사양의 근거</h3>}
                <p className="rationale-dialog__summary">{basis.summary}</p>
                <ul>
                  {basis.points.map((point) => (
                    <li key={point}>{point}</li>
                  ))}
                </ul>
                {basis.upgrade.length > 0 && (
                  <>
                    <Subheading className="rationale-dialog__subheading">이럴 땐 더 필요해요</Subheading>
                    <ul>
                      {basis.upgrade.map((each) => (
                        <li key={each}>{each}</li>
                      ))}
                    </ul>
                  </>
                )}
                {basis.tip !== null && <p className="rationale-dialog__tip">{basis.tip}</p>}
              </section>
            )}
          </div>
        </dialog>,
        document.body,
      )}
    </>
  );
}
