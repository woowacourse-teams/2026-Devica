import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { forget } from '../src/fetched';
import { ProductListView } from '../src/ProductListView';
import { EMPTY_CONDITION, type Product, type SpecMatch } from '../src/products';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const item = (code: string, value: string, displayValue = value) => ({ code, displayName: code, value, displayValue });

async function show(mode: 'ALL' | 'MATCHED', specMatch: SpecMatch | null) {
  forget('products:LAPTOP?sort=PRICE_ASC');
  forget('products:LAPTOP?os=MAC&sort=PRICE_ASC');
  const product: Product = {
    id: 1,
    brand: 'Apple',
    name: '테스트 노트북',
    imageUrl: null,
    minPrice: 1_000_000,
    specs: [
      item('OS', 'MAC', 'Mac'),
      item('CPU', 'Apple M5 Pro'),
      item('MEMORY', '48', '48GB'),
      item('STORAGE', '512', '512GB'),
      item('COLOR_GAMUT', 'WIDE', '넓은 색 영역(DCI-P3)'),
    ],
    specMatch,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => ({
      ok: true,
      json: async () => (url.includes('/products?') ? { content: [product], hasNext: false } : []),
    })),
  );
  render(
    <ProductListView
      categoryCode="LAPTOP"
      mode={mode}
      specs={[]}
      backLabel="뒤로"
      state={{ sort: 'PRICE_ASC', condition: EMPTY_CONDITION, filterOpen: false }}
      searching={false}
      onChangeState={vi.fn()}
      onSearched={vi.fn()}
      onBack={vi.fn()}
      onDetail={vi.fn()}
      onShowAll={vi.fn()}
    />,
  );
  await screen.findByRole('heading', { name: product.name });
}

test('맞춤 목록의 사양 일치 제품에 딱 맞는 사양 배지를 붙인다', async () => {
  // when
  await show('MATCHED', { status: 'EXACT', exceededSpecs: [] });

  // then
  expect(screen.getByText('딱 맞는 사양')).toBeTruthy();
  expect(screen.queryByText('↑')).toBeNull();
});

test('맞춤 목록의 사양 초과 제품은 높은 항목에만 표시를 붙인다', async () => {
  // when
  await show('MATCHED', { status: 'EXCEEDED', exceededSpecs: ['MEMORY'] });

  // then
  expect(screen.queryByText('딱 맞는 사양')).toBeNull();
  const specs = Array.from(document.querySelectorAll('.product-card__specs li'), (li) => li.textContent);
  expect(specs).toContain('48GB\u2060↑, 필터보다 높음');
  expect(specs).toContain('512GB');
});

test('전체 제품 목록에서는 판정 결과가 와도 표시하지 않는다', async () => {
  // when
  await show('ALL', { status: 'EXACT', exceededSpecs: [] });

  // then
  expect(screen.queryByText('딱 맞는 사양')).toBeNull();
});

test('화면 색 영역은 카드에서 짧은 이름으로 보여 준다', async () => {
  // when
  await show('ALL', null);

  // then
  expect(screen.getByText('DCI-P3')).toBeTruthy();
});
