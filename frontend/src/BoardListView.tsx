import { type ReactNode, useEffect, useRef, useState } from 'react';
import { Link } from 'wouter';
import { track } from './analytics';
import { BoardByline } from './BoardShared';
import { BOARD_POSTS_CACHE, type BoardPostSummary, type BoardScope, fetchBoardPosts } from './board';
import { keepLatestStartingWith, usePaged } from './fetched';
import { boardUrl, currentNavState, PATHS, query } from './routes';
import type { Served } from './selection';
import { useBoardRequest } from './useBoardRequest';
import { useCompact } from './useCompact';

type ListScope = BoardScope & { purpose: Served };

const PREFETCH_MARGIN = '0px 0px 600px 0px';
const UNABORTED = new AbortController().signal;
const KEPT_VISITS = 10;

export function BoardListView({ scope, page }: { scope: ListScope; page: number }) {
  const { category, purpose } = scope;
  const compact = useCompact();
  // 글쓰기 버튼은 목록을 받았는지와 관계없이 늘 보인다. 게시판을 바꿨을 때만 다시 센다.
  // biome-ignore lint/correctness/useExhaustiveDependencies: 게시판이 바뀔 때마다 돌아야 한다. 쪽만 넘길 때는 다시 세지 않는다.
  useEffect(() => {
    track('question_cta_viewed', {});
  }, [category.code, purpose.code]);
  const writingUrl = `${PATHS.boardWrite}${query([
    ['category', category.code],
    ['purpose', purpose.code],
  ])}`;
  const toolbar = (
    <div className="board-toolbar">
      <p className="board-browse__scope">
        <b>
          {category.name} / {purpose.name}
        </b>
        <span className="board-meta">최신순</span>
      </p>
      <Link
        className="button button--primary board-write-button"
        href={writingUrl}
        state={{ from: 'board' }}
        onClick={() => rememberScroll()}
      >
        <svg
          aria-hidden="true"
          width="16"
          height="16"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.4"
          strokeLinecap="round"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
        <span className="board-write-button__label">글쓰기</span>
      </Link>
    </div>
  );
  return (
    <section className="board-browse__posts" aria-label={`${category.name} ${purpose.name} 질문 목록`}>
      {compact && page === 0 ? (
        <ScrollingPosts key={`${category.code}:${purpose.code}`} scope={scope} toolbar={toolbar} />
      ) : (
        <PagedPosts scope={scope} page={page} toolbar={toolbar} />
      )}
    </section>
  );
}

function PagedPosts({ scope, page, toolbar }: { scope: ListScope; page: number; toolbar: ReactNode }) {
  const { category, purpose } = scope;
  const { state, retry } = useBoardRequest(JSON.stringify([category.code, purpose.code, page]), (signal) =>
    fetchBoardPosts(category.code, purpose.code, page, signal),
  );
  useScrollRestoredAfterLoad(state.status === 'success');
  const empty = state.status === 'success' && state.data.content.length === 0;
  return (
    <>
      <div className="board-browse__card">
        {toolbar}
        {state.status === 'loading' && <LoadingPosts />}
        {state.status === 'failed' && <FailedPosts notFound={state.notFound} onRetry={retry} />}
        {state.status === 'success' &&
          (empty ? (
            page === 0 ? (
              <NoPosts />
            ) : (
              <div className="board-status">
                <p className="board-status__title">이 페이지에는 게시글이 없습니다.</p>
                <Link className="text-button" href={boardUrl(category.code, purpose.code)}>
                  첫 페이지로
                </Link>
              </div>
            )
          ) : (
            <PostList posts={state.data.content} />
          ))}
      </div>
      {state.status === 'success' && !empty && (
        <nav className="board-pagination" aria-label="게시글 페이지">
          {page > 0 ? (
            <Link className="button" href={boardUrl(category.code, purpose.code, page - 1)} aria-label="이전 페이지">
              ← 이전
            </Link>
          ) : (
            <span className="button" aria-disabled="true">
              ← 이전
            </span>
          )}
          <span className="board-pagination__current" aria-current="page">
            {page + 1} 페이지
          </span>
          {state.data.hasNext ? (
            <Link className="button" href={boardUrl(category.code, purpose.code, page + 1)} aria-label="다음 페이지">
              다음 →
            </Link>
          ) : (
            <span className="button" aria-disabled="true">
              다음 →
            </span>
          )}
        </nav>
      )}
    </>
  );
}

