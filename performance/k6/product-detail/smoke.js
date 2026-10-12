// performance/k6/product-detail/smoke.js
import {
  sloThresholds,
  summaryTrendStats,
  userAgent,
  validityThresholds,
} from '../common/options.js';
import { firstPageIds, getProductDetail } from './requests.js';

export const options = {
  userAgent: userAgent('product-detail-smoke'),
  vus: 1,
  iterations: 1,
  thresholds: {
    ...sloThresholds(),
    ...validityThresholds,
  },
  summaryTrendStats,
};

export default function () {
  getProductDetail(firstPageIds()[0]);
}
