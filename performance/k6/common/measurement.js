// 워밍업 1분은 2026-10-14 첫 측정에서 확정했다(docs/adr/0040-성능-테스트-판정-기준.md)
const WARMUP = '1m';
const MEASURE = '10m';

export function userAgent(testName) {
  return `devica-k6/${testName}`;
}

// 워밍업 → 측정을 끊지 않고 이어 간다. 측정 구간은 `<name>_measure` 시나리오다.
// 측정 구간의 부하 모양이 워밍업과 다르면 measure를 따로 준다. measure는 스스로 MEASURE만큼 이어져야 한다
export function warmupAndMeasure(name, scenario, measure = { ...scenario, duration: MEASURE }) {
  return {
    [`${name}_warmup`]: { ...scenario, startTime: '0s', duration: WARMUP },
    [`${name}_measure`]: { ...measure, startTime: WARMUP },
  };
}

// 측정 구간에만 거는 기준. k6는 오류율만 판정한다.
// p95 200ms는 네트워크 시간을 빼고 API에 배정한 시간이라 요청 로그의 서버 처리 시간으로 판정한다
export function measureThresholds(name) {
  const selector = `{scenario:${name}_measure}`;

  return {
    [`http_req_failed${selector}`]: ['rate<=0.01'],
    // 아래 둘은 판정 기준이 아니다. 측정 구간의 k6 응답 시간과 요청 수를 요약에 따로 남긴다
    [`http_req_duration${selector}`]: ['p(95)>=0'],
    [`http_reqs${selector}`]: ['count>0'],
  };
}

// 넘으면 SLO와 관계없이 그 회차는 무효다
export const validityThresholds = {
  checks: ['rate==1'],
  dropped_iterations: ['count==0'],
};

export const summaryTrendStats = ['avg', 'med', 'p(95)', 'p(99)', 'max'];
