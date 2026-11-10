import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { BoardListView } from '../src/BoardListView';
import { BoardPostPage } from '../src/BoardPostView';
import { BoardWriteView } from '../src/BoardWriteView';
import { BOARD_POSTS_CACHE, type BoardPostSummary } from '../src/board';
import { updatePagedStartingWith } from '../src/fetched';

const SCOPE = {
  category: { code: 'LAPTOP', name: '노트북' },
  purpose: { code: 'BACKEND_DEVELOPMENT', name: '백엔드 개발' },
};

const LIST_URL = '/board?category=LAPTOP&purpose=BACKEND_DEVELOPMENT';
let pages = 1;

const fetchMock = vi.fn(async (url: string) => {
  const { pathname, searchParams } = new URL(url, 'https://devica.test');
  const page = Number(searchParams.get('page'));
  const body = pathname.endsWith('/comments')
    ? []
    : pathname.startsWith('/api/board-posts/')
      ? {
          id: 1,
          title: '글 1',
          content: '본문',
          createdAt: '2026-11-10T00:00:00',
          updatedAt: '2026-11-10T00:00:00',
          categoryCode: SCOPE.category.code,
          categoryName: SCOPE.category.name,
          purposeCode: SCOPE.purpose.code,
          purposeName: SCOPE.purpose.name,
        }
      : {
          content:
            page < pages
              ? [{ id: page + 1, title: `글 ${page + 1}`, createdAt: '2026-11-10T00:00:00', commentCount: 0 }]
              : [],
          hasNext: page + 1 < pages,
        };
  return { ok: true, json: async () => body };
});

function desktop() {
  vi.stubGlobal('matchMedia', () => ({ matches: true, addEventListener() {}, removeEventListener() {} }));
}

beforeEach(() => {
  pages = 1;
  fetchMock.mockClear();
  vi.stubGlobal('fetch', fetchMock);
  vi.stubGlobal('matchMedia', () => ({ matches: false, addEventListener() {}, removeEventListener() {} }));
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      disconnect() {}
    },
  );
  window.history.replaceState(null, '', LIST_URL);
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

test('글을 열 때 방문 표식을 적고, 댓글을 달고 돌아오면 다시 받지 않고 댓글 수와 위치를 되살린다', async () => {
  const first = render(<BoardListView scope={SCOPE} page={0} />);
  await screen.findByText('글 1');
  const visit = window.history.state.boardVisit;
  expect(visit).toBeTypeOf('number');

  window.history.pushState(null, '', window.location.href);
  const replaced = vi.spyOn(window.history, 'replaceState');
  fireEvent.click(screen.getByText('글 1'));
  expect(replaced.mock.calls[0][0]).toMatchObject({ boardVisit: visit, scrollY: 0 });
  first.unmount();

  updatePagedStartingWith<BoardPostSummary>(BOARD_POSTS_CACHE, (post) =>
    post.id === 1 ? { ...post, commentCount: post.commentCount + 1 } : post,
  );
  window.history.replaceState(
    { boardVisit: visit, scrollY: 300 },
    '',
    '/board?category=LAPTOP&purpose=BACKEND_DEVELOPMENT',
  );
  const scrolled = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  await act(async () => {
    render(<BoardListView scope={SCOPE} page={0} />);
  });

  expect(screen.getByText('댓글 1')).toBeTruthy();
  expect(fetchMock).toHaveBeenCalledTimes(1);
  expect(scrolled).toHaveBeenCalledWith(0, 300);
});

test('데스크톱 목록은 글을 받은 뒤 보던 위치로 되돌린다', async () => {
  desktop();
  window.history.replaceState({ scrollY: 500 }, '', LIST_URL);
  const scrolled = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  render(<BoardListView scope={SCOPE} page={0} />);
  await screen.findByText('글 1');
  expect(scrolled).toHaveBeenCalledWith(0, 500);
});

