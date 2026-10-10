import { getProducts, getProductDetail } from './requests.js';
import { SharedArray } from 'k6/data';

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

function validateIds(ids) {
  if (!Array.isArray(ids) || ids.length === 0 ||
      ids.some((id) => !Number.isSafeInteger(id) || id <= 0)) {
    throw new Error('상세 조회용 제품 ID 목록은 양의 정수로 채워진 배열이어야 합니다.');
  }
}

const fileIds = idSource === 'file'
  ? new SharedArray('product detail ids', () => {
      const ids = JSON.parse(open('./data/product-ids.json'));
      validateIds(ids);
      return ids;
    })
  : null;

export const options = {
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
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
  },

  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (idSource === 'file') {
    return null;
  }

  const response = getProducts();
  let ids;

  try {
    ids = response.json().content.map((product) => product.id);
  } catch {
    throw new Error('제품 목록에서 상세 조회용 ID를 읽지 못했습니다.');
  }

  validateIds(ids);

  return ids;
}

export default function (setupIds) {
  const ids = fileIds || setupIds;
  const id = ids[Math.floor(Math.random() * ids.length)];
  getProductDetail(id);
}
