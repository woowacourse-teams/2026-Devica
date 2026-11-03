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
  priceCondition: '학생 인증 필요',
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

test.each([
  ['쿠팡', '일반 판매가'],
  ['학생복지스토어', '학생 인증 필요'],
  ['당근', '판교 · 미개봉 · 확인 매물 중 최저가'],
])('대표 가격 옆에 %s의 조건을 표시한다', async (name, priceCondition) => {
  // when
  await show(900_000, { ...offer, name, priceCondition });

  // then
  expect(screen.getByText('900,000원')).toBeTruthy();
  expect(screen.getByText(`${name} · ${priceCondition}`)).toBeTruthy();
  expect(screen.getByRole('button', { name: '상세 보기' })).toBeTruthy();
});

test.each([undefined, null, { ...offer, price: 800_000 }])(
  '구버전 또는 불일치 출처의 조건을 추정하지 않는다: %j',
  async (source) => {
    // when
    await show(900_000, source);

    // then
    expect(screen.getByText('판매처·조건 정보 없음')).toBeTruthy();
    expect(screen.queryByText(/일반 판매가|학생 인증 필요/)).toBeNull();
  },
);

test('조건이 없는 판매처와 가격 자체가 없는 제품을 구분한다', async () => {
  // when: 가격 조건 누락
  await show(900_000, { ...offer, priceCondition: undefined });

  // then
  expect(screen.getByText('학생복지스토어 · 조건 정보 없음')).toBeTruthy();

  // when: 가격 자체가 없음
  cleanup();
  await show(null, offer);

  // then
  expect(screen.getByText('가격 정보 없음')).toBeTruthy();
  expect(screen.queryByText(/학생복지스토어|판매처·조건 정보 없음/)).toBeNull();
});

test('긴 조건을 숨기거나 HTML로 실행하지 않는다', async () => {
  // given
  const priceCondition = `${'특정 학생 자격 필요 '.repeat(20)}<script>test</script>`;

  // when
  await show(900_000, { ...offer, priceCondition });

  // then
  expect(screen.getByText(`학생복지스토어 · ${priceCondition}`)).toBeTruthy();
  expect(document.querySelector('script')).toBeNull();
});
