import { Redirect } from 'wouter';
import { BoardBrowseView } from './BoardBrowseView';
import { BoardLoading, BoardMessage } from './BoardShared';
import { BoardWriteView } from './BoardWriteView';
import { BOARD_PAGE_SIZE, fetchBoardScope } from './board';
import { boardUrl } from './routes';
import { useBoardRequest } from './useBoardRequest';

export function BoardPage({ params, writing = false }: { params: URLSearchParams; writing?: boolean }) {
  const category = params.get('category');
  const purpose = params.get('purpose');
  const rawPage = params.get('page') ?? '0';
  const page = Number(rawPage);
  const invalid =
    category === '' ||
    purpose === '' ||
    (category === null && purpose !== null) ||
    !/^\d+$/.test(rawPage) ||
    !Number.isSafeInteger(page) ||
    page > Math.floor(2_147_483_647 / BOARD_PAGE_SIZE) ||
    ['category', 'purpose', 'page'].some((key) => params.getAll(key).length > 1);
  if (invalid) {
    return (
      <BoardMessage
        title="게시판 주소를 확인해 주세요"
        message="제품과 사용 목적을 다시 선택해 게시판으로 이동해 주세요."
      />
    );
  }
  if (writing) {
    if (category === null || purpose === null) {
      return <Redirect to={boardUrl(category, purpose)} replace />;
    }
    return <BoardWritePage category={category} purpose={purpose} />;
  }
  return <BoardBrowseView categoryCode={category} purposeCode={purpose} page={page} />;
}

function BoardWritePage({ category, purpose }: { category: string; purpose: string }) {
  const { state, retry } = useBoardRequest(JSON.stringify([category, purpose]), (signal) =>
    fetchBoardScope(category, purpose, signal),
  );
  if (state.status === 'loading') {
    return <BoardLoading />;
  }
  if (state.status === 'failed') {
    return (
      <BoardMessage
        title={state.notFound ? '선택한 게시판을 찾을 수 없습니다' : '게시판을 불러오지 못했습니다'}
        message={state.notFound ? '제품과 사용 목적을 다시 선택해 주세요.' : '잠시 뒤에 다시 시도해 주세요.'}
        onRetry={state.notFound ? undefined : retry}
      />
    );
  }
  const scope = state.data;
  if (scope.purpose === null) {
    return <Redirect to={boardUrl(category)} replace />;
  }
  const selected = { category: scope.category, purpose: scope.purpose };
  return <BoardWriteView key={JSON.stringify([category, purpose])} scope={selected} />;
}
