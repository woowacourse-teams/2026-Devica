import { type FormEvent, useEffect, useRef, useState } from 'react';
import { Link } from 'wouter';
import { track } from './analytics';
import { BoardScopeInfo } from './BoardShared';
import { BOARD_POSTS_CACHE, BoardRequestError, type BoardScope, createBoardPost } from './board';
import { forgetStartingWith } from './fetched';
import { boardUrl, goBack, navigateInside, replace } from './routes';
import type { Served } from './selection';

const BLANK_ERROR = '제목과 본문은 공백만 입력할 수 없습니다.';

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
      setError(BLANK_ERROR);
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
      // 저장은 됐으므로 화면을 떠났더라도 센다.
      track('question_submitted', {});
      forgetStartingWith(BOARD_POSTS_CACHE);
      if (!controller.signal.aborted) {
        // 뒤로 가기로 돌아왔을 때 이미 등록한 글을 다시 제출하지 않도록 작성 화면을 상세로 대체한다.
        replace(`/board/posts/${post.id}`, { from: 'boardWrite' });
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

  const blank = error === BLANK_ERROR;

  return (
    <section className="view-panel flow-view board-view" aria-labelledby="board-write-title">
      <Link
        className="text-button view-back-button"
        href={listUrl}
        onClick={(event) => navigateInside(event, () => goBack(listUrl, 'board'))}
      >
        ← 목록으로
      </Link>
      <div className="flow-head">
        <h1 id="board-write-title">질문 작성</h1>
        <p className="flow-head__description">로그인 없이 익명으로 작성하며, 등록한 글은 바로 공개됩니다.</p>
      </div>
      <form className="board-form" onSubmit={submit} aria-busy={submitting}>
        <div className="board-field">
          <span className="board-field__label">게시판</span>
          <BoardScopeInfo scope={scope} />
        </div>
        <div className="board-field">
          <div className="board-field__head">
            <label htmlFor="board-post-title">
              제목 <span className="board-field__required">(필수)</span>
            </label>
            <span className="board-count" id="board-title-count">
              {title.length} / 255자
            </span>
          </div>
          <input
            id="board-post-title"
            name="title"
            type="text"
            required
            maxLength={255}
            value={title}
            disabled={submitting}
            aria-invalid={blank && title.trim() === ''}
            aria-describedby="board-title-count"
            placeholder="궁금한 점을 한 문장으로 적어 주세요."
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>
        <div className="board-field">
          <div className="board-field__head">
            <label htmlFor="board-post-content">
              본문 <span className="board-field__required">(필수)</span>
            </label>
            <span className="board-count" id="board-content-count">
              {content.length.toLocaleString('ko-KR')} / 10,000자
            </span>
          </div>
          <textarea
            id="board-post-content"
            name="content"
            required
            maxLength={10000}
            rows={10}
            value={content}
            disabled={submitting}
            aria-invalid={blank && content.trim() === ''}
            aria-describedby="board-content-count"
            placeholder="사용 환경과 궁금한 내용을 자세히 적어 주세요."
            onChange={(event) => setContent(event.target.value)}
          />
        </div>
        {error !== null && (
          <p className={blank ? 'board-error' : 'board-alert'} role="alert">
            {error}
          </p>
        )}
        <div className="board-form__actions sticky-actions">
          <Link
            className="button board-form__back"
            href={listUrl}
            onClick={(event) => navigateInside(event, () => goBack(listUrl, 'board'))}
          >
            목록으로
          </Link>
          <button className="button button--primary" type="submit" disabled={submitting}>
            {submitting ? '등록 중…' : '질문 등록'}
            {!submitting && <span aria-hidden="true">→</span>}
          </button>
        </div>
      </form>
    </section>
  );
}
