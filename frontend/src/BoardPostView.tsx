import { type MouseEvent, useEffect } from 'react';
import { Link } from 'wouter';
import { track } from './analytics';
import { BoardComments } from './BoardComments';
import { BoardByline, BoardLoading, BoardMessage, BoardScopeInfo } from './BoardShared';
import { fetchBoardPost } from './board';
import { boardUrl, currentNavState } from './routes';
import { useBoardRequest } from './useBoardRequest';

export function BoardPostPage({ id }: { id: string }) {
  const number = Number(id);
  if (!/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(number)) {
    return <BoardMessage title="게시글 주소를 확인해 주세요" message="게시판에서 질문을 다시 선택해 주세요." />;
  }
  return <BoardPostView id={number} />;
}

function BoardPostView({ id }: { id: number }) {
  const { state, retry } = useBoardRequest(String(id), (signal) => fetchBoardPost(id, signal));
  const title = state.status === 'success' ? state.data.title : null;
  useEffect(() => {
    document.title = title === null ? '질문 상세 — Devica' : `${title} — Devica`;
  }, [title]);
  // 글을 받아 화면에 보였을 때만 센다. 받지 못했으면 세지 않는다.
  const loaded = state.status === 'success';
  useEffect(() => {
    if (loaded) {
      track('post_viewed', { post_id: String(id) });
    }
  }, [loaded, id]);
  if (state.status === 'loading') {
    return <BoardLoading message="질문을 불러오는 중입니다…" />;
  }
  if (state.status === 'failed') {
    return (
      <BoardMessage
        title={state.notFound ? '게시글을 찾을 수 없습니다' : '질문을 불러오지 못했습니다'}
        message={state.notFound ? '게시판에서 다른 질문을 선택해 주세요.' : '잠시 뒤에 다시 시도해 주세요.'}
        onRetry={state.notFound ? undefined : retry}
      />
    );
  }
  const post = state.data;
  const listUrl = boardUrl(post.categoryCode, post.purposeCode);
  const backToList = (event: MouseEvent<HTMLAnchorElement>) => {
    if (currentNavState().from === 'board') {
      event.preventDefault();
      window.history.back();
    }
  };
  return (
    <section className="view-panel flow-view board-view" aria-labelledby="board-post-title">
      <Link className="text-button view-back-button" href={listUrl} onClick={backToList}>
        ← 목록으로
      </Link>
      <BoardScopeInfo
        scope={{
          category: { code: post.categoryCode, name: post.categoryName },
          purpose: { code: post.purposeCode, name: post.purposeName },
        }}
      />
      <article className="board-post">
        <h1 className="board-post__title" id="board-post-title">
          {post.title}
        </h1>
        <BoardByline createdAt={post.createdAt} />
        <div className="board-post__content">{post.content}</div>
      </article>
      <BoardComments key={post.id} postId={post.id} />
      <Link className="button board-return" href={listUrl} onClick={backToList}>
        목록으로
      </Link>
    </section>
  );
}
