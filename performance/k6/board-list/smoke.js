import { summaryTrendStats, userAgent, validityThresholds } from '../common/options.js';
import { getBoardPosts } from './requests.js';

export const options = {
  userAgent: userAgent('board-list-smoke'),
  vus: 1,
  iterations: 1,
  thresholds: validityThresholds,
  summaryTrendStats,
};

export default function () {
  getBoardPosts();
}
