import { Link, Redirect } from 'wouter';
import { BoardListView } from './BoardListView';
import { fetchBoardCategories, fetchBoardPurposes } from './board';
import { boardUrl, currentNavState } from './routes';
import { CATEGORY_DISPLAY, PURPOSE_DISPLAY, type SelectionCard, type Served, toCards } from './selection';
import { useBoardRequest } from './useBoardRequest';
import './BoardView.css';
import './BoardBrowseView.css';

export function BoardBrowseView({
  categoryCode,
  purposeCode,
  page,
}: {
  categoryCode: string | null;
  purposeCode: string | null;
  page: number;
}) {
  const { state, retry } = useBoardRequest('board-categories', fetchBoardCategories);
  const categories = state.status === 'success' ? toCards(state.data, CATEGORY_DISPLAY) : [];
  const firstCategory = categories.find((item) => item.available);
  const category = state.status === 'success' ? state.data.find((item) => item.code === categoryCode) : undefined;

  if (categoryCode === null && firstCategory) {
    return <Redirect to={boardUrl(firstCategory.code, null, page)} replace state={currentNavState()} />;
  }

  return (
    <section className="view-panel board-view board-browse" aria-labelledby="board-title">
      <h1 className="board-title" id="board-title">
        질문 게시판
      </h1>
      <p className="section-description">제품과 사용 목적에 맞는 질문을 나눠 보세요.</p>
      <div className="board-browse__layout">
        <aside className="board-browse__sidebar" aria-label="게시판 제품 선택">
          {state.status === 'loading' && (
            <p className="board-meta" role="status">
              제품을 불러오는 중입니다…
            </p>
          )}
          {state.status === 'failed' && <BrowseError message="제품을 불러오지 못했습니다." onRetry={retry} />}
          {state.status === 'success' && (
            <BoardNavigation
              label="제품"
              kind="category"
              items={categories}
              selected={categoryCode}
              href={(code) => (code === categoryCode ? boardUrl(code, purposeCode, page) : boardUrl(code))}
            />
          )}
        </aside>
        <div className="board-browse__content">
          {state.status === 'success' &&
            (category ? (
              <BoardPurposePanel category={category} purposeCode={purposeCode} page={page} />
            ) : (
              <div className="board-browse__empty">
                <h2>{state.data.length === 0 ? '아직 이용할 수 있는 게시판이 없습니다' : '제품을 선택해 주세요'}</h2>
                <p className="board-meta" role={categoryCode === null ? undefined : 'alert'}>
                  {state.data.length === 0
                    ? '게시판이 열리면 이곳에서 질문을 나눌 수 있습니다.'
                    : categoryCode === null
                      ? '제품 탭에서 제품을 고르면 사용 목적별 게시판을 볼 수 있습니다.'
                      : '선택한 제품을 찾을 수 없습니다. 제품 탭에서 다시 선택해 주세요.'}
                </p>
              </div>
            ))}
        </div>
      </div>
    </section>
  );
}

function BoardPurposePanel({
  category,
  purposeCode,
  page,
}: {
  category: Served;
  purposeCode: string | null;
  page: number;
}) {
  const { state, retry } = useBoardRequest(category.code, (signal) => fetchBoardPurposes(category.code, signal));
  const purposes = state.status === 'success' ? toCards(state.data, PURPOSE_DISPLAY) : [];
  const firstPurpose = purposes.find((item) => item.available);
  const purpose = state.status === 'success' ? state.data.find((item) => item.code === purposeCode) : undefined;

  if (purposeCode === null && firstPurpose) {
    return <Redirect to={boardUrl(category.code, firstPurpose.code, page)} replace state={currentNavState()} />;
  }

  return (
    <>
      <h2 className="board-browse__category-title">{category.name}</h2>
      {state.status === 'loading' && (
        <p className="board-meta" role="status">
          사용 목적을 불러오는 중입니다…
        </p>
      )}
      {state.status === 'failed' && <BrowseError message="사용 목적을 불러오지 못했습니다." onRetry={retry} />}
      {state.status === 'success' && (
        <>
          <BoardNavigation
            label="사용 목적"
            kind="purpose"
            items={purposes}
            selected={purposeCode}
            href={(code) => boardUrl(category.code, code, code === purposeCode ? page : 0)}
          />
          {purpose ? (
            <BoardListView scope={{ category, purpose }} page={page} />
          ) : (
            <div className="board-browse__empty">
              <h3>
                {state.data.length === 0 ? '아직 이용할 수 있는 사용 목적이 없습니다' : '사용 목적을 선택해 주세요'}
              </h3>
              <p className="board-meta" role={purposeCode === null ? undefined : 'alert'}>
                {state.data.length === 0
                  ? '다른 제품의 게시판을 선택해 주세요.'
                  : purposeCode === null
                    ? '사용 목적 탭을 누르면 해당 질문 목록이 바로 표시됩니다.'
                    : '선택한 사용 목적을 찾을 수 없습니다. 위 탭에서 다시 선택해 주세요.'}
              </p>
            </div>
          )}
        </>
      )}
    </>
  );
}

// URL을 이동하는 탐색 링크이므로 기본 링크의 키보드 조작·새 탭 열기를 유지한다.
function BoardNavigation({
  label,
  kind,
  items,
  selected,
  href,
}: {
  label: string;
  kind: 'category' | 'purpose';
  items: SelectionCard[];
  selected: string | null;
  href: (code: string) => string;
}) {
  return (
    <nav className={`board-browse__nav board-browse__nav--${kind}`} aria-label={label}>
      <p className="board-browse__label">{label}</p>
      <ul className="board-browse__tabs">
        {items.map((item) => (
          <li key={item.code}>
            {item.available ? (
              <Link
                className="board-browse__tab"
                href={href(item.code)}
                aria-current={item.code === selected ? 'page' : undefined}
              >
                {item.name}
              </Link>
            ) : (
              <button className="board-browse__tab" type="button" disabled>
                {item.name}
                <span className="board-browse__badge">준비 중</span>
              </button>
            )}
          </li>
        ))}
      </ul>
    </nav>
  );
}

function BrowseError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <div className="board-browse__error">
      <p className="board-error" role="alert">
        {message}
      </p>
      <button className="button" type="button" onClick={onRetry}>
        다시 시도
      </button>
    </div>
  );
}
