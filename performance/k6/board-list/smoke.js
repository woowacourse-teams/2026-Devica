import { summaryTrendStats, userAgent, validityThresholds } from '../common/measurement.js';
import { runListRequest } from './traffic.js';

export const options = {
  userAgent: userAgent('board-list-smoke'),
  scenarios: {
    smoke: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '4s',
      duration: '1m',
      preAllocatedVUs: 5,
      gracefulStop: '10s',
    },
  },
  thresholds: validityThresholds,
  summaryTrendStats,
};

export default function () {
  runListRequest();
}
