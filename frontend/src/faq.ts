const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export type FaqSummary = {
  slug: string;
  question: string;
};

export type FaqDetail = FaqSummary & {
  answer: string;
};

async function fetchJson<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`FAQ 를 불러오지 못했습니다 (${response.status})`);
  }
  return response.json();
}

/** 제품 유형과 사용 목적을 모두 골랐다면 그 조합의 FAQ 를, 아니면 서비스 공통 FAQ 를 받는다. */
export function fetchFaqs(categoryCode: string | null, purposeCode: string | null): Promise<FaqSummary[]> {
  if (categoryCode === null || purposeCode === null) {
    return fetchJson('/api/faqs');
  }
  return fetchJson(`/api/product-categories/${categoryCode}/usage-purposes/${purposeCode}/faqs`);
}

export function fetchFaq(slug: string): Promise<FaqDetail> {
  return fetchJson(`/api/faqs/${encodeURIComponent(slug)}`);
}
