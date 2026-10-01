import { useEffect, useId, useRef, useState } from 'react';

// 결과 화면이 알려주는 항목. 실제 값과 OS 는 결과 화면에서 보도록 여기서는 무엇을 알려주는지만 적는다.
const ITEMS = [
  { label: 'PROCESSOR', description: '작업에 필요한 CPU 등급' },
  { label: 'MEMORY', description: '필요한 RAM 용량' },
  { label: 'STORAGE', description: '필요한 SSD 용량' },
  { label: 'REASON', description: '왜 그 사양이 필요한지에 대한 근거' },
];

// hover 는 마우스를 올린 동안만 보여주는 미리보기, pinned 는 눌러서 열어 둔 상태다.
type Mode = 'closed' | 'hover' | 'pinned';

/** 첫 화면의 "이런 결과를 받아요". 마우스를 올리거나 누르면 결과로 무엇을 받는지 팝업으로 보여준다. */
export function ResultPreview() {
  const [mode, setMode] = useState<Mode>('closed');
  const wrapper = useRef<HTMLDivElement>(null);
  const panelId = useId();
  const open = mode !== 'closed';

  // 열려 있는 동안 Esc 와 바깥 누르기로 닫는다.
  useEffect(() => {
    if (!open) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMode('closed');
      }
    };
    const onPointerDown = (event: PointerEvent) => {
      if (!wrapper.current?.contains(event.target as Node)) {
        setMode('closed');
      }
    };
    document.addEventListener('keydown', onKeyDown);
    document.addEventListener('pointerdown', onPointerDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.removeEventListener('pointerdown', onPointerDown);
    };
  }, [open]);

  return (
    // 터치는 pointerenter 도 함께 일으킨다. 미리보기는 마우스일 때만 연다.
    // biome-ignore lint/a11y/noStaticElementInteractions: 마우스 미리보기만 받는다. 키보드와 터치는 안쪽 버튼이 맡는다.
    <div
      className="result-preview"
      ref={wrapper}
      onPointerEnter={(event) => event.pointerType === 'mouse' && mode === 'closed' && setMode('hover')}
      onPointerLeave={(event) => event.pointerType === 'mouse' && mode === 'hover' && setMode('closed')}
      onBlur={(event) => {
        if (!wrapper.current?.contains(event.relatedTarget as Node | null)) {
          setMode('closed');
        }
      }}
    >
      <button
        className="result-preview__trigger"
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setMode(mode === 'pinned' ? 'closed' : 'pinned')}
      >
        이런 결과를 받아요
        <span className="result-preview__mark" aria-hidden="true">
          {open ? '−' : '+'}
        </span>
      </button>
      <div className="result-preview__panel" id={panelId} hidden={!open}>
        <p className="result-preview__title">질문에 답하면 내 작업에 맞춘 권장 사양을 알려드려요</p>
        <dl className="result-preview__items">
          {ITEMS.map((item) => (
            <div key={item.label}>
              <dt>{item.label}</dt>
              <dd>{item.description}</dd>
            </div>
          ))}
        </dl>
        <p className="result-preview__footer">
          이 사양을 기준으로, 조건을 만족하는 노트북만 골라 바로 검색할 수 있어요.
        </p>
      </div>
    </div>
  );
}
