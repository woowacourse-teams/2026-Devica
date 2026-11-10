import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { App } from '../src/App';
import type { Question, Survey } from '../src/questions';

vi.mock('../src/analytics', () => ({ track: vi.fn() }));

const question = (code: string, title: string, option: string, content: string): Question => ({
  code,
  title,
  description: null,
  label: null,
  input: 'SINGLE',
  exclusive: null,
  showWhen: null,
  options: [{ code: option, content, description: null }],
});

afterEach(() => {
  cleanup();
  sessionStorage.removeItem('devica.answers');
  window.history.replaceState({}, '', '/');
  vi.unstubAllGlobals();
});

test('복원한 필수 답변이 사라졌으면 해당 문항에 다시 답한 뒤 추천을 요청한다', async () => {
  const purpose = 'BACKEND_DEVELOPMENT';
  const survey: Survey = {
    version: 'new-survey',
    steps: [
      { title: null, layout: null, questions: [question('PREFERRED_OS', '선호 OS', 'MACOS', 'Mac')] },
      {
        title: '현재 사양',
        layout: 'CURRENT_SPEC',
        questions: [question('CURRENT_MEMORY', '메모리', 'GB_16', '16GB')],
      },
      {
        title: null,
        layout: null,
        questions: [question('MULTI_APP_SLOWDOWN', '노트북이 느려지나요?', 'OFTEN', '자주 느려진다')],
      },
      {
        title: null,
        layout: null,
        questions: [question('STORAGE_SHORTAGE', '저장 공간이 부족했나요?', 'NEVER', '없다')],
      },
    ],
  };
  const fetch = vi.fn(async (url: string) => {
    const data = url.endsWith('/survey') ? survey : url.endsWith('/cpu-tiers') ? [] : { specs: [] };
    return { ok: true, json: async () => data };
  });
  vi.stubGlobal('fetch', fetch);
  vi.stubGlobal('matchMedia', () => ({ matches: false }));
  vi.stubGlobal('scrollTo', vi.fn());
  sessionStorage.setItem(
    'devica.answers',
    JSON.stringify({ purpose, answers: { PREFERRED_OS: ['MACOS'], SLOWDOWN: ['OFTEN'] } }),
  );
  window.history.replaceState({ from: 'question' }, '', `/questions/4?category=LAPTOP&purpose=${purpose}`);

  render(<App />);

  await screen.findByRole('heading', { name: '노트북이 느려지나요?' });
  expect(window.location.pathname).toBe('/questions/3');
  expect(screen.getByRole('status').textContent).toContain('설문이 바뀌었어요');
  expect(fetch).not.toHaveBeenCalledWith(expect.stringContaining('/recommendation'), expect.anything());

  fireEvent.click(screen.getByRole('button', { name: '자주 느려진다' }));
  expect(window.history.state.reviewQuestion).toBeUndefined();
  await screen.findByRole('heading', { name: '저장 공간이 부족했나요?' });
  expect(screen.queryByRole('status')).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '없다' }));

  await waitFor(() => {
    const recommendation = fetch.mock.calls.find(([url]) => url.includes('/recommendation?'))?.[0];
    expect(recommendation).toBeDefined();
    expect(Object.fromEntries(new URL(recommendation ?? '', window.location.origin).searchParams)).toEqual({
      PREFERRED_OS: 'MACOS',
      MULTI_APP_SLOWDOWN: 'OFTEN',
      STORAGE_SHORTAGE: 'NEVER',
    });
  });
});
