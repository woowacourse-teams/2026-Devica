// 권장 사양은 서버가 계산한다. 답변을 주지 않으면 조정 전 기본 권장 사양이 온다.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

export type SpecItem = {
  code: string;
  displayName: string;
  value: string;
  displayValue: string;
  // 한 항목에 조정 조건이 여럿 걸리면 근거도 여럿이다.
  reasons: string[];
};

// 서버는 선호 OS 와 관계없이 맥·윈도우 권장안을 늘 함께 준다. 선호한 쪽은 preferred 로 알린다.
export type Spec = {
  items: SpecItem[];
  preferred: boolean;
};

type RecommendationResponse = {
  specs: Spec[];
};

// 질문이나 선택지가 바뀌어 주소에 담긴 답을 서버가 받지 않는다. 콘텐츠가 바뀌기 전의 결과 링크에서 생긴다.
export class OutdatedAnswersError extends Error {}

const OUTDATED_ANSWER_CODES = ['QUESTION_NOT_FOUND', 'ANSWER_NOT_ALLOWED'];

async function errorOf(response: Response): Promise<Error> {
  const body: { code?: string } | null = await response.json().catch(() => null);
  if (body?.code !== undefined && OUTDATED_ANSWER_CODES.includes(body.code)) {
    return new OutdatedAnswersError(body.code);
  }
  return new Error(`권장 사양을 불러오지 못했습니다 (${response.status})`);
}

/** 답을 주지 않으면 조정 전 기본 권장 사양이 온다. 경로가 같아 화면마다 나누지 않는다. */
export async function fetchRecommendation(purposeCode: string, query = ''): Promise<Spec[]> {
  const suffix = query === '' ? '' : `?${query}`;
  const response = await fetch(`${BASE_URL}/api/usage-purposes/${purposeCode}/recommendation${suffix}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw await errorOf(response);
  }

  const body: RecommendationResponse = await response.json();
  return body.specs;
}

export function osValueOf(spec: Spec): string {
  return spec.items.find((item) => item.code === 'OS')?.value ?? '';
}

// 선호 OS 를 "아직 안 정함" 으로 답했으면 없다.
export function preferredOsOf(specs: Spec[]): string | null {
  const preferred = specs.find((spec) => spec.preferred);
  return preferred === undefined ? null : osValueOf(preferred);
}
