import { SharedArray } from 'k6/data';
import {
  validityThresholds,
  sloThresholds,
  summaryTrendStats,
  userAgent,
} from '../common/measurement.js';
import { firstPageIds, getProductDetail, validateIds } from './requests.js';

const rps = Number(__ENV.RPS || 10);
const vus = Number(__ENV.VUS || 20);
const idSource = __ENV.PRODUCT_IDS_SOURCE || 'first-page';

if (!Number.isInteger(rps) || rps < 1) {
  throw new Error('RPS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(vus) || vus < 1) {
  throw new Error('VUS는 1 이상의 정수여야 합니다.');
}

if (idSource !== 'first-page' && idSource !== 'file') {
  throw new Error('PRODUCT_IDS_SOURCE는 first-page 또는 file이어야 합니다.');
}

const fileIds = idSource === 'file'
  ? new SharedArray('product detail ids', () => {
      const ids = JSON.parse(open('../data/product-ids.json'));
      validateIds(ids);
      return ids;
    })
  : null;

export const options = {
  userAgent: userAgent('product-detail-load'),
  scenarios: {
    product_detail: {
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

export function setup() {
  if (idSource === 'file') {
    return null;
  }

  return firstPageIds();
}

export default function (setupIds) {
  const ids = fileIds || setupIds;
  const id = ids[Math.floor(Math.random() * ids.length)];
  getProductDetail(id);
}
