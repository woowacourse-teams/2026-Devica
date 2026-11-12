import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { forget } from '../src/fetched';
import { ProductListView } from '../src/ProductListView';
import { EMPTY_CONDITION, type Product } from '../src/products';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

async function show(price: number | null) {
  forget('products:LAPTOP?sort=PRICE_ASC');
  const product: Product = {
    id: 1,
    brand: '검증용',
    name: '테스트 노트북',
    imageUrl: null,
    specs: [],
    minPrice: price,
    specMatch: null,
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
      mode="ALL"
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

test('목록에 대표 가격과 상세 링크를 표시한다', async () => {
  // when
  await show(900_000);

  // then
  expect(screen.getByText('900,000원')).toBeTruthy();
  expect(screen.getByRole('link', { name: /테스트 노트북/ }).getAttribute('href')).toBe('/products/1');
  expect(screen.queryByText('판매처 정보 없음')).toBeNull();
});

test('가격이 없는 제품에는 가격 정보 없음을 표시한다', async () => {
  // when
  await show(null);

  // then
  expect(screen.getByText('가격 정보 없음')).toBeTruthy();
});
