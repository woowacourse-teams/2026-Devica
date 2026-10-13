export function userAgent(testName) {
  return `devica-k6/${testName}`;
}

// k6는 오류율만 판정한다. p95 200ms는 네트워크를 뺀 서버 처리 시간의 예산이라 요청 로그로 판정한다
export function sloThresholds(scenario) {
  const selector = scenario ? `{scenario:${scenario}}` : '';

  return {
    [`http_req_failed${selector}`]: ['rate<=0.01'],
    // 판정 기준이 아니다. 측정 장소에서 체감한 응답 시간을 그 구간만 따로 요약에 남긴다
    [`http_req_duration${selector}`]: ['p(95)>=0'],
  };
}

// 넘으면 SLO와 관계없이 그 회차는 무효다
export const validityThresholds = {
  checks: ['rate==1'],
  dropped_iterations: ['count==0'],
};

export const summaryTrendStats = ['avg', 'med', 'p(95)', 'p(99)', 'max'];
