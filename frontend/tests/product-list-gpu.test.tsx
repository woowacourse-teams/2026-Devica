import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { type ProductListState, ProductListView } from '../src/ProductListView';
import { EMPTY_CONDITION, type GpuTier, type SearchCondition } from '../src/products';

const GPU_TIERS: GpuTier[] = [
  { code: 'BASIC', name: 'M 칩', os: 'MAC', lowest: true },
  { code: 'PRO', name: 'M Pro 칩', os: 'MAC', lowest: false },
  { code: 'INTEGRATED', name: '내장 GPU', os: 'WINDOWS', lowest: true },
  { code: 'XX50', name: 'RTX xx50 급', os: 'WINDOWS', lowest: false },
];

const fetchMock = vi.fn(async (url: string) => ({
  ok: true,
  json: async () =>
    url.includes('/products?') ? { content: [], hasNext: false } : url.includes('/gpu-tiers') ? GPU_TIERS : [],
}));

beforeEach(() => {
  fetchMock.mockClear();
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function show(condition: Partial<SearchCondition>, onChangeState = vi.fn()) {
  render(
    <ProductListView
      categoryCode="LAPTOP"
      mode="ALL"
      specs={[]}
      backLabel="뒤로"
      state={{ sort: 'PRICE_ASC', condition: { ...EMPTY_CONDITION, ...condition }, filterOpen: true }}
      searching={false}
      onChangeState={onChangeState}
      onSearched={vi.fn()}
      onBack={vi.fn()}
      onDetail={vi.fn()}
      onShowAll={vi.fn()}
    />,
  );
}

const productRequests = () => fetchMock.mock.calls.map(([url]) => url).filter((url) => url.includes('/products?'));

test('Windows 에서만 가장 낮은 등급을 뺀 GPU 필터를 보여 준다', async () => {
  show({ os: 'WINDOWS' });
  const gpu = await screen.findByRole('button', { name: 'RTX xx50 급 GPU 이상' });
  expect(gpu.parentElement?.querySelectorAll('button')).toHaveLength(2);
  cleanup();

  show({ os: 'MAC' });
  await screen.findByText('조건에 맞는 제품이 없습니다.');
  expect(screen.queryByLabelText('GPU')).toBeNull();
});

test('Mac GPU 조건은 필터 없이도 조건 칩으로 보이고, 가장 낮은 등급과 다른 OS 의 등급은 걸지 않는다', async () => {
  show({ os: 'MAC', gpuTier: 'PRO' });
  expect(await screen.findByRole('button', { name: 'M Pro 칩 GPU 이상 필터 지우기' })).toBeTruthy();
  expect(productRequests().some((url) => url.includes('gpuTier=PRO'))).toBe(true);
  cleanup();
  fetchMock.mockClear();

  for (const gpuTier of ['INTEGRATED', 'PRO', 'toString']) {
    show({ os: 'WINDOWS', gpuTier });
    expect(await screen.findByText('필터를 직접 조정해 다시 확인해 주세요.')).toBeTruthy();
    cleanup();
  }
  expect(productRequests().some((url) => url.includes('gpuTier'))).toBe(false);
});

test('OS 를 바꾸면 그 OS 의 등급이 아닌 GPU 조건을 지운다', async () => {
  const onChangeState = vi.fn();
  show({ os: 'WINDOWS', gpuTier: 'XX50' }, onChangeState);
  await screen.findByRole('button', { name: 'RTX xx50 급 GPU 이상 필터 지우기' });
  const previous: ProductListState = {
    sort: 'PRICE_ASC',
    condition: { ...EMPTY_CONDITION, os: 'WINDOWS', gpuTier: 'XX50' },
    filterOpen: true,
  };

  fireEvent.click(screen.getByRole('button', { name: 'Mac' }));
  expect(onChangeState.mock.lastCall?.[0](previous).condition.gpuTier).toBeNull();
  fireEvent.click(screen.getByRole('button', { name: '둘 다' }));
  expect(onChangeState.mock.lastCall?.[0](previous).condition.gpuTier).toBeNull();
});
