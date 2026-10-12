import { checkResponse, get } from '../common/http.js';

export function getBoardPosts(page = 0, size = 20) {
  const response = get(
    `/api/product-categories/LAPTOP/usage-purposes/BACKEND_DEVELOPMENT/posts?page=${page}&size=${size}`,
    { name: 'GET board posts' },
  );

  checkResponse(response, '게시글 목록', (body) => (
    Array.isArray(body?.content) &&
    body?.page === page &&
    body?.size === size &&
    typeof body?.hasNext === 'boolean'
  ));

  return response;
}
