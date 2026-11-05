import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { QuestionView } from '../src/QuestionView';
import type { Question, Screen } from '../src/questions';
import { ResultView } from '../src/ResultView';
import { fetchRecommendation } from '../src/recommendation';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const question = (code: string, title: string, options: string[]): Question => ({
  code,
  title,
  description: null,
  label: null,
  input: 'MULTI',
  exclusive: null,
  showWhen: null,
  options: options.map((option) => ({ code: option, content: option, description: null })),
});

test('설문 의견 남기기는 지금 화면의 질문과 답을 채운 설문 의견 양식을 새 탭으로 연다', () => {
  const screens: Screen[] = [
    {
      title: '어떤 IDE 를 쓰나요?',
      description: null,
      currentSpec: false,
      questions: [{ question: question('IDE', '어떤 IDE 를 쓰나요?', ['JETBRAINS', 'LIGHT_EDITOR']), label: null }],
    },
  ];

  render(
    <QuestionView
      purpose="BACKEND_DEVELOPMENT"
      surveyVersion="3f9a1c2e7b40"
      screens={screens}
      index={0}
      answers={{ PREFERRED_OS: ['MACOS'], IDE: ['JETBRAINS', 'LIGHT_EDITOR'] }}
      onAnswer={() => {}}
      onPrevious={() => {}}
      onNext={() => {}}
    />,
  );

  const link = screen.getByRole('link', { name: '의견 남기기 ↗' });
  const url = new URL(link.getAttribute('href') ?? '');
  expect(url.origin + url.pathname).toBe('https://github.com/woowacourse-teams/2026-Devica/issues/new');
  expect(Object.fromEntries(url.searchParams)).toEqual({
    template: 'product-recommendation-survey-feedback.yml',
    title: '[제품 추천 설문 의견] 어떤 IDE 를 쓰나요?',
    purpose: 'BACKEND_DEVELOPMENT',
    questions: 'IDE',
    question_title: '어떤 IDE 를 쓰나요?',
    selected: 'IDE.JETBRAINS,IDE.LIGHT_EDITOR',
    answers: 'PREFERRED_OS.MACOS,IDE.JETBRAINS,IDE.LIGHT_EDITOR',
    survey_version: '3f9a1c2e7b40',
  });
  expect(link.getAttribute('target')).toBe('_blank');
});

test('한 화면에 질문이 여럿이면 그 화면의 질문과 답만 고른 답으로 채운다', () => {
  const screens: Screen[] = [
    {
      title: '지금 쓰는 노트북',
      description: null,
      currentSpec: true,
      questions: [
        { question: question('CURRENT_CPU', '프로세서', ['MID']), label: null },
        { question: question('CURRENT_MEMORY', '메모리', ['GB_16']), label: null },
      ],
    },
  ];

  render(
    <QuestionView
      purpose="BACKEND_DEVELOPMENT"
      surveyVersion={null}
      screens={screens}
      index={0}
      answers={{ PREFERRED_OS: ['MACOS'], CURRENT_CPU: ['MID'] }}
      onAnswer={() => {}}
      onPrevious={() => {}}
      onNext={() => {}}
    />,
  );

  const url = new URL(screen.getByRole('link', { name: '의견 남기기 ↗' }).getAttribute('href') ?? '');
  expect(url.searchParams.get('questions')).toBe('CURRENT_CPU,CURRENT_MEMORY');
  expect(url.searchParams.get('selected')).toBe('CURRENT_CPU.MID');
  expect(url.searchParams.get('survey_version')).toBe('');
});

test('결과 응답에 answersOutdated 가 없으면 바꿔 읽지 않은 결과로 본다', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({ ok: true, json: async () => ({ specs: [] }) })),
  );

  expect(await fetchRecommendation('BACKEND_DEVELOPMENT')).toEqual({ specs: [], answersOutdated: false });
});

const resultView = (onReanswer?: () => void) => (
  <ResultView
    categoryCode="LAPTOP"
    specs={[]}
    os="BOTH"
    edits={{}}
    tiers={null}
    tiersFailed={false}
    onRetryTiers={() => {}}
    onChangeOs={() => {}}
    onEdit={() => {}}
    onResetEdits={() => {}}
    onSearch={() => {}}
    onReanswer={onReanswer}
  />
);

test('바꿔 읽은 결과는 바꿔 읽었다고 알리고 다시 답하게 한다', () => {
  const onReanswer = vi.fn();
  render(resultView(onReanswer));

  expect(screen.getByText(/질문이 바뀌어 지금 질문에 맞춰 다시 읽었어요/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '다시 답하기' }));
  expect(onReanswer).toHaveBeenCalledOnce();
});

test('바꿔 읽지 않은 결과는 알리지 않는다', () => {
  render(resultView());

  expect(screen.queryByRole('button', { name: '다시 답하기' })).toBeNull();
});