test('데스크톱 목록은 쪽을 오가도 돌아온 쪽마다 보던 위치로 되돌린다', async () => {
  desktop();
  pages = 2;
  const scrolled = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  window.history.replaceState({ scrollY: 300 }, '', `${LIST_URL}&page=1`);
  const view = render(<BoardListView scope={SCOPE} page={1} />);
  await screen.findByText('글 2');
  vi.spyOn(window, 'scrollY', 'get').mockReturnValue(700);
  const replaced = vi.spyOn(window.history, 'replaceState');
  fireEvent.click(screen.getByRole('link', { name: '이전 페이지' }));
  expect(replaced.mock.calls[0][0]).toMatchObject({ scrollY: 700 });
  view.rerender(<BoardListView scope={SCOPE} page={0} />);
  await screen.findByText('글 1');
  window.history.replaceState({ scrollY: 700 }, '', `${LIST_URL}&page=1`);
  view.rerender(<BoardListView scope={SCOPE} page={1} />);
  await screen.findByText('글 2');
  expect(scrolled).toHaveBeenCalledWith(0, 700);
});

test('글이 없는 페이지에는 페이지 이동을 표시하지 않는다', async () => {
  desktop();
  render(<BoardListView scope={SCOPE} page={5} />);
  await screen.findByText('이 페이지에는 게시글이 없습니다.');
  expect(screen.queryByRole('navigation', { name: '게시글 페이지' })).toBeNull();
});

test('모바일 목록은 보던 위치까지 내려갈 수 있을 때까지 이어 받는다', async () => {
  pages = 5;
  vi.spyOn(document.documentElement, 'scrollHeight', 'get').mockImplementation(
    () => document.querySelectorAll('.board-list li').length * 1000,
  );
  const scrolled = vi.spyOn(window, 'scrollTo').mockImplementation(() => {});
  window.history.replaceState({ boardVisit: 4242, scrollY: 2000 }, '', LIST_URL);
  render(<BoardListView scope={SCOPE} page={0} />);
  await screen.findByText('글 3');
  await act(async () => {});
  expect(fetchMock).toHaveBeenCalledTimes(3);
  expect(scrolled).toHaveBeenLastCalledWith(0, 2000);
});

test('목록에서 연 글의 목록으로는 기록을 되돌리고, 바로 연 글은 게시판 첫 페이지를 연다', async () => {
  const back = vi.spyOn(window.history, 'back').mockImplementation(() => {});
  window.history.replaceState({ from: 'board' }, '', '/board/posts/1');
  const opened = render(<BoardPostPage id="1" />);
  fireEvent.click(await screen.findByRole('link', { name: '← 목록으로' }));
  expect(back).toHaveBeenCalledTimes(1);
  opened.unmount();

  window.history.replaceState(null, '', '/board/posts/1');
  const length = window.history.length;
  render(<BoardPostPage id="1" />);
  fireEvent.click(await screen.findByRole('link', { name: '목록으로' }));
  expect(back).toHaveBeenCalledTimes(1);
  expect(window.location.pathname + window.location.search).toBe(LIST_URL);
  expect(window.history.length).toBe(length + 1);
});

test('등록한 글의 목록으로는 기록을 되돌리지 않고 첫 페이지 목록을 새로 연다', async () => {
  const back = vi.spyOn(window.history, 'back').mockImplementation(() => {});
  window.history.replaceState({ from: 'boardWrite' }, '', '/board/posts/1');
  const length = window.history.length;
  render(<BoardPostPage id="1" />);
  fireEvent.click(await screen.findByRole('link', { name: '목록으로' }));
  expect(back).not.toHaveBeenCalled();
  expect(window.location.pathname + window.location.search).toBe(LIST_URL);
  expect(window.history.length).toBe(length + 1);
});

test('목록에서 연 작성 화면의 두 목록으로 버튼은 기록을 되돌린다', () => {
  const back = vi.spyOn(window.history, 'back').mockImplementation(() => {});
  window.history.replaceState({ from: 'board' }, '', '/board/new?category=LAPTOP&purpose=BACKEND_DEVELOPMENT');
  render(<BoardWriteView scope={SCOPE} />);
  fireEvent.click(screen.getByRole('link', { name: '← 목록으로' }));
  fireEvent.click(screen.getByRole('link', { name: '목록으로' }));
  expect(back).toHaveBeenCalledTimes(2);
});
