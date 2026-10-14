import exec from 'k6/execution';
import { summaryTrendStats, userAgent, validityThresholds } from '../common/measurement.js';
import { runSession, sessionList } from './sessions.js';

// 1분에 15세션. 반복이 1~2회 더 돌 수 있어 여유분을 둔다
const sessions = sessionList(20);

export const options = {
  userAgent: userAgent('product-list-smoke'),
  scenarios: {
    smoke: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '4s',
      duration: '1m',
      preAllocatedVUs: 10,
      gracefulStop: '30s',
    },
  },
  thresholds: validityThresholds,
  summaryTrendStats,
};

export default function () {
  runSession(sessions[exec.scenario.iterationInTest % sessions.length]);
}
