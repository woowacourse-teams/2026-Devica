// performance/k6/board-list/load.js
import {
  validityThresholds,
  sloThresholds,
  summaryTrendStats,
  userAgent,
} from '../common/options.js';
import { getBoardPosts } from './requests.js';

const rps = Number(__ENV.RPS || 10);
const vus = Number(__ENV.VUS || 20);
const page = Number(__ENV.POST_PAGE ?? 0);

if (!Number.isInteger(rps) || rps < 1) {
  throw new Error('RPS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(vus) || vus < 1) {
  throw new Error('VUS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(page) || page < 0 || page * 20 > 2147483647) {
  throw new Error('POST_PAGE는 조회 가능한 범위의 0 이상 정수여야 합니다.');
}

export const options = {
  userAgent: userAgent('board-list-load'),
  scenarios: {
    board_posts: {
      executor: 'constant-arrival-rate',
      rate: rps,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: vus,
      gracefulStop: '10s',
    },
  },

  thresholds: {
    ...sloThresholds(),
    ...validityThresholds,
  },

  summaryTrendStats,
};

export default function () {
  getBoardPosts(page);
}
