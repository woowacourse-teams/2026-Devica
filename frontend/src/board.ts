import type { Served } from './selection';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
export const BOARD_PAGE_SIZE = 20;

export type BoardPostSummary = { id: number; title: string; createdAt: string; commentCount: number };
export type BoardPostDetail = Omit<BoardPostSummary, 'commentCount'> & {
  content: string;
  updatedAt: string;
  categoryCode: string;
  categoryName: string;
  purposeCode: string;
  purposeName: string;
};
export type BoardPostList = {
  content: BoardPostSummary[];
  page: number;
  size: number;
  hasNext: boolean;
};
export type BoardScope = { category: Served; purpose: Served | null };
export type BoardPostInput = { title: string; content: string };
export type BoardComment = { id: number; content: string; createdAt: string };

export class BoardRequestError extends Error {
  constructor(public readonly status: number) {
    super(`게시판 요청 실패 (${status})`);
  }
}

async function request(path: string, options: RequestInit = {}): Promise<Response> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  });
  if (!response.ok) {
    throw new BoardRequestError(response.status);
  }
  return response;
}

async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await request(path, options);
  return response.json();
}

function postsPath(category: string, purpose: string): string {
  return `/api/product-categories/${encodeURIComponent(category)}/usage-purposes/${encodeURIComponent(purpose)}/posts`;
}

export function fetchBoardCategories(signal: AbortSignal) {
  return requestJson<Served[]>('/api/product-categories', { signal });
}

export function fetchBoardPurposes(categoryCode: string, signal: AbortSignal) {
  return requestJson<Served[]>(`/api/product-categories/${encodeURIComponent(categoryCode)}/usage-purposes`, {
    signal,
  });
}

// 주소의 코드가 실제로 제공되는 선택지인지 확인하고 화면에 쓸 이름을 함께 받는다.
export async function fetchBoardScope(
  categoryCode: string,
  purposeCode: string | null,
  signal: AbortSignal,
): Promise<BoardScope> {
  const categories = await fetchBoardCategories(signal);
  const category = categories.find((item) => item.code === categoryCode);
  if (category === undefined) {
    throw new BoardRequestError(404);
  }
  if (purposeCode === null) {
    return { category, purpose: null };
  }
  const purposes = await fetchBoardPurposes(categoryCode, signal);
  const purpose = purposes.find((item) => item.code === purposeCode);
  if (purpose === undefined) {
    throw new BoardRequestError(404);
  }
  return { category, purpose };
}

export function fetchBoardPosts(
  category: string,
  purpose: string,
  page: number,
  signal: AbortSignal,
  size = BOARD_PAGE_SIZE,
) {
  return requestJson<BoardPostList>(`${postsPath(category, purpose)}?page=${page}&size=${size}`, { signal });
}

export function fetchBoardPost(id: number, signal: AbortSignal) {
  return requestJson<BoardPostDetail>(`/api/board-posts/${id}`, { signal });
}

export function createBoardPost(category: string, purpose: string, input: BoardPostInput, signal: AbortSignal) {
  return requestJson<{ id: number }>(postsPath(category, purpose), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
    signal,
  });
}

export function fetchBoardComments(postId: number, signal: AbortSignal) {
  return requestJson<BoardComment[]>(`/api/board-posts/${postId}/comments`, { signal });
}

export async function createBoardComment(postId: number, content: string, signal: AbortSignal): Promise<void> {
  // 등록 성공 뒤 목록을 다시 조회하므로, 비어 있을 수 있는 응답 본문을 파싱하지 않는다.
  await request(`/api/board-posts/${postId}/comments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ content }),
    signal,
  });
}

export function boardDate(value: string): string {
  // 백엔드의 LocalDateTime은 시간대 없이 내려오므로 표시할 때 임의의 시간대로 변환하지 않는다.
  const [date, time = ''] = value.split('T');
  return `${date.replaceAll('-', '. ')}. ${time.slice(0, 5)}`.trim();
}
