import exec from 'k6/execution';
import {
  measureThresholds,
  summaryTrendStats,
  userAgent,
  validityThresholds,
  warmupAndMeasure,
} from '../common/measurement.js';
import { runSession, sessionList } from './sessions.js';

// 홍보 직후 10분간 약 260명 = 6초에 13세션. 세션당 평균 4요청이라 약 9 RPS
const SESSIONS = 13;
const TIME_UNIT = '6s';
const WARMUP_SESSIONS = 130; // 워밍업 1분
const MEASURE_SESSIONS = 1300; // 측정 10분

// 종료 시점에 따라 반복이 1~2회 더 돌 수 있어 여유분을 둔다
const sessions = sessionList(WARMUP_SESSIONS + MEASURE_SESSIONS + 10);

export const options = {
  userAgent: userAgent('product-list-load'),
  scenarios: warmupAndMeasure('product_list', {
    executor: 'constant-arrival-rate',
    rate: SESSIONS,
    timeUnit: TIME_UNIT,
    // 세션 하나는 최대 약 25초(요청 3번 × 타임아웃 5초 + 간격 2번 × 5초)다.
    // 서버가 느려져도 세션을 버리지 않도록 25초 × 초당 약 2.17세션보다 넉넉히 둔다
    preAllocatedVUs: 60,
    // 진행 중인 세션은 끝까지 기다린다
    gracefulStop: '30s',
  }),
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
