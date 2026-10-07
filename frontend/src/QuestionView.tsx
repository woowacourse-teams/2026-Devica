import { Fragment, useEffect, useRef } from 'react';
import { ChoiceGroup } from './ChoiceGroup';
import type { Answers, Question, Screen } from './questions';
import './QuestionView.css';

const HINT = '선택한 항목을 한 번 더 누르면 해제됩니다.';
// 고른 항목이 눌린 모습으로 바뀌자마자 넘어간다. 선택지 배경 전환(120ms)과 맞춘다. 더 길면 멈칫한다.
const ADVANCE_DELAY_MS = 120;

type Props = {
  screens: Screen[];
  index: number;
  answers: Answers;
  onAnswer: (code: string, values: string[]) => void;
  onPrevious: () => void;
  onNext: () => void;
};

export function QuestionView({ screens, index, answers, onAnswer, onPrevious, onNext }: Props) {
  const timer = useRef<number | undefined>(undefined);
  // 타이머가 울릴 때는 방금 고른 답이 반영된 onNext 를 불러야 한다.
  const latestNext = useRef(onNext);
  useEffect(() => {
    latestNext.current = onNext;
  });
  // 기다리는 사이 화면이 바뀌면(이전, 브라우저 뒤로 가기) 예약한 이동을 취소한다.
  // biome-ignore lint/correctness/useExhaustiveDependencies: index 가 바뀔 때마다 정리해야 한다
  useEffect(() => () => window.clearTimeout(timer.current), [index]);

  const screen = screens[index];
  if (screen === undefined) {
    return null;
  }

  const isAnswered = ({ question }: { question: Question }) => (answers[question.code] ?? []).length > 0;
  const last = index === screens.length - 1;
  // 질문 화면은 선택지에 "모르겠다"류 답이 있어 건너뛰기를 두지 않는다. 모든 질문에 답해야 넘어간다 (#181).
  // 현재 사양에는 그런 선택지가 없어 비워 두고 넘어갈 수 있게 한다. 미응답은 서버가 기본값으로 처리한다.
  const answered = screen.currentSpec ? screen.questions.some(isAnswered) : screen.questions.every(isAnswered);
  const skippable = screen.currentSpec && !answered;
  const nextLabel = skippable ? '건너뛰기' : last ? '결과 보기' : '다음';

  // 단일 선택 질문 하나뿐인 화면은 고르는 순간 답이 정해지므로 바로 넘어간다 (#177).
  // 선택지를 고르는 것이 유일한 진행 수단이라 아래 버튼을 두지 않는다 (#181). 이미 고른 선택지를 눌러도 그 답으로 넘어간다.
  const single =
    !screen.currentSpec && screen.questions.length === 1 && screen.questions[0].question.inputType === 'SINGLE'
      ? screen.questions[0].question.code
      : null;
  const answerAndAdvance = (code: string, optionCode: string) => {
    onAnswer(code, [optionCode]);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => latestNext.current(), ADVANCE_DELAY_MS);
  };

  return (
    <section className="view-panel question-view" id="question-view" aria-live="polite">
      {/* 선택지가 많아도 스크롤 없이 닿도록 진행 표시 위에 둔다 (#181). */}
      <button className="text-button view-back-button" id="previous-button" type="button" onClick={onPrevious}>
        ← 이전
      </button>
      <p className="progress-meta">
        <span id="progress-label">사양 맞추는 중</span>
        <span id="progress-count">
          {index + 1} / {screens.length}
        </span>
      </p>
      <div className="progress-track" aria-hidden="true">
        <span id="progress-value" style={{ width: `${((index + 1) / screens.length) * 100}%` }} />
      </div>

      <div id="question-content">
        <h2 className="question-heading">{screen.title}</h2>
        {screen.description !== null && <p className="question-description">{screen.description}</p>}

        {screen.currentSpec ? (
          <div className="current-spec-form">
            <p className="choice-hint">{HINT}</p>
            <CurrentSpecFields screen={screen} answers={answers} onAnswer={onAnswer} />
          </div>
        ) : (
          <>
            {single === null && <p className="choice-hint">{HINT}</p>}
            {screen.questions.map(({ question, label }) => (
              <QuestionGroup
                key={question.code}
                question={question}
                label={label}
                chosen={answers[question.code] ?? []}
                onAnswer={onAnswer}
                onPick={single === null ? undefined : (optionCode) => answerAndAdvance(single, optionCode)}
              />
            ))}
          </>
        )}
      </div>

      {single === null && (
        <div className="flow-actions">
          <button
            className={`button ${skippable ? 'button--skip' : 'button--primary'}`}
            id="next-button"
            type="button"
            disabled={!answered && !skippable}
            onClick={onNext}
          >
            {nextLabel}
          </button>
        </div>
      )}
    </section>
  );
}

// 현재 사양은 레이블만 달린 칩 목록이다. OS 를 고르기 전에는 프로세서 선택지가 없다.
function CurrentSpecFields({ screen, answers, onAnswer }: Pick<Props, 'answers' | 'onAnswer'> & { screen: Screen }) {
  const hasCpu = screen.questions.some(({ question }) => question.code.endsWith('_CPU'));

  return (
    <>
      {screen.questions.map(({ question, label }, order) => (
        <Fragment key={question.code}>
          <fieldset className="current-spec-field">
            <legend>{label}</legend>
            <ChoiceGroup
              choices={question.options.map((option) => ({ value: option.code, label: option.content }))}
              value={answers[question.code]?.[0] ?? null}
              onChange={(value) => onAnswer(question.code, value === null ? [] : [value])}
            />
          </fieldset>
          {/* 프로세서는 OS 다음 자리다. 고르기 전이라 선택지가 없어도 자리는 지킨다. */}
          {order === 0 && !hasCpu && (
            <fieldset className="current-spec-field">
              <legend>PROCESSOR</legend>
              <p className="choice-notice">OS를 먼저 선택해 주세요</p>
            </fieldset>
          )}
        </Fragment>
      ))}
    </>
  );
}

type GroupProps = {
  question: Question;
  label: string | null;
  chosen: string[];
  onAnswer: (code: string, values: string[]) => void;
  // 있으면 누른 선택지를 토글하지 않고 그대로 넘긴다.
  onPick?: (optionCode: string) => void;
};

function QuestionGroup({ question, label, chosen, onAnswer, onPick }: GroupProps) {
  const exclusiveCode = question.options.find((option) => option.exclusive)?.code ?? null;

  const toggle = (optionCode: string) => {
    if (question.inputType === 'SINGLE') {
      onAnswer(question.code, chosen.includes(optionCode) ? [] : [optionCode]);
      return;
    }
    if (optionCode === exclusiveCode) {
      onAnswer(question.code, chosen.length === 1 && chosen[0] === optionCode ? [] : [optionCode]);
      return;
    }
    // 다른 선택지를 고르면 배타 선택지는 빠진다.
    const rest = chosen.filter((code) => code !== exclusiveCode);
    onAnswer(
      question.code,
      rest.includes(optionCode) ? rest.filter((code) => code !== optionCode) : [...rest, optionCode],
    );
  };

  return (
    <fieldset className="question-group">
      {label !== null && <legend>{label}</legend>}
      {question.options.map((option) => (
        <button
          className="question-option"
          type="button"
          key={option.code}
          aria-pressed={chosen.includes(option.code)}
          onClick={() => (onPick ? onPick(option.code) : toggle(option.code))}
        >
          <span className="question-option__title">{option.content}</span>
          {option.description !== null && <span className="question-option__description">{option.description}</span>}
        </button>
      ))}
    </fieldset>
  );
}
