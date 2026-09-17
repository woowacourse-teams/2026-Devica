import { Fragment } from 'react';
import { ChoiceGroup } from './ChoiceGroup';
import type { Answers, Question, Screen } from './questions';
import './QuestionView.css';

const HINT = '선택한 항목을 한 번 더 누르면 해제됩니다.';

type Props = {
  screens: Screen[];
  index: number;
  answers: Answers;
  onAnswer: (code: string, values: string[]) => void;
  onPrevious: () => void;
  onNext: () => void;
};

export function QuestionView({ screens, index, answers, onAnswer, onPrevious, onNext }: Props) {
  const screen = screens[index];
  if (screen === undefined) {
    return null;
  }

  const answered = screen.questions.some(({ question }) => (answers[question.code] ?? []).length > 0);
  const last = index === screens.length - 1;
  // 답을 고르지 않아도 넘어갈 수 있다. 미응답은 서버가 기본값으로 처리한다.
  const nextLabel = !answered ? '건너뛰기' : (last ? '결과 보기' : '다음');

  return (
    <section className="view-panel question-view" id="question-view" aria-live="polite">
      <p className="progress-meta">
        <span id="progress-label">사양 맞추는 중</span>
        <span id="progress-count">{index + 1} / {screens.length}</span>
      </p>
      <div className="progress-track" aria-hidden="true">
        <span id="progress-value" style={{ width: `${((index + 1) / screens.length) * 100}%` }}/>
      </div>

      <div id="question-content">
        <h2 className="question-heading">{screen.title}</h2>
        {screen.description !== null && <p className="question-description">{screen.description}</p>}

        {screen.currentSpec ? (
          <div className="current-spec-form">
            <p className="choice-hint">{HINT}</p>
            <CurrentSpecFields screen={screen} answers={answers} onAnswer={onAnswer}/>
          </div>
        ) : (
          <>
            <p className="choice-hint">{HINT}</p>
            {screen.questions.map(({ question, label }) => (
              <QuestionGroup
                key={question.code}
                question={question}
                label={label}
                chosen={answers[question.code] ?? []}
                onAnswer={onAnswer}
              />
            ))}
          </>
        )}
      </div>

      <div className="flow-actions">
        <button className="button button--secondary" id="previous-button" type="button" onClick={onPrevious}>
          이전
        </button>
        <button
          className={`button ${answered ? 'button--primary' : 'button--skip'}`}
          id="next-button"
          type="button"
          onClick={onNext}
        >
          {nextLabel}
        </button>
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
};

function QuestionGroup({ question, label, chosen, onAnswer }: GroupProps) {
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
          onClick={() => toggle(option.code)}
        >
          <span className="question-option__title">{option.content}</span>
          {option.description !== null && (
            <span className="question-option__description">{option.description}</span>
          )}
        </button>
      ))}
    </fieldset>
  );
}
