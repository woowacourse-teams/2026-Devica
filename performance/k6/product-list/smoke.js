// performance/k6/product-list/smoke.js
// 초당 약 0.25 세션(약 1 RPS)으로 1분
import exec from 'k6/execution';
import { summaryTrendStats, userAgent, validityThresholds } from '../common/options.js';
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
  // 스모크는 스크립트와 서버가 정상인지만 본다. 워밍업 없이 요청이 적어 SLO 판정에 쓰지 않는다
  thresholds: validityThresholds,
  summaryTrendStats,
};

export default function () {
  runSession(sessions[exec.scenario.iterationInTest % sessions.length]);
}
