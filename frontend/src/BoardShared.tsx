import { Link } from 'wouter';
import type { BoardScope } from './board';
import { PATHS } from './routes';
import './BoardView.css';

export function BoardMessage({
  title,
  message,
  onRetry,
  backTo = PATHS.board,
}: {
  title: string;
  message: string;
  onRetry?: () => void;
  backTo?: string;
}) {
  return (
    <section className="error-panel" aria-labelledby="board-message-title">
      <h1 id="board-message-title">{title}</h1>
      <p className="error-panel__message" role="alert">
        {message}
      </p>
      <div className="error-panel__actions">
        {onRetry && (
          <button className="button button--primary" type="button" onClick={onRetry}>
            다시 시도
          </button>
        )}
        <Link className={onRetry ? 'button' : 'button button--primary'} href={backTo}>
          게시판으로 이동
        </Link>
      </div>
    </section>
  );
}

export function BoardLoading({ message = '게시판을 불러오는 중입니다…' }: { message?: string }) {
  return (
    <section className="view-panel board-view" aria-live="polite" aria-busy="true">
      <p>{message}</p>
    </section>
  );
}

export function BoardScopeInfo({ scope }: { scope: BoardScope }) {
  return (
    <div className="board-scope">
      <p className="board-scope__name">
        {scope.category.name}
        {scope.purpose && <> / {scope.purpose.name}</>}
      </p>
    </div>
  );
}
