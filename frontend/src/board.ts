import type { Served } from './selection';

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
export const BOARD_PAGE_SIZE = 20;

export type BoardPostSummary = { id: number; title: string; createdAt: string };
export type BoardPostDetail = BoardPostSummary & {
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

export class BoardRequestError extends Error {
  constructor(public readonly status: number) {
    super(`게시판 요청 실패 (${status})`);
  }
}

async function requestJson<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: { Accept: 'application/json', ...options.headers },
  });
  if (!response.ok) {
    throw new BoardRequestError(response.status);
  }
  return response.json();
}

function postsPath(category: string, purpose: string): string {
  return `/api/product-categories/${encodeURIComponent(category)}/usage-purposes/${encodeURIComponent(purpose)}/posts`;
}

// 주소의 코드가 실제로 제공되는 선택지인지 확인하고 화면에 쓸 이름을 함께 받는다.
export async function fetchBoardScope(
  categoryCode: string,
  purposeCode: string | null,
  signal: AbortSignal,
): Promise<BoardScope> {
  const categories = await requestJson<Served[]>('/api/product-categories', { signal });
  const category = categories.find((item) => item.code === categoryCode);
  if (category === undefined) {
    throw new BoardRequestError(404);
  }
  if (purposeCode === null) {
    return { category, purpose: null };
  }
  const purposes = await requestJson<Served[]>(
    `/api/product-categories/${encodeURIComponent(categoryCode)}/usage-purposes`,
    { signal },
  );
  const purpose = purposes.find((item) => item.code === purposeCode);
  if (purpose === undefined) {
    throw new BoardRequestError(404);
  }
  return { category, purpose };
}

export function fetchBoardPosts(category: string, purpose: string, page: number, signal: AbortSignal) {
  return requestJson<BoardPostList>(`${postsPath(category, purpose)}?page=${page}&size=${BOARD_PAGE_SIZE}`, { signal });
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

export function boardDate(value: string): string {
  // 백엔드의 LocalDateTime은 시간대 없이 내려오므로 표시할 때 임의의 시간대로 변환하지 않는다.
  const [date, time = ''] = value.split('T');
  return `${date.replaceAll('-', '. ')}. ${time.slice(0, 5)}`.trim();
}
