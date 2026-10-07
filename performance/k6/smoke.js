// performance/k6/smoke.js
import { getProducts, getProductDetail, getBoardPosts } from './requests.js';

export const options = {
  vus: 1,
  iterations: 1,

  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  const list = getProducts();
  let id;

  try {
    id = list.json()?.content?.[0]?.id;
  } catch {
    throw new Error('제품 목록 응답을 읽지 못했습니다.');
  }

  if (!Number.isInteger(id)) {
    throw new Error('목록에서 제품 ID를 찾지 못했습니다.');
  }

  getProductDetail(id);
  getBoardPosts();
}
