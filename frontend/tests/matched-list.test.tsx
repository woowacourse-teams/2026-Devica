import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { App } from '../src/App';
import type { Spec } from '../src/recommendation';

vi.mock('../src/analytics', () => ({ track: vi.fn() }));

const item = (code: string, value: string) => ({ code, displayName: code, value, displayValue: value, reasons: [] });

const spec = (os: string, memory: string): Spec => ({
  preferred: false,
  notes: [],
  items: [item('OS', os), item('MEMORY', memory)],
});

beforeEach(() => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        url.includes('/recommendation')
          ? { specs: [spec('MAC', '16'), spec('WINDOWS', '16')] }
          : url.includes('/products?')
            ? { content: [], hasNext: false }
            : [],
    })),
  );
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  vi.stubGlobal('scrollTo', vi.fn());
});

afterEach(() => {
  cleanup();
  window.history.replaceState({}, '', '/');
  vi.unstubAllGlobals();
});

const NOTE = 'Mac과 Windows 모두 권장해요. 탭을 바꾸면 다른 OS 제품을 볼 수 있어요.';

function open(search: string) {
  window.history.replaceState({}, '', `/result/products?category=LAPTOP&purpose=BACKEND_DEVELOPMENT&${search}`);
  render(<App />);
}

test('OS 탭을 바꾸면 그 OS 의 고친 사양으로 검색 조건을 채운다', async () => {
  open('edit=MAC.MEMORY.48&edit=WINDOWS.MEMORY.32');
  await waitFor(() => expect(new URLSearchParams(window.location.search).get('memoryGb')).toBe('48'));
  expect(await screen.findByText(NOTE)).toBeTruthy();

  fireEvent.click(await screen.findByRole('button', { name: 'Windows' }));
  await waitFor(() => expect(new URLSearchParams(window.location.search).get('memoryGb')).toBe('32'));

  fireEvent.click(screen.getByRole('button', { name: 'Mac' }));
  await waitFor(() => expect(new URLSearchParams(window.location.search).get('memoryGb')).toBe('48'));
});

test('제품 추천 결과에서 OS 를 골랐으면 다른 탭 안내를 보여 주지 않는다', async () => {
  open('specOs=MAC');
  await screen.findByRole('button', { name: 'Windows' });
  expect(screen.queryByText(NOTE)).toBeNull();
});