function ScrollingPosts({ scope, toolbar }: { scope: ListScope; toolbar: ReactNode }) {
  const { category, purpose } = scope;
  const [visit] = useState(() => currentNavState().boardVisit ?? Date.now());
  useEffect(() => {
    window.history.replaceState({ ...currentNavState(), boardVisit: visit }, '');
    keepLatestStartingWith(BOARD_POSTS_CACHE, KEPT_VISITS);
  }, [visit]);
  const posts = usePaged<BoardPostSummary>(`${BOARD_POSTS_CACHE}${category.code}:${purpose.code}:${visit}`, (page) =>
    fetchBoardPosts(category.code, purpose.code, page, UNABORTED),
  );
  const { data, hasNext, loading, failed, more } = posts;
  useScrollRestoredAfterLoad(data !== null);

  const [sentinel, setSentinel] = useState<HTMLDivElement | null>(null);
  useEffect(() => {
    if (sentinel === null || !hasNext || loading || failed) {
      return;
    }
    const observer = new IntersectionObserver((entries) => entries[0]?.isIntersecting && more(), {
      rootMargin: PREFETCH_MARGIN,
    });
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [sentinel, hasNext, loading, failed, more]);

  return (
    <div className="board-browse__card">
      {toolbar}
      {data === null ? (
        failed ? (
          <FailedPosts notFound={false} reselectable onRetry={more} />
        ) : (
          <LoadingPosts />
        )
      ) : data.length === 0 ? (
        <NoPosts />
      ) : (
        <>
          <PostList posts={data} visit={visit} />
          {hasNext && <div ref={setSentinel} />}
          {loading && (
            <p className="board-status board-meta" role="status">
              게시글을 더 불러오는 중입니다…
            </p>
          )}
          {failed && (
            <div className="board-status">
              <p className="board-meta" role="alert">
                게시글을 더 불러오지 못했습니다.
              </p>
              <button className="button" type="button" onClick={more}>
                다시 시도
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function useScrollRestoredAfterLoad(loaded: boolean) {
  const restored = useRef(false);
  useEffect(() => {
    if (!loaded || restored.current) {
      return;
    }
    restored.current = true;
    const { scrollY } = currentNavState();
    if (scrollY !== undefined) {
      window.scrollTo(0, scrollY);
    }
  }, [loaded]);
}

function LoadingPosts() {
  return (
    <p className="board-status" role="status">
      게시글을 불러오는 중입니다…
    </p>
  );
}

function reloadBoard() {
  window.location.assign(PATHS.board);
}

function FailedPosts({
  notFound,
  reselectable = notFound,
  onRetry,
}: {
  notFound: boolean;
  reselectable?: boolean;
  onRetry: () => void;
}) {
  return (
    <div className="board-status">
      <p role="alert">
        {notFound
          ? '선택한 게시판을 찾을 수 없습니다. 제품과 사용 목적을 다시 선택해 주세요.'
          : '게시글을 불러오지 못했습니다.'}
      </p>
      {!notFound && (
        <button className="button" type="button" onClick={onRetry}>
          다시 시도
        </button>
      )}
      {reselectable && (
        <button className="text-button" type="button" onClick={reloadBoard}>
          게시판 다시 선택
        </button>
      )}
    </div>
  );
}

function NoPosts() {
  return (
    <div className="board-status">
      <p className="board-status__title">아직 등록된 질문이 없습니다.</p>
      <p className="board-meta">첫 번째 질문을 남겨 보세요.</p>
    </div>
  );
}

function rememberScroll(visit?: number) {
  const state = currentNavState();
  window.history.replaceState({ ...state, scrollY: window.scrollY, boardVisit: visit ?? state.boardVisit }, '');
}

function PostList({ posts, visit }: { posts: BoardPostSummary[]; visit?: number }) {
  return (
    <ul className="board-list">
      {posts.map((post) => (
        <li key={post.id}>
          <Link
            className="board-list__link"
            href={`/board/posts/${post.id}`}
            state={{ from: 'board' }}
            onClick={() => rememberScroll(visit)}
          >
            <div className="board-list__text">
              <span className="board-list__title">{post.title}</span>
              <BoardByline createdAt={post.createdAt}>
                <span className={post.commentCount > 0 ? 'board-list__comments' : undefined}>
                  댓글 {post.commentCount}
                </span>
              </BoardByline>
            </div>
            <span className="board-list__arrow" aria-hidden="true">
              →
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
}
