import { useLayoutEffect, useRef } from 'react';
import { Link, Redirect } from 'wouter';
import { BoardListView } from './BoardListView';
import { fetchBoardCategories, fetchBoardPurposes } from './board';
import { useFetched } from './fetched';
import { boardUrl, currentNavState } from './routes';
import { CATEGORY_DISPLAY, PURPOSE_DISPLAY, type SelectionCard, type Served, toCards } from './selection';
import './BoardView.css';
import './BoardBrowseView.css';

const UNABORTED = new AbortController().signal;

export function BoardBrowseView({
  categoryCode,
  purposeCode,
  page,
}: {
  categoryCode: string | null;
  purposeCode: string | null;
  page: number;
}) {
  const { data, failed, retry } = useFetched('board-categories', () => fetchBoardCategories(UNABORTED));
  const categories = data === null ? [] : toCards(data, CATEGORY_DISPLAY);
  const firstCategory = categories.find((item) => item.available);
  const category = data?.find((item) => item.code === categoryCode);

  if (categoryCode === null && firstCategory) {
    return <Redirect to={boardUrl(firstCategory.code, null, page)} replace state={currentNavState()} />;
  }

  return (
    <section className="view-panel flow-view board-browse" aria-labelledby="board-title">
      <div className="flow-head">
        <h1 id="board-title">질문 게시판</h1>
      </div>
      {data === null && !failed && (
        <p className="board-browse__card board-status" role="status">
          게시판을 불러오는 중입니다…
        </p>
      )}
      {data === null && failed && <BrowseError message="게시판을 불러오지 못했습니다." onRetry={retry} />}
      {data !== null && (
        <div className="board-browse__layout">
          <aside className="board-browse__sidebar" aria-label="게시판 제품 선택">
            <BoardNavigation
              label="제품"
              kind="category"
              items={categories}
              selected={categoryCode}
              href={(code) => (code === categoryCode ? boardUrl(code, purposeCode, page) : boardUrl(code))}
            />
          </aside>
          <div className="board-browse__content">
            {category ? (
              <BoardPurposePanel category={category} purposeCode={purposeCode} page={page} />
            ) : (
              <div className="board-browse__empty">
                <h2>{data.length === 0 ? '아직 이용할 수 있는 게시판이 없습니다' : '제품을 선택해 주세요'}</h2>
                <p className="board-meta" role={categoryCode === null ? undefined : 'alert'}>
                  {data.length === 0
                    ? '게시판이 열리면 이곳에서 질문을 나눌 수 있습니다.'
                    : categoryCode === null
                      ? '제품 탭에서 제품을 고르면 사용 목적별 게시판을 볼 수 있습니다.'
                      : '선택한 제품을 찾을 수 없습니다. 제품 탭에서 다시 선택해 주세요.'}
                </p>
              </div>
            )}
          </div>
        </div>
      )}
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
  const { data, failed, retry } = useFetched(`board-purposes:${category.code}`, () =>
    fetchBoardPurposes(category.code, UNABORTED),
  );
  const purposes = data === null ? [] : toCards(data, PURPOSE_DISPLAY);
  const firstPurpose = purposes.find((item) => item.available);
  const purpose = data?.find((item) => item.code === purposeCode);

  if (purposeCode === null && firstPurpose) {
    return <Redirect to={boardUrl(category.code, firstPurpose.code, page)} replace state={currentNavState()} />;
  }

  return (
    <>
      <div className="board-browse__head">
        <h2 className="board-browse__category-title">{category.name}</h2>
        {data === null && !failed && (
          <p className="board-meta" role="status">
            사용 목적을 불러오는 중입니다…
          </p>
        )}
        {data !== null && (
          <BoardNavigation
            label="사용 목적"
            kind="purpose"
            items={purposes}
            selected={purposeCode}
            href={(code) => boardUrl(category.code, code, code === purposeCode ? page : 0)}
          />
        )}
      </div>
      {data === null && failed && <BrowseError message="사용 목적을 불러오지 못했습니다." onRetry={retry} />}
      {data !== null &&
        (purpose ? (
          <BoardListView scope={{ category, purpose }} page={page} />
        ) : (
          <div className="board-browse__empty">
            <h3>{data.length === 0 ? '아직 이용할 수 있는 사용 목적이 없습니다' : '사용 목적을 선택해 주세요'}</h3>
            <p className="board-meta" role={purposeCode === null ? undefined : 'alert'}>
              {data.length === 0
                ? '다른 제품의 게시판을 선택해 주세요.'
                : purposeCode === null
                  ? '사용 목적 탭을 누르면 해당 질문 목록이 바로 표시됩니다.'
                  : '선택한 사용 목적을 찾을 수 없습니다. 위 탭에서 다시 선택해 주세요.'}
            </p>
          </div>
        ))}
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
  const tabs = useRef<HTMLUListElement>(null);
  // biome-ignore lint/correctness/useExhaustiveDependencies: 선택한 탭이 바뀌면 DOM 에서 그 탭의 자리를 다시 재야 한다.
  useLayoutEffect(() => {
    const list = tabs.current;
    if (kind !== 'purpose' || !list) return;
    const place = () => {
      const current = list.querySelector<HTMLElement>('[aria-current="page"]');
      list.style.setProperty('--indicator-x', `${current?.offsetLeft ?? 0}px`);
      list.style.setProperty('--indicator-y', `${current?.offsetTop ?? 0}px`);
      list.style.setProperty('--indicator-w', `${current?.offsetWidth ?? 0}px`);
      list.style.setProperty('--indicator-h', `${current?.offsetHeight ?? 0}px`);
    };
    place();
    const frame = requestAnimationFrame(() => list.setAttribute('data-sliding', ''));
    const observer = new ResizeObserver(place);
    observer.observe(list);
    return () => {
      cancelAnimationFrame(frame);
      observer.disconnect();
    };
  }, [kind, selected]);

  return (
    <nav className={`board-browse__nav board-browse__nav--${kind}`} aria-label={label}>
      <p className="board-browse__label">{label}</p>
      <ul className="board-browse__tabs" ref={tabs}>
        {items.map((item) => (
          <li key={item.code}>
            {item.available ? (
              <Link
                className="board-browse__tab"
                href={href(item.code)}
                aria-current={item.code === selected ? 'page' : undefined}
              >
                {item.shortName ?? item.name}
              </Link>
            ) : (
              <button className="board-browse__tab" type="button" disabled>
                {item.shortName ?? item.name}
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
