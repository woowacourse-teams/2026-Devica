import { useRef } from 'react';
import './ChoiceGroup.css';

export type Choice = {
  value: string;
  label: string;
  disabled?: boolean;
};

type Props = {
  choices: Choice[];
  value: string | null;
  // 이미 고른 값을 다시 누르면 null 이 온다. 원본이 선택 해제를 허용한다.
  onChange: (value: string | null) => void;
  // OS 전환처럼 하나의 스위치로 읽혀야 할 때 쓴다.
  segmented?: boolean;
  label?: string;
  disabled?: boolean;
};

export function ChoiceGroup({ choices, value, onChange, segmented = false, label, disabled = false }: Props) {
  const groupRef = useRef<HTMLDivElement>(null);

  const select = (choice: Choice, index: number) => {
    const selected = value === choice.value;
    onChange(selected ? null : choice.value);
    // 선택하면 칩이 다시 그려진다. 키보드 사용자가 자리를 잃지 않게 되돌린다.
    requestAnimationFrame(() => {
      groupRef.current?.querySelectorAll('button')[index]?.focus();
    });
  };

  return (
    <div
      className={segmented ? 'choice-group choice-group--segmented' : 'choice-group'}
      ref={groupRef}
      role="group"
      aria-label={label}
    >
      {choices.map((choice, index) => (
        <button
          className="chip"
          type="button"
          key={choice.value}
          disabled={disabled || (choice.disabled ?? false)}
          aria-pressed={value === choice.value}
          onClick={() => select(choice, index)}
        >
          {choice.label}
        </button>
      ))}
    </div>
  );
}
