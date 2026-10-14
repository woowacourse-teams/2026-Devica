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

// query는 page, size, sort와 필요한 필터(os, cpuTier, memoryGb, storageGb)
export function getProducts(query, tags) {
  const response = http.get(productsUrl(query), params({ name: NAME, ...tags }));

  checkProducts(response, query);
  return response;
}
