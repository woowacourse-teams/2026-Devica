import { SharedArray } from 'k6/data';
import { getBoardPosts, getProductDetail, getProducts } from './requests.js';

const productListRps = Number(__ENV.PRODUCT_LIST_RPS ?? 1);
const productDetailRps = Number(__ENV.PRODUCT_DETAIL_RPS ?? 1);
const boardListRps = Number(__ENV.BOARD_LIST_RPS ?? 1);
const vus = Number(__ENV.VUS ?? 20);
const productPage = Number(__ENV.PRODUCT_PAGE ?? 0);
const postPage = Number(__ENV.POST_PAGE ?? 0);
const idSource = __ENV.PRODUCT_IDS_SOURCE || 'first-page';

for (const [name, rate] of [
  ['PRODUCT_LIST_RPS', productListRps],
  ['PRODUCT_DETAIL_RPS', productDetailRps],
  ['BOARD_LIST_RPS', boardListRps],
]) {
  if (!Number.isInteger(rate) || rate < 0) {
    throw new Error(`${name}는 0 이상의 정수여야 합니다.`);
  }
}

if (productListRps + productDetailRps + boardListRps === 0) {
  throw new Error('적어도 한 종류의 요청률은 1 이상이어야 합니다.');
}

if (!Number.isInteger(vus) || vus < 1) {
  throw new Error('VUS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(productPage) || productPage < 0 || productPage > 2147483647) {
  throw new Error('PRODUCT_PAGE는 0 이상의 정수여야 합니다.');
}

if (!Number.isInteger(postPage) || postPage < 0 || postPage * 20 > 2147483647) {
  throw new Error('POST_PAGE는 조회 가능한 범위의 0 이상 정수여야 합니다.');
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

const fileIds = productDetailRps > 0 && idSource === 'file'
  ? new SharedArray('mixed load product ids', () => {
      const ids = JSON.parse(open('./data/product-ids.json'));
      validateIds(ids);
      return ids;
    })
  : null;

const duration = __ENV.DURATION || '30s';

function scenario(rate, exec) {
  return {
    executor: 'constant-arrival-rate',
    rate,
    timeUnit: '1s',
    duration,
    preAllocatedVUs: vus,
    gracefulStop: '10s',
    exec,
  };
}

const scenarios = {};

if (productListRps > 0) {
  scenarios.product_list = scenario(productListRps, 'productList');
}

if (productDetailRps > 0) {
  scenarios.product_detail = scenario(productDetailRps, 'productDetail');
}

if (boardListRps > 0) {
  scenarios.board_posts = scenario(boardListRps, 'boardPosts');
}

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  if (productDetailRps === 0 || idSource === 'file') {
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

export function productList() {
  getProducts(productPage);
}

export function productDetail(setupIds) {
  const ids = fileIds || setupIds;
  const id = ids[Math.floor(Math.random() * ids.length)];
  getProductDetail(id);
}

export function boardPosts() {
  getBoardPosts(postPage);
}
