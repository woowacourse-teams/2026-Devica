// performance/k6/common/options.js
// 팀 공통 통과 기준과 결과 통계. 기준의 근거는 performance/README.md에 있다

// 로그와 대시보드에서 테스트 트래픽을 구분한다
export function userAgent(testName) {
  return `devica-k6/${testName}`;
}

// SLO: 오류율 1% 이하, p95 200ms 이하. scenario를 주면 그 시나리오의 요청에만 적용한다
export function sloThresholds(scenario) {
  const selector = scenario ? `{scenario:${scenario}}` : '';

  return {
    [`http_req_failed${selector}`]: ['rate<=0.01'],
    [`http_req_duration${selector}`]: ['p(95)<=200'],
  };
}

// 결과 유효성. 넘으면 SLO와 관계없이 그 회차는 무효다
// checks: 4xx나 200 응답의 형식 오류는 스크립트 버그나 API 계약 변경이다
// dropped_iterations: 0이 아니면 의도한 부하가 걸리지 않은 것이다
export const validityThresholds = {
  checks: ['rate==1'],
  dropped_iterations: ['count==0'],
};

export const summaryTrendStats = ['avg', 'med', 'p(95)', 'p(99)', 'max'];
