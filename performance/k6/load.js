// performance/k6/load.js
import { getProducts } from './requests.js';

const rps = Number(__ENV.RPS || 10);
const vus = Number(__ENV.VUS || 20);
const productPage = Number(__ENV.PRODUCT_PAGE ?? 0);

if (!Number.isInteger(rps) || rps < 1) {
  throw new Error('RPS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(vus) || vus < 1) {
  throw new Error('VUS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(productPage) || productPage < 0 || productPage > 2147483647) {
  throw new Error('PRODUCT_PAGE는 0 이상의 정수여야 합니다.');
}

export const options = {
  scenarios: {
    product_list: {
      executor: 'constant-arrival-rate',
      rate: rps,
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: vus,
      gracefulStop: '10s',
    },
  },

  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
  },

  summaryTrendStats: [
    'avg',
    'med',
    'p(95)',
    'p(99)',
    'max',
  ],
};

export default function () {
  getProducts(productPage);
}
