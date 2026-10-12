import { checkResponse, get, jsonBody } from '../common/http.js';
import { getProducts } from '../product-list/requests.js';

export function getProductDetail(id) {
  const response = get(`/api/products/${id}`, { name: 'GET product detail' });

  checkResponse(response, '제품 상세', (body) => body?.id === id && Array.isArray(body?.offers));

  return response;
}

export function validateIds(ids) {
  if (!Array.isArray(ids) || ids.length === 0 ||
      ids.some((id) => !Number.isSafeInteger(id) || id <= 0)) {
    throw new Error('상세 조회용 제품 ID 목록은 양의 정수로 채워진 배열이어야 합니다.');
  }
}

export function firstPageIds() {
  const ids = jsonBody(getProducts())?.content?.map((product) => product.id);

  if (ids === undefined) {
    throw new Error('제품 목록에서 상세 조회용 ID를 읽지 못했습니다.');
  }

  validateIds(ids);
  return ids;
}
