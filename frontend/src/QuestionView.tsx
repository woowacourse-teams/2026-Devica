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

  const answered = screen.questions.some(({ question }) => (answers[question.code] ?? []).length > 0);
  const last = index === screens.length - 1;
  // 답을 고르지 않아도 넘어갈 수 있다. 미응답은 서버가 기본값으로 처리한다.
  const nextLabel = !answered ? '건너뛰기' : last ? '결과 보기' : '다음';

  // 단일 선택 질문 하나뿐인 화면은 고르는 순간 답이 정해지므로 바로 넘어간다 (#177).
  // 이미 고른 선택지를 눌러도 그 답으로 넘어가고, 답을 비우는 일은 "건너뛰기"가 맡는다. 그래서 버튼은 건너뛰기 하나다.
  const single =
    !screen.currentSpec && screen.questions.length === 1 && screen.questions[0].question.inputType === 'SINGLE'
      ? screen.questions[0].question.code
      : null;
  const answerAndAdvance = (code: string, values: string[], delay: number) => {
    onAnswer(code, values);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => latestNext.current(), delay);
  };

  return (
    <section className="view-panel question-view" id="question-view" aria-live="polite">
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
                onPick={
                  single === null ? undefined : (optionCode) => answerAndAdvance(single, [optionCode], ADVANCE_DELAY_MS)
                }
              />
            ))}
          </>
        )}
      </div>

      <div className="flow-actions">
        <button className="button button--secondary" id="previous-button" type="button" onClick={onPrevious}>
          이전
        </button>
        {single === null ? (
          <button
            className={`button ${answered ? 'button--primary' : 'button--skip'}`}
            id="next-button"
            type="button"
            onClick={onNext}
          >
            {nextLabel}
          </button>
        ) : (
          <button
            className="button button--skip"
            id="next-button"
            type="button"
            onClick={() => answerAndAdvance(single, [], 0)}
          >
            건너뛰기
          </button>
        )}
      </div>
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
