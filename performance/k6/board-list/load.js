import {
  measureThresholds,
  summaryTrendStats,
  userAgent,
  validityThresholds,
  warmupAndMeasure,
} from '../common/measurement.js';
import { runListRequest } from './traffic.js';

// 260명 × 방문당 목록 요청 3회 ÷ 600초 = 1.3 RPS. 여유를 두어 2 RPS로 측정한다.
export const options = {
  userAgent: userAgent('board-list-load'),
  scenarios: warmupAndMeasure('board_list', {
    executor: 'constant-arrival-rate',
    rate: 2,
    timeUnit: '1s',
    // 2 RPS × 타임아웃 5초 = 약 10 VU. 느린 응답에도 요청을 시작할 여유를 둔다.
    preAllocatedVUs: 20,
    gracefulStop: '10s',
  }),
  thresholds: {
    ...measureThresholds('board_list'),
    ...validityThresholds,
    // 판정 기준이 아니라 측정 구간의 정상 응답 수를 요약에 남기는 용도다.
    'board_requests_succeeded{scenario:board_list_measure}': ['count>=0'],
  },
  summaryTrendStats,
};

export default function () {
  runListRequest();
}
