import http from 'k6/http';
import { checkResponse, params, url } from '../common/http.js';

const NAME = 'GET products';

function productsUrl(query) {
  const queryString = Object.entries(query)
    .map(([key, value]) => `${key}=${encodeURIComponent(value)}`)
    .join('&');

  return url(`/api/product-categories/LAPTOP/products?${queryString}`);
}

function checkProducts(response, query) {
  checkResponse(response, '제품 목록', (body) => (
    Array.isArray(body?.content) &&
    body?.page === query.page &&
    body?.size === query.size &&
    typeof body?.hasNext === 'boolean'
  ));
}

// requests: [{ query, tags }]. query는 page, size, os, cpuTier, memoryGb, storageGb, sort.
// 화면 하나가 여러 목록을 동시에 요청할 때도 쓴다
export function getProductsAtOnce(requests) {
  const responses = http.batch(requests.map(({ query, tags }) => [
    'GET',
    productsUrl(query),
    null,
    params({ name: NAME, ...tags }),
  ]));

  responses.forEach((response, i) => checkProducts(response, requests[i].query));
  return responses;
}
