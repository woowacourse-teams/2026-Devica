import { type FormEvent, useEffect, useRef, useState } from 'react';
import { BoardRequestError, boardDate, createBoardComment, fetchBoardComments } from './board';
import { useBoardRequest } from './useBoardRequest';

const COMMENT_MAX_LENGTH = 1000;
type SubmitState = { status: 'idle' | 'submitting' | 'success' } | { status: 'failed'; message: string };

export function BoardComments({ postId }: { postId: number }) {
  const { state, retry } = useBoardRequest(String(postId), (signal) => fetchBoardComments(postId, signal));

  return (
    <section className="board-comments" aria-labelledby="board-comments-title">
      <h2 className="board-comments__title" id="board-comments-title">
        댓글{state.status === 'success' ? ` ${state.data.length}` : ''}
      </h2>
      {state.status === 'loading' && (
        <p className="board-status" role="status">
          댓글을 불러오는 중입니다…
        </p>
      )}
      {state.status === 'failed' && (
        <div className="board-status">
          <p role="alert">
            {state.notFound ? '게시글을 찾을 수 없어 댓글을 불러오지 못했습니다.' : '댓글을 불러오지 못했습니다.'}
          </p>
          <button className="button" type="button" onClick={retry}>
            댓글 다시 불러오기
          </button>
        </div>
      )}
      {state.status === 'success' &&
        (state.data.length === 0 ? (
          <p className="board-status">아직 댓글이 없습니다. 첫 번째 답변을 남겨 보세요.</p>
        ) : (
          <ol className="board-comments__list">
            {state.data.map((comment) => (
              <li key={comment.id}>
                <p className="board-meta">
                  익명 · <time dateTime={comment.createdAt}>{boardDate(comment.createdAt)}</time>
                </p>
                <p className="board-comments__content">{comment.content}</p>
              </li>
            ))}
          </ol>
        ))}
      <BoardCommentForm postId={postId} onCreated={retry} />
    </section>
  );
}

function BoardCommentForm({ postId, onCreated }: { postId: number; onCreated: () => void }) {
  const [content, setContent] = useState('');
  const [state, setState] = useState<SubmitState>({ status: 'idle' });
  const pending = useRef<AbortController | null>(null);
  const submitting = state.status === 'submitting';
  const valid = content.trim() !== '' && content.length <= COMMENT_MAX_LENGTH;

  useEffect(() => () => pending.current?.abort(), []);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending.current !== null || !valid) {
      return;
    }
    const controller = new AbortController();
    pending.current = controller;
    setState({ status: 'submitting' });
    try {
      await createBoardComment(postId, content, controller.signal);
      if (!controller.signal.aborted) {
        setContent('');
        setState({ status: 'success' });
        onCreated();
      }
    } catch (caught: unknown) {
      if (!controller.signal.aborted) {
        let message =
          '댓글을 등록하지 못했습니다. 입력한 내용은 유지됩니다. 댓글 목록에서 등록 여부를 확인한 뒤 다시 시도해 주세요.';
        if (caught instanceof BoardRequestError) {
          if (caught.status === 404) {
            message = '게시글을 찾을 수 없습니다. 입력한 내용을 복사한 뒤 게시글을 다시 확인해 주세요.';
          } else if (caught.status === 400) {
            message = '댓글은 공백을 제외한 내용을 포함해 1,000자 이내로 입력해 주세요.';
          }
        }
        setState({ status: 'failed', message });
      }
    } finally {
      if (!controller.signal.aborted) {
        pending.current = null;
      }
    }
  };

  return (
    <form className="board-form board-comment-form" onSubmit={submit} aria-busy={submitting}>
      <div className="board-field">
        <label htmlFor="board-comment-content">댓글 작성</label>
        <p className="board-meta" id="board-comment-help">
          로그인 없이 익명으로 작성하며, 등록한 댓글은 바로 공개됩니다.
        </p>
        <textarea
          id="board-comment-content"
          name="content"
          required
          maxLength={COMMENT_MAX_LENGTH}
          rows={4}
          value={content}
          disabled={submitting}
          aria-describedby="board-comment-help board-comment-count"
          placeholder="질문에 도움이 될 답변을 남겨 주세요."
          onChange={(event) => {
            setContent(event.target.value);
            setState({ status: 'idle' });
          }}
        />
        <span className="board-count" id="board-comment-count">
          {content.length.toLocaleString('ko-KR')} / 1,000자
        </span>
      </div>
      {state.status === 'failed' && (
        <p className="board-error" role="alert">
          {state.message}
        </p>
      )}
      {state.status === 'success' && <p role="status">댓글을 등록했습니다.</p>}
      <button className="button button--primary" type="submit" disabled={submitting || !valid}>
        {submitting ? '등록 중…' : '댓글 등록'}
      </button>
    </form>
  );
}
