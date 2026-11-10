import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { App } from '../src/App';
import type { Spec } from '../src/recommendation';

vi.mock('../src/analytics', () => ({ track: vi.fn() }));

const item = (code: string, value: string, displayValue: string) => ({
  code,
  displayName: code,
  value,
  displayValue,
  basis: null,
  reasons: [],
});

const spec = (os: string): Spec => ({
  items: [item('OS', os, os), item('REQUIRED_CPU', 'BASIC', 'M 칩'), item('MEMORY', '16', '16GB')],
  preferred: false,
  notes: [],
});

afterEach(() => {
  cleanup();
  window.history.replaceState({}, '', '/');
  vi.unstubAllGlobals();
});

test('답 없이 맞춤 목록 주소로 바로 들어오면 뒤로 가기가 기본 권장 사양 화면으로 간다', async () => {
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () =>
        url.includes('/recommendation')
          ? { specs: [spec('MAC'), spec('WINDOWS')] }
          : url.includes('/products?')
            ? { content: [], hasNext: false }
            : [],
    })),
  );
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener: vi.fn(), removeEventListener: vi.fn() }));
  vi.stubGlobal('scrollTo', vi.fn());
  window.history.replaceState(
    {},
    '',
    '/result/products?category=LAPTOP&purpose=BACKEND_DEVELOPMENT&os=MAC&cpuTier=BASIC&memoryGb=16',
  );

  render(<App />);

  fireEvent.click(await screen.findByRole('button', { name: '← 기본 권장 사양으로' }));

  expect(window.location.pathname).toBe('/intro');
  expect(window.location.search).toBe('?category=LAPTOP&purpose=BACKEND_DEVELOPMENT');
});
