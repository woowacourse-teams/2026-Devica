import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { forget } from '../src/fetched';
import { ProductListView } from '../src/ProductListView';
import { EMPTY_CONDITION, type Offer, type Product } from '../src/products';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

const offer: Offer = {
  id: 10,
  name: '학생복지스토어',
  price: 900_000,
  purchaseUrl: 'https://example.test/product',
};

async function show(price: number | null, source?: Offer | null) {
  forget('products:LAPTOP?sort=RECOMMENDED');
  const product: Product = {
    id: 1,
    brand: '검증용',
    name: '테스트 노트북',
    imageUrl: null,
    specs: [],
    minPrice: price,
    minPriceOffer: source,
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
      state={{ sort: 'RECOMMENDED', condition: EMPTY_CONDITION, filterOpen: false }}
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

test.each(['쿠팡', '학생복지스토어', 'Apple 공홈'])('대표 가격 옆에 %s를 표시한다', async (name) => {
  // when
  await show(900_000, { ...offer, name });

  // then
  expect(screen.getByText('900,000원')).toBeTruthy();
  expect(screen.getByText(name)).toBeTruthy();
  expect(screen.getByRole('button', { name: '상세 보기' })).toBeTruthy();
});

test.each([undefined, null, { ...offer, price: 800_000 }])(
  '출처가 없거나 가격이 일치하지 않으면 판매처를 추정하지 않는다: %j',
  async (source) => {
    // when
    await show(900_000, source);

    // then
    expect(screen.getByText('판매처 정보 없음')).toBeTruthy();
    expect(screen.queryByText('학생복지스토어')).toBeNull();
  },
);

test('가격이 없는 제품에는 판매처를 표시하지 않는다', async () => {
  // when
  await show(null, offer);

  // then
  expect(screen.getByText('가격 정보 없음')).toBeTruthy();
  expect(screen.queryByText(/학생복지스토어|판매처 정보 없음/)).toBeNull();
});
