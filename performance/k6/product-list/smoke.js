// performance/k6/product-list/smoke.js
// 초당 약 0.25 세션(약 1 RPS)으로 1분
import exec from 'k6/execution';
import {
  sloThresholds,
  summaryTrendStats,
  userAgent,
  validityThresholds,
} from '../common/options.js';
import { runSession, sessionList } from './sessions.js';

const sessions = sessionList(15);

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
  thresholds: {
    ...sloThresholds(),
    ...validityThresholds,
  },
  summaryTrendStats,
};

export default function () {
  runSession(sessions[exec.scenario.iterationInTest % sessions.length]);
}
