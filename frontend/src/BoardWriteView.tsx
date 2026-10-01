import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'wouter';
import { BoardScopeInfo } from './BoardShared';
import { BoardRequestError, type BoardScope, createBoardPost } from './board';
import { boardUrl, replace } from './routes';
import type { Served } from './selection';

export function BoardWriteView({ scope }: { scope: BoardScope & { purpose: Served } }) {
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pending = useRef<AbortController | null>(null);
  const listUrl = boardUrl(scope.category.code, scope.purpose.code);

  useEffect(() => () => pending.current?.abort(), []);

  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (pending.current !== null) {
      return;
    }
    if (title.trim() === '' || content.trim() === '') {
      setError('제목과 본문은 공백만 입력할 수 없습니다.');
      return;
    }
    const controller = new AbortController();
    pending.current = controller;
    setSubmitting(true);
    setError(null);
    try {
      const post = await createBoardPost(
        scope.category.code,
        scope.purpose.code,
        { title, content },
        controller.signal,
      );
      if (!controller.signal.aborted) {
        // 뒤로 가기로 돌아왔을 때 이미 등록한 글을 다시 제출하지 않도록 작성 화면을 상세로 대체한다.
        replace(`/board/posts/${post.id}`);
      }
    } catch (caught: unknown) {
      if (!controller.signal.aborted) {
        setError(
          caught instanceof BoardRequestError && caught.status === 404
            ? '선택한 게시판을 찾을 수 없습니다. 입력한 내용을 복사한 뒤 게시판을 다시 선택해 주세요.'
            : '등록하지 못했습니다. 입력한 내용은 유지됩니다. 목록에서 등록 여부를 확인한 뒤 다시 시도해 주세요.',
        );
      }
    } finally {
      if (!controller.signal.aborted) {
        pending.current = null;
        setSubmitting(false);
      }
    }
  };

  return (
    <section className="view-panel board-view" aria-labelledby="board-write-title">
      <Link className="text-button view-back-button" href={listUrl}>
        ← 목록으로
      </Link>
      <h1 className="board-title" id="board-write-title">
        질문 작성
      </h1>
      <p className="section-description">로그인 없이 익명으로 작성하며, 등록한 글은 바로 공개됩니다.</p>
      <BoardScopeInfo scope={scope} />
      <form className="board-form" onSubmit={submit} aria-busy={submitting}>
        <div className="board-field">
          <label htmlFor="board-post-title">제목</label>
          <input
            id="board-post-title"
            name="title"
            type="text"
            required
            maxLength={255}
            value={title}
            disabled={submitting}
            aria-describedby="board-title-count"
            placeholder="궁금한 점을 한 문장으로 적어 주세요."
            onChange={(event) => setTitle(event.target.value)}
          />
          <span className="board-count" id="board-title-count">
            {title.length} / 255자
          </span>
        </div>
        <div className="board-field">
          <label htmlFor="board-post-content">본문</label>
          <textarea
            id="board-post-content"
            name="content"
            required
            maxLength={10000}
            rows={12}
            value={content}
            disabled={submitting}
            aria-describedby="board-content-count"
            placeholder="사용 환경과 궁금한 내용을 자세히 적어 주세요."
            onChange={(event) => setContent(event.target.value)}
          />
          <span className="board-count" id="board-content-count">
            {content.length.toLocaleString('ko-KR')} / 10,000자
          </span>
        </div>
        {error !== null && (
          <p className="board-error" role="alert">
            {error}
          </p>
        )}
        <div className="board-form__actions">
          <Link className="text-button" href={listUrl}>
            목록으로
          </Link>
          <button className="button button--primary" type="submit" disabled={submitting}>
            {submitting ? '등록 중…' : '질문 등록'}
          </button>
        </div>
      </form>
    </section>
  );
}
