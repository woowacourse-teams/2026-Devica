import exec from 'k6/execution';
import {
  measureThresholds,
  summaryTrendStats,
  userAgent,
  validityThresholds,
  warmupAndMeasure,
} from '../common/measurement.js';
import { runSession, sessionList } from './sessions.js';

// 홍보 직후 10분간 약 260명 × 목록 진입 5번 = 1,300세션. 평균 분당 130세션(약 6.5 RPS)
// 측정 구간은 게시 직후에 몰리도록 첫 2분에 평균의 3배, 나머지 8분에 평균의 절반을 건다
const AVERAGE_PER_MINUTE = 130;
const PEAK_PER_MINUTE = AVERAGE_PER_MINUTE * 3; // 2분 × 390 = 780세션
const TAIL_PER_MINUTE = AVERAGE_PER_MINUTE / 2; // 8분 × 65 = 520세션
const WARMUP_SESSIONS = AVERAGE_PER_MINUTE; // 워밍업 1분은 평균 속도로
const MEASURE_SESSIONS = 1300;

// 종료 시점에 따라 반복이 1~2회 더 돌 수 있어 여유분을 둔다
const sessions = sessionList(WARMUP_SESSIONS + MEASURE_SESSIONS + 10);

// 세션 하나는 최대 약 25초(요청 3번 × 타임아웃 5초 + 간격 2번 × 5초)다.
// 서버가 느려져도 세션을 버리지 않도록 25초 × 초당 세션 수보다 넉넉히 둔다
const WARMUP_VUS = 60; // 초당 약 2.17세션
const MEASURE_VUS = 180; // 피크 초당 6.5세션

export const options = {
  userAgent: userAgent('product-list-load'),
  scenarios: warmupAndMeasure(
    'product_list',
    {
      executor: 'constant-arrival-rate',
      rate: AVERAGE_PER_MINUTE,
      timeUnit: '1m',
      preAllocatedVUs: WARMUP_VUS,
      // 진행 중인 세션은 끝까지 기다린다
      gracefulStop: '30s',
    },
    {
      executor: 'ramping-arrival-rate',
      startRate: PEAK_PER_MINUTE,
      timeUnit: '1m',
      // 0초 단계로 피크에서 꼬리로 바로 내려간다
      stages: [
        { target: PEAK_PER_MINUTE, duration: '2m' },
        { target: TAIL_PER_MINUTE, duration: '0s' },
        { target: TAIL_PER_MINUTE, duration: '8m' },
      ],
      preAllocatedVUs: MEASURE_VUS,
      gracefulStop: '30s',
    },
  ),
  thresholds: {
    ...measureThresholds('product_list'),
    ...validityThresholds,
  },
  summaryTrendStats,
};

// 모든 회차가 같은 세션을 같은 순서로 쓴다. 측정은 워밍업 다음 세션부터 이어진다
export default function () {
  const offset = exec.scenario.name === 'product_list_measure' ? WARMUP_SESSIONS : 0;
  runSession(sessions[(offset + exec.scenario.iterationInTest) % sessions.length]);
}
