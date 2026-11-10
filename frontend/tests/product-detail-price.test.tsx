import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, expect, test, vi } from 'vitest';
import { ProductDetailView } from '../src/ProductDetailView';
import type { Offer, ProductDetail } from '../src/products';

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function quote(overrides: Partial<Offer> = {}): Offer {
  return {
    id: 10,
    name: '쿠팡',
    price: 1_000_000,
    purchaseUrl: 'https://example.test/product',
    checkedAt: '2026-10-30',
    ...overrides,
  };
}

async function show(offers: Offer[]) {
  const product: ProductDetail = {
    id: 1,
    brand: '검증용',
    name: '테스트 노트북',
    code: 'TEST-1',
    imageUrl: null,
    description: null,
    specs: [],
    offers,
  };
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => ({ ok: true, json: async () => product })),
  );
  render(<ProductDetailView productId={1} backLabel="목록으로" onBack={vi.fn()} />);
  await screen.findByRole('heading', { name: product.name });
}

test('판매처 가격을 비교하고 동률은 ID 순으로 확인일·링크와 함께 표시한다', async () => {
  // given
  const offers: Offer[] = [
    quote({ id: 2, name: '학생복지스토어', price: 900_000 }),
    quote(),
    quote({ id: 1, name: 'Apple 공홈', price: 900_000, purchaseUrl: 'https://example.test/apple' }),
  ];

  // when
  await show(offers);

  // then
  const rows = screen.getAllByRole('listitem');
  expect(rows).toHaveLength(offers.length);
  expect(within(rows[0]).getByText('Apple 공홈')).toBeTruthy();
  expect(within(rows[1]).getByText('학생복지스토어')).toBeTruthy();
  expect(within(rows[2]).getByText('쿠팡')).toBeTruthy();
  expect(document.querySelector('.product-detail__source')?.textContent).toBe('Apple 공홈');
  expect(document.querySelector('.product-detail__price strong')?.textContent).toBe('900,000원');
  const link = within(rows[0]).getByRole('link', { name: 'Apple 공홈 판매처 보기 (새 창)' });
  expect(link.textContent).toContain('판매처 보기');
  expect(link.getAttribute('href')).toBe('https://example.test/apple');
  expect(link.getAttribute('rel')).toContain('noopener');
  expect(within(rows[1]).getByRole('link', { name: '학생복지스토어 판매처 보기 (새 창)' })).toBeTruthy();
  expect(rows.map((row) => row.querySelector('time')?.getAttribute('datetime'))).toEqual(
    offers.map((offer) => offer.checkedAt),
  );
});

test('당근은 미개봉 참고가와 검색 이동을 안내하고 통합 최저가에 포함한다', async () => {
  // given
  const searchUrl = 'https://www.daangn.com/kr/search/buy-sell/?q=MacBook';
  const offers = [quote({ name: '당근', price: 800_000, purchaseUrl: searchUrl }), quote()];

  // when
  await show(offers);

  // then
  const row = within(screen.getAllByRole('listitem')[0]);
  expect(row.getByText('미개봉 매물 참고가 · 검색 결과로 이동')).toBeTruthy();
  expect(row.getByRole('link', { name: '당근 판매처 보기 (새 창)' }).getAttribute('href')).toBe(searchUrl);
  expect(document.querySelector('.product-detail__price strong')?.textContent).toBe('800,000원');
  expect(document.querySelector('.product-detail__source')?.textContent).toBe('당근');
  expect(screen.getAllByText('미개봉 매물 참고가 · 검색 결과로 이동')).toHaveLength(1);
});

test('확인일이 없는 응답은 날짜를 추정하지 않고 판매처 링크를 제공한다', async () => {
  // when
  await show([{ name: '쿠팡', price: 1_000_000, purchaseUrl: 'https://example.test/legacy' }]);

  // then
  const row = within(screen.getByRole('listitem'));
  expect(row.getByText('확인일 미기록')).toBeTruthy();
  expect(row.getByRole('link', { name: '쿠팡 판매처 보기 (새 창)' })).toBeTruthy();
});

test('판매처가 없으면 가격과 구매 링크를 만들지 않는다', async () => {
  // when
  await show([]);

  // then
  expect(screen.getByText('가격 정보 없음')).toBeTruthy();
  expect(screen.getByText('판매 중인 곳이 없습니다.')).toBeTruthy();
  expect(screen.queryByRole('link')).toBeNull();
});

test('날짜가 null이어도 확인일 미기록으로 표시한다', async () => {
  // when
  await show([quote({ checkedAt: null })]);

  // then
  expect(screen.getByText('확인일 미기록')).toBeTruthy();
  expect(document.querySelector('time')).toBeNull();
});

test.each(['javascript:alert(1)', 'data:text/html,test', '/product', '//example.test/product', '잘못된 주소'])(
  '웹 절대 주소가 아닌 URL은 링크로 제공하지 않는다: %s',
  async (purchaseUrl) => {
    // when
    await show([quote({ purchaseUrl })]);

    // then
    expect(screen.queryByRole('link')).toBeNull();
    expect(screen.getByText('링크 확인 필요')).toBeTruthy();
  },
);

test('HTTP 링크도 제공하고 확인 날짜를 브라우저 시간대로 변환하지 않는다', async () => {
  // when
  await show([quote({ purchaseUrl: 'http://example.test/product', checkedAt: '2026-01-01' })]);

  // then
  expect(screen.getByRole('link').getAttribute('href')).toBe('http://example.test/product');
  const date = document.querySelector('time');
  expect(date?.textContent).toBe('2026-01-01');
  expect(date?.getAttribute('datetime')).toBe('2026-01-01');
});
