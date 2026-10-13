// performance/k6/requests.js
import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080')
  .replace(/\/+$/, '');

export function getProducts(page = 0, size = 20) {
  const response = http.get(
    `${baseUrl}/api/product-categories/LAPTOP/products?page=${page}&size=${size}`,
    {
      tags: {
        name: 'GET products',
      },
      timeout: '5s',
      redirects: 0,
      responseCallback: http.expectedStatuses(200),
    },
  );

  check(response, {
    'HTTP 상태가 200이다': (res) => res.status === 200,

    '제품 목록 응답 형식이 올바르다': (res) => {
      try {
        const body = res.json();

        return (
          Array.isArray(body?.content) &&
          body?.page === page &&
          body?.size === size &&
          typeof body?.hasNext === 'boolean'
        );
      } catch {
        return false;
      }
    },
  });

  return response;
}

export function getProductDetail(id) {
  const response = http.get(`${baseUrl}/api/products/${id}`, {
    tags: { name: 'GET product detail' },
    timeout: '5s',
    redirects: 0,
    responseCallback: http.expectedStatuses(200),
  });

  check(response, {
    '제품 상세 HTTP 상태가 200이다': (res) => res.status === 200,
    '제품 상세 응답 형식이 올바르다': (res) => {
      try {
        const body = res.json();
        return body?.id === id && Array.isArray(body?.offers);
      } catch {
        return false;
      }
    },
  });

  return response;
}

export function getBoardPosts(page = 0, size = 20) {
  const response = http.get(
    `${baseUrl}/api/product-categories/LAPTOP/usage-purposes/BACKEND_DEVELOPMENT/posts?page=${page}&size=${size}`,
    {
      tags: { name: 'GET board posts' },
      timeout: '5s',
      redirects: 0,
      responseCallback: http.expectedStatuses(200),
    },
  );

  check(response, {
    '게시글 목록 HTTP 상태가 200이다': (res) => res.status === 200,
    '게시글 목록 응답 형식이 올바르다': (res) => {
      try {
        const body = res.json();
        return (
          Array.isArray(body?.content) &&
          body?.page === page &&
          body?.size === size &&
          typeof body?.hasNext === 'boolean'
        );
      } catch {
        return false;
      }
    },
  });

  return response;
}
