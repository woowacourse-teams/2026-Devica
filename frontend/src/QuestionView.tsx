import { Fragment, useEffect, useRef } from 'react';
import { flushSync } from 'react-dom';
import { ChoiceGroup } from './ChoiceGroup';
import type { Answers, Question, Screen } from './questions';
import './QuestionView.css';

const HINT = '선택한 항목을 한 번 더 누르면 해제돼요.';
const MULTI_SENTENCE = '여러 개를 선택할 수 있어요.';

function descriptionOf(screen: Screen): string | null {
  const rest = screen.description?.replace(MULTI_SENTENCE, '').trim() ?? '';
  return rest === '' ? null : rest;
}
// 고른 항목에 체크 표시가 붙은 것만 보고 넘어간다. 넘어가는 느낌은 밀려나는 전환이 맡는다. 400ms 로 기다리면 멈칫해 보였다.
const ADVANCE_DELAY_MS = 150;

// 질문이 바뀔 때 앞으로 가면 왼쪽으로, 뒤로 가면 오른쪽으로 밀어낸다. 지원하지 않는 브라우저와 움직임 줄이기 설정에서는 바로 바꾼다.
function slide(direction: 'forward' | 'back', update: () => void) {
  if (document.startViewTransition === undefined || window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    update();
    return;
  }
  const root = document.documentElement;
  root.dataset.slide = direction;
  const transition = document.startViewTransition(() => flushSync(update));
  transition.finished.finally(() => delete root.dataset.slide);
}

const SURVEY_FEEDBACK_FORM_URL = 'https://github.com/woowacourse-teams/2026-Devica/issues/new';

const pairsOf = (answers: [string, string[]][]) =>
  answers.flatMap(([code, options]) => options.map((option) => `${code}.${option}`)).join(',');

function surveyFeedbackUrl(purpose: string, surveyVersion: string | null, screen: Screen, answers: Answers): string {
  const codes = screen.questions.map(({ question }) => question.code);
  const params = new URLSearchParams({
    template: 'product-recommendation-survey-feedback.yml',
    title: `[제품 추천 설문 의견] ${screen.title}`,
    purpose,
    questions: codes.join(','),
    question_title: screen.title,
    selected: pairsOf(Object.entries(answers).filter(([code]) => codes.includes(code))),
    answers: pairsOf(Object.entries(answers)),
    survey_version: surveyVersion ?? '',
  });
  return `${SURVEY_FEEDBACK_FORM_URL}?${params}`;
}

type Props = {
  purpose: string;
  surveyVersion: string | null;
  screens: Screen[];
  index: number;
  answers: Answers;
  outdated: boolean;
  onAnswer: (code: string, values: string[]) => void;
  onPrevious: () => void;
  onNext: () => void;
};

export function QuestionView({
  purpose,
  surveyVersion,
  screens,
  index,
  answers,
  outdated,
  onAnswer,
  onPrevious,
  onNext,
}: Props) {
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
  // 답하기 전에는 선택지를 고르는 것이 유일한 진행 수단이라 다음 버튼을 두지 않는다 (#181). 되돌아와 이미 답이 있으면 다음 버튼으로 그대로 넘어간다.
  const single =
    !screen.currentSpec && screen.questions.length === 1 && screen.questions[0].question.input === 'SINGLE'
      ? screen.questions[0].question.code
      : null;
  const answerAndAdvance = (code: string, optionCode: string) => {
    onAnswer(code, [optionCode]);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => slide('forward', () => latestNext.current()), ADVANCE_DELAY_MS);
  };

  return (
    <section className="view-panel flow-view question-view" id="question-view" aria-live="polite">
      <div className="question-progress">
        <p className="progress-meta">
          <span id="progress-label">추천 결과 만드는 중</span>
          <span id="progress-count">{index + 1}번째 질문</span>
        </p>
        <div className="progress-track" aria-hidden="true">
          <span id="progress-value" style={{ width: `${((index + 1) / screens.length) * 100}%` }} />
        </div>
        <p className="progress-note">답에 따라 질문이 늘거나 줄 수 있어요.</p>
      </div>

      <div className="question-content" id="question-content">
        <div className="flow-head">
          <h1 className="question-heading">{screen.title}</h1>
          {descriptionOf(screen) !== null && <p className="flow-head__description">{descriptionOf(screen)}</p>}
        </div>
        {outdated && (
          <p className="notice" role="status">
            설문이 바뀌어 고르신 답 중 없어진 선택지를 지웠습니다. 이 질문에 다시 답해 주세요.
          </p>
        )}

        {screen.currentSpec ? (
          <div className="current-spec-form">
            <SpecCheckGuide />
            <p className="question-hint">{HINT}</p>
            <CurrentSpecFields screen={screen} answers={answers} onAnswer={onAnswer} />
          </div>
        ) : (
          <div className="question-groups">
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
          </div>
        )}
        {!screen.currentSpec && single === null && <p className="question-hint">{HINT}</p>}
      </div>

      <p className="question-feedback">
        이 질문이 내 상황과 맞지 않나요?{' '}
        <a href={surveyFeedbackUrl(purpose, surveyVersion, screen, answers)} target="_blank" rel="noopener noreferrer">
          의견 남기기 ↗
        </a>{' '}
        <span className="question-feedback__hint">GitHub 계정이 필요해요.</span>
      </p>

      <div className="sticky-actions question-actions">
        <button className="button" id="previous-button" type="button" onClick={() => slide('back', onPrevious)}>
          ← 이전
        </button>
        {(single === null || answered) && (
          <button
            className={`button ${skippable ? 'button--skip' : 'button--primary'}`}
            id="next-button"
            type="button"
            disabled={!answered && !skippable}
            onClick={() => slide('forward', onNext)}
          >
            {nextLabel}
            {!skippable && <span aria-hidden="true">→</span>}
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

// 사양을 모르는 사용자가 어디서 확인하는지 알려 준다 (#229).
function SpecCheckGuide() {
  return (
    <section className="spec-guide" aria-labelledby="spec-guide-title">
      <h3 className="spec-guide__title" id="spec-guide-title">
        내 사양 확인하는 법
      </h3>
      <dl className="spec-guide__screens">
        <dt>Mac</dt>
        <dd>설정 › 일반 › 정보</dd>
        <dt>Windows</dt>
        <dd>설정 › 시스템 › 정보</dd>
      </dl>
    </section>
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
  const exclusiveCode = question.exclusive;

  const toggle = (optionCode: string) => {
    if (question.input === 'SINGLE') {
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
      <legend className="question-group__legend">
        {label !== null && <span>{label}</span>}
        <span className="question-mode">
          {question.input === 'SINGLE' ? '하나만 선택' : '여러 개 선택'}
          {onPick !== undefined && ' · 고르면 바로 넘어가요'}
        </span>
      </legend>
      {question.options.map((option) => (
        <button
          className={`question-option question-option--${question.input === 'SINGLE' ? 'single' : 'multi'}${option.code === exclusiveCode ? ' question-option--exclusive' : ''}`}
          type="button"
          key={option.code}
          aria-pressed={chosen.includes(option.code)}
          onClick={() => (onPick ? onPick(option.code) : toggle(option.code))}
        >
          <span className="question-option__mark" aria-hidden="true">
            <svg
              aria-hidden="true"
              width="16"
              height="16"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="3"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <path d="M5 12l5 5L20 7" />
            </svg>
          </span>
          <span className="question-option__text">
            <span className="question-option__title">{option.content}</span>
            {option.description !== null && <span className="question-option__description">{option.description}</span>}
          </span>
        </button>
      ))}
    </fieldset>
  );
}
