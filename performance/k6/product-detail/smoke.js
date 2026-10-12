// performance/k6/product-detail/smoke.js
import { summaryTrendStats, userAgent, validityThresholds } from '../common/options.js';
import { firstPageIds, getProductDetail } from './requests.js';

export const options = {
  userAgent: userAgent('product-detail-smoke'),
  vus: 1,
  iterations: 1,
  // 스모크는 스크립트와 서버가 정상인지만 본다. 워밍업 없이 요청이 적어 SLO 판정에 쓰지 않는다
  thresholds: validityThresholds,
  summaryTrendStats,
};

export default function () {
  getProductDetail(firstPageIds()[0]);
}
