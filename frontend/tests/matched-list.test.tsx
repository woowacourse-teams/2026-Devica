import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { App } from '../src/App';
import { forgetStartingWith } from '../src/fetched';
import type { Spec } from '../src/recommendation';

vi.mock('../src/analytics', () => ({ track: vi.fn() }));

const item = (code: string, value: string) => ({
  code,
  displayName: code,
  value,
  displayValue: value,
  basis: null,
  reasons: [],
});

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
  forgetStartingWith('');
  window.history.replaceState({}, '', '/');
  vi.unstubAllGlobals();
});

const NOTE = '탭을 바꾸면 Mac과 Windows 제품을 각각 볼 수 있어요.';

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

const TIERS = [
  { code: 'PRO', name: 'M Pro 칩', os: 'MAC' },
  { code: 'H', name: '고성능 Core Ultra 7', os: 'WINDOWS' },
  { code: 'U', name: '저전력 Core Ultra 5', os: 'WINDOWS' },
];

const MAC_PRO: Spec = {
  ...spec('MAC', '16'),
  items: [item('OS', 'MAC'), item('REQUIRED_CPU', 'PRO'), item('REQUIRED_GPU', 'PRO')],
};

const WINDOWS_U: Spec = { ...spec('WINDOWS', '16'), items: [item('OS', 'WINDOWS'), item('REQUIRED_CPU', 'U')] };

const fetchMock = vi.fn();

function hold(responses: Record<string, unknown>) {
  const pending: Record<string, () => void> = {};
  fetchMock.mockReset();
  fetchMock.mockImplementation((url: string) => {
    const name = Object.keys(responses).find((each) => url.includes(each));
    if (name === undefined) {
      return Promise.resolve({
        ok: true,
        json: async () => (url.includes('/products?') ? { content: [], hasNext: false } : []),
      });
    }
    return new Promise((resolve) => {
      const previous = pending[name];
      pending[name] = () => {
        previous?.();
        resolve({ ok: true, json: async () => responses[name] });
      };
    });
  });
  vi.stubGlobal('fetch', fetchMock);
  return pending;
}

const searchParam = (key: string) => new URLSearchParams(window.location.search).get(key);

test.each([
  ['CPU 등급', 'cpu-tiers'],
  ['제품 추천 결과', 'recommendation'],
])('고친 CPU 가 그 OS 의 등급이 아니면 권장 등급으로 채운다. %s 응답이 먼저 와도 같다', async (_, first) => {
  // given
  const pending = hold({ 'cpu-tiers': TIERS, recommendation: { specs: [MAC_PRO] } });

  // when
  open('specOs=MAC&edit=MAC.REQUIRED_CPU.H');
  const second = first === 'cpu-tiers' ? 'recommendation' : 'cpu-tiers';
  await waitFor(() => expect(pending[first]).toBeDefined());
  pending[first]();
  await waitFor(() => expect(pending[second]).toBeDefined());
  pending[second]();

  // then
  await waitFor(() => expect(searchParam('cpuTier')).toBe('PRO'));
  expect(searchParam('os')).toBe('MAC');
  expect(searchParam('gpuTier')).toBe('PRO');
});

test('등급을 받기 전에 OS 탭을 오가도 받은 뒤에는 권장 등급으로 채운다', async () => {
  // given
  const pending = hold({ 'cpu-tiers': TIERS, recommendation: { specs: [MAC_PRO, WINDOWS_U] } });
  open('specOs=BOTH&edit=MAC.REQUIRED_CPU.H&os=MAC&cpuTier=PRO&gpuTier=PRO');
  await waitFor(() => expect(pending.recommendation).toBeDefined());
  pending.recommendation();

  // when
  fireEvent.click(await screen.findByRole('button', { name: 'Windows' }));
  fireEvent.click(screen.getByRole('button', { name: 'Mac' }));
  await waitFor(() => expect(searchParam('cpuTier')).toBe('H'));
  pending['cpu-tiers']();

  // then
  await waitFor(() => expect(searchParam('cpuTier')).toBe('PRO'));
  expect(searchParam('gpuTier')).toBe('PRO');
  await waitFor(() => {
    const requests = fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.includes('/products?'));
    expect(requests.at(-1)).toContain('cpuTier=PRO');
  });
});
