import { summaryTrendStats, userAgent, validityThresholds } from '../common/measurement.js';
import { firstPageIds, getProductDetail } from './requests.js';

export const options = {
  userAgent: userAgent('product-detail-smoke'),
  vus: 1,
  iterations: 1,
  thresholds: validityThresholds,
  summaryTrendStats,
};

export default function () {
  getProductDetail(firstPageIds()[0]);
}
