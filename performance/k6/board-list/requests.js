import http from 'k6/http';
import { Counter } from 'k6/metrics';
import { checkResponse, params, url } from '../common/http.js';

export const PAGE_SIZE = 20;
const succeeded = new Counter('board_requests_succeeded');

function isValidList(body, page) {
  return Array.isArray(body?.content) &&
    body.content.length > 0 && body.content.length <= PAGE_SIZE &&
    body.page === page && body.size === PAGE_SIZE &&
    typeof body.hasNext === 'boolean' &&
    body.content.every((post) => (
      Number.isSafeInteger(post?.id) && post.id > 0 &&
      typeof post.title === 'string' &&
      typeof post.createdAt === 'string' &&
      Number.isSafeInteger(post.commentCount) && post.commentCount >= 0
    ));
}

// 반복 한 번에 목록 요청 한 건만 보낸다. 재시도나 다음 페이지 요청은 하지 않는다.
export function getPosts(page, tags = {}) {
  const requestTags = { page: String(page), size: String(PAGE_SIZE), ...tags };
  const response = http.get(
    url(`/api/product-categories/LAPTOP/usage-purposes/BACKEND_DEVELOPMENT/posts?page=${page}&size=${PAGE_SIZE}`),
    params({ name: 'GET board posts', ...requestTags }),
  );

  let valid = false;
  checkResponse(response, '게시글 목록', (body) => {
    valid = isValidList(body, page);
    return valid;
  });
  // 200 응답 중 형식 검사까지 통과한 요청만 센다. 실패만 있어도 0을 요약에 남긴다.
  succeeded.add(valid ? 1 : 0, requestTags);
  return response;
}
