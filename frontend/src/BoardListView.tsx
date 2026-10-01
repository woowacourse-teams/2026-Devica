import { Link } from 'wouter';
import { BoardScopeInfo } from './BoardShared';
import { type BoardScope, boardDate, fetchBoardPosts } from './board';
import { boardUrl, PATHS, query } from './routes';
import type { Served } from './selection';
import { useBoardRequest } from './useBoardRequest';

export function BoardListView({ scope, page }: { scope: BoardScope & { purpose: Served }; page: number }) {
  const { category, purpose } = scope;
  const { state, retry } = useBoardRequest(JSON.stringify([category.code, purpose.code, page]), (signal) =>
    fetchBoardPosts(category.code, purpose.code, page, signal),
  );
  const writingUrl = `${PATHS.boardWrite}${query([
    ['category', category.code],
    ['purpose', purpose.code],
  ])}`;
  return (
    <section className="view-panel board-view" aria-labelledby="board-title">
      <h1 className="board-title" id="board-title">
        질문 게시판
      </h1>
      <p className="section-description">제품과 사용 목적에 대한 궁금한 점을 자유롭게 남겨 주세요.</p>
      <BoardScopeInfo scope={scope} editable />
      <div className="board-toolbar">
        <span>최신순</span>
        <Link className="button button--primary board-button" href={writingUrl}>
          글쓰기
        </Link>
      </div>
      {state.status === 'loading' && (
        <p className="board-status" role="status">
          게시글을 불러오는 중입니다…
        </p>
      )}
      {state.status === 'failed' && (
        <div className="board-status">
          <p role="alert">
            {state.notFound
              ? '선택한 게시판을 찾을 수 없습니다. 제품과 사용 목적을 다시 선택해 주세요.'
              : '게시글을 불러오지 못했습니다.'}
          </p>
          {!state.notFound && (
            <button className="button" type="button" onClick={retry}>
              다시 시도
            </button>
          )}
          {state.notFound && (
            <Link className="text-button" href={PATHS.board}>
              게시판 다시 선택
            </Link>
          )}
        </div>
      )}
      {state.status === 'success' && (
        <>
          {state.data.content.length === 0 ? (
            <div className="board-status">
              <p>{page === 0 ? '아직 등록된 질문이 없습니다.' : '이 페이지에는 게시글이 없습니다.'}</p>
              {page === 0 ? (
                <p className="section-description">첫 번째 질문을 남겨 보세요.</p>
              ) : (
                <Link className="text-button" href={boardUrl(category.code, purpose.code)}>
                  첫 페이지로
                </Link>
              )}
            </div>
          ) : (
            <ul className="board-list">
              {state.data.content.map((post) => (
                <li key={post.id}>
                  <Link className="board-list__link" href={`/board/posts/${post.id}`}>
                    <div className="board-list__text">
                      <span className="board-list__title">{post.title}</span>
                      <span className="board-meta">
                        익명 · <time dateTime={post.createdAt}>{boardDate(post.createdAt)}</time>
                      </span>
                    </div>
                    <span className="board-list__arrow" aria-hidden="true">
                      →
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <nav className="board-pagination" aria-label="게시글 페이지">
            {page > 0 ? (
              <Link href={boardUrl(category.code, purpose.code, page - 1)} aria-label="이전 페이지">
                ← 이전
              </Link>
            ) : (
              <span aria-disabled="true">← 이전</span>
            )}
            <span aria-current="page">{page + 1} 페이지</span>
            {state.data.hasNext ? (
              <Link href={boardUrl(category.code, purpose.code, page + 1)} aria-label="다음 페이지">
                다음 →
              </Link>
            ) : (
              <span aria-disabled="true">다음 →</span>
            )}
          </nav>
        </>
      )}
    </section>
  );
}
