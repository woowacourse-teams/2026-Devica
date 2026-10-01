import { diagnose } from './diagnose';

// 비어 있으면 상대 경로로 호출한다. 프론트엔드를 서빙하는 쪽이 /api 와 /actuator 를 백엔드로 넘긴다는 전제다.
const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

type Evaluation = {
  ok: boolean;
  summary: string;
  reason?: string;
};

export type Check = {
  label: string;
  path: string;
  evaluate: (body: unknown) => Evaluation;
};

export type CheckResult = {
  status: number | null;
  elapsedMs: number;
  ok: boolean;
  summary: string;
  diagnosis: string | null;
};

type CodeName = {
  code: string;
  name: string;
};

function isCodeName(item: unknown): item is CodeName {
  return (
    typeof item === 'object' &&
    item !== null &&
    typeof (item as CodeName).code === 'string' &&
    typeof (item as CodeName).name === 'string'
  );
}

function evaluateCodeNames(body: unknown, emptyReason: string): Evaluation {
  if (!Array.isArray(body) || !body.every(isCodeName)) {
    return {
      ok: false,
      summary: describeBody(body),
      reason: '응답 형식이 예상과 다릅니다. 요청이 백엔드가 아닌 다른 곳으로 넘어가고 있지 않은지 확인하세요.',
    };
  }
  if (body.length === 0) {
    return { ok: false, summary: '0건', reason: emptyReason };
  }

  const preview = body
    .slice(0, 3)
    .map((item) => `${item.code}(${item.name})`)
    .join(', ');
  return { ok: true, summary: `${body.length}건 · ${preview}${body.length > 3 ? ' 외' : ''}` };
}

function describeBody(body: unknown): string {
  const error = body as { message?: unknown; code?: unknown } | null;
  if (error !== null && typeof error.code === 'string' && typeof error.message === 'string') {
    return `${error.code} · ${error.message}`;
  }
  return body === null ? '본문 없음' : JSON.stringify(body).slice(0, 200);
}

// 앞의 점검이 실패해도 뒤의 점검이 원인을 좁혀 주므로 중간에 멈추지 않고 전부 호출한다.
export const CHECKS: Check[] = [
  {
    label: 'HEALTH',
    path: '/actuator/health',
    evaluate: (body) => {
      const status = (body as { status?: unknown } | null)?.status;
      return status === 'UP'
        ? { ok: true, summary: '{"status":"UP"}' }
        : {
            ok: false,
            summary: describeBody(body),
            reason: '백엔드는 응답하지만 구성 요소 하나가 준비되지 않았습니다. 대개 데이터베이스 연결입니다.',
          };
    },
  },
  {
    // 리포지토리를 거치므로 이 호출이 곧 데이터베이스 조회다.
    label: 'CATEGORIES',
    path: '/api/product-categories',
    evaluate: (body) =>
      evaluateCodeNames(
        body,
        '데이터베이스는 연결됐지만 제품 종류가 비어 있습니다. Flyway 기준 데이터 마이그레이션이 반영됐는지 확인하세요.',
      ),
  },
  {
    label: 'USAGE PURPOSES',
    path: '/api/product-categories/LAPTOP/usage-purposes',
    evaluate: (body) =>
      evaluateCodeNames(
        body,
        '노트북에 연결된 사용 목적이 없습니다. Flyway 기준 데이터 마이그레이션이 반영됐는지 확인하세요.',
      ),
  },
];

export async function runCheck(check: Check): Promise<CheckResult> {
  const startedAt = performance.now();
  try {
    const response = await fetch(`${BASE_URL}${check.path}`, { headers: { Accept: 'application/json' } });
    const body: unknown = await response.json().catch(() => null);
    const elapsedMs = Math.round(performance.now() - startedAt);

    if (!response.ok) {
      return {
        status: response.status,
        elapsedMs,
        ok: false,
        summary: describeBody(body),
        diagnosis: diagnose(response.status),
      };
    }

    const evaluation = check.evaluate(body);
    return {
      status: response.status,
      elapsedMs,
      ok: evaluation.ok,
      summary: evaluation.summary,
      diagnosis: evaluation.ok ? null : (evaluation.reason ?? null),
    };
  } catch {
    // 상태 코드 없이 실패했다면 응답이 아예 오지 않은 것이다.
    return {
      status: null,
      elapsedMs: Math.round(performance.now() - startedAt),
      ok: false,
      summary: '응답 없음',
      diagnosis: diagnose(null),
    };
  }
}
