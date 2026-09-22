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

export type Spec = {
    items: SpecItem[];
};

type RecommendationResponse = {
    specs: Spec[];
};

/** 답을 주지 않으면 조정 전 기본 권장 사양이 온다. 경로가 같아 화면마다 나누지 않는다. */
export async function fetchRecommendation(purposeCode: string, query = ''): Promise<Spec[]> {
    const suffix = query === '' ? '' : `?${query}`;
    const response = await fetch(`${BASE_URL}/api/usage-purposes/${purposeCode}/recommendation${suffix}`, {
        headers: {Accept: 'application/json'},
    });
    if (!response.ok) {
        throw new Error(`권장 사양을 불러오지 못했습니다 (${response.status})`);
    }

    const body: RecommendationResponse = await response.json();
    return body.specs;
}

export function osValueOf(spec: Spec): string {
    return spec.items.find((item) => item.code === 'OS')?.value ?? '';
}
