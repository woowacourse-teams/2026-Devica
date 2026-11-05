import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { QuestionView } from '../src/QuestionView';
import type { Question, Screen } from '../src/questions';
import { ResultView } from '../src/ResultView';

afterEach(cleanup);

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

test('질문이 바뀌기 전 링크의 결과는 바꿔 읽었다고 알리고 다시 답하게 한다', () => {
  const onReanswer = vi.fn();
  render(resultView(onReanswer));

  expect(screen.getByText(/질문이 바뀌기 전의 결과 링크예요/)).toBeTruthy();
  fireEvent.click(screen.getByRole('button', { name: '다시 답하기' }));
  expect(onReanswer).toHaveBeenCalledOnce();
});

test('바꿔 읽지 않은 결과는 알리지 않는다', () => {
  render(resultView());

  expect(screen.queryByRole('button', { name: '다시 답하기' })).toBeNull();
});
