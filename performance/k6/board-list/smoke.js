// performance/k6/board-list/smoke.js
import {
  sloThresholds,
  summaryTrendStats,
  userAgent,
  validityThresholds,
} from '../common/options.js';
import { getBoardPosts } from './requests.js';

export const options = {
  userAgent: userAgent('board-list-smoke'),
  vus: 1,
  iterations: 1,
  thresholds: {
    ...sloThresholds(),
    ...validityThresholds,
  },
  summaryTrendStats,
};

export default function () {
  getBoardPosts();
}
