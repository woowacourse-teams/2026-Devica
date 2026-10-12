import http from 'k6/http';
import { checkResponse, params, url } from '../common/http.js';

const NAME = 'GET products';

function withPage(query) {
  return { page: 0, size: 20, ...query };
}

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

// query: page, size(기본 0, 20)와 os, cpuTier, memoryGb, storageGb, sort
export function getProducts(query = {}, tags = {}) {
  const fullQuery = withPage(query);
  const response = http.get(productsUrl(fullQuery), params({ name: NAME, ...tags }));

  checkProducts(response, fullQuery);
  return response;
}

// requests: [{ query, tags }]. 화면 하나가 여러 목록을 동시에 요청할 때 쓴다
export function getProductsAtOnce(requests) {
  const fullQueries = requests.map(({ query }) => withPage(query));
  const responses = http.batch(requests.map(({ tags }, i) => [
    'GET',
    productsUrl(fullQueries[i]),
    null,
    params({ name: NAME, ...tags }),
  ]));

  responses.forEach((response, i) => checkProducts(response, fullQueries[i]));
  return responses;
}
