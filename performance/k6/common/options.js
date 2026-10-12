// performance/k6/common/options.js
// 팀 공통 통과 기준과 결과 통계. 기준의 근거는 performance/README.md에 있다

// 로그와 대시보드에서 테스트 트래픽을 구분한다
export function userAgent(testName) {
  return `devica-k6/${testName}`;
}

// SLO 중 k6가 판정하는 것은 오류율 1% 이하뿐이다. scenario를 주면 그 시나리오의 요청에만 적용한다.
// p95 200ms는 네트워크를 뺀 서버 처리 시간의 예산이라, 측정 뒤 요청 로그로 판정한다
export function sloThresholds(scenario) {
  const selector = scenario ? `{scenario:${scenario}}` : '';

  return {
    [`http_req_failed${selector}`]: ['rate<=0.01'],
    // 판정 기준이 아니다. 측정 장소에서 체감한 응답 시간을 그 구간만 따로 요약에 남긴다
    [`http_req_duration${selector}`]: ['p(95)>=0'],
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
