// performance/k6/board-list/smoke.js
import { summaryTrendStats, userAgent, validityThresholds } from '../common/options.js';
import { getBoardPosts } from './requests.js';

export const options = {
  userAgent: userAgent('board-list-smoke'),
  vus: 1,
  iterations: 1,
  // 스모크는 스크립트와 서버가 정상인지만 본다. 워밍업 없이 요청이 적어 SLO 판정에 쓰지 않는다
  thresholds: validityThresholds,
  summaryTrendStats,
};

export default function () {
  getBoardPosts();
}
