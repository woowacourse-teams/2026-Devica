import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { BoardListView } from '../src/BoardListView';
import { BOARD_POSTS_CACHE, type BoardPostSummary } from '../src/board';
import { updatePagedStartingWith } from '../src/fetched';

const SCOPE = {
  category: { code: 'LAPTOP', name: '노트북' },
  purpose: { code: 'BACKEND_DEVELOPMENT', name: '백엔드 개발' },
};

const fetchMock = vi.fn(async (url: string) => {
  const page = Number(new URL(url, 'https://devica.test').searchParams.get('page'));
  return {
    ok: true,
    json: async () => ({
      content: [{ id: page + 1, title: `글 ${page + 1}`, createdAt: '2026-11-10T00:00:00', commentCount: 0 }],
      hasNext: false,
    }),
  };
});

beforeEach(() => {
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
  window.history.replaceState(null, '', '/board?category=LAPTOP&purpose=BACKEND_DEVELOPMENT');
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
