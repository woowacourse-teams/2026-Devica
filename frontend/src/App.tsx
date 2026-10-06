import { useCallback, useEffect, useLayoutEffect, useMemo, useState } from 'react';
import { Redirect, Route, Switch } from 'wouter';
import { useHistoryState, usePathname, useSearch } from 'wouter/use-browser-location';
import { track } from './analytics';
import { BoardPage } from './BoardPage';
import { BoardPostPage } from './BoardPostView';
import { CategoryCards } from './CategoryCards';
import { FaqView } from './FaqView';
import { useFetched } from './fetched';
import { HomeHero } from './HomeHero';
import { IntroView } from './IntroView';
import { ProductDetailView } from './ProductDetailView';
import { initialListState, type ProductListState, ProductListView } from './ProductListView';
import type { SortType } from './products';
import { QuestionView } from './QuestionView';
import { type Answers, fetchQuestions, pruneHiddenAnswers, resolveScreens, toSearchParams } from './questions';
import { type ResultOs, ResultView } from './ResultView';
import { fetchRecommendation, osValueOf, type Spec } from './recommendation';
import {
  answerEntries,
  answersOf,
  backLabelOf,
  boardEntryUrl,
  conditionOf,
  currentNavState,
  flowEntries,
  flowOf,
  go,
  goBack,
  isBoardScreen,
  listEntries,
  type NavState,
  PATHS,
  positiveOf,
  query,
  replace,
  screenOf,
  sortOf,
  specOsOf,
  titleOf,
} from './routes';
import { SelectionView } from './SelectionView';
import { SiteFooter } from './SiteFooter';
import { SiteHeader } from './SiteHeader';

// 화면은 두 흐름으로 나뉜다.
// - 추천 흐름: 첫 화면(서비스 소개) → 제품 선택 → 사용 목적 → 기본 권장 사양 → 질문 → 결과 → 맞춤 목록
// - 검색 흐름(UC-07): 제품 유형 선택 → 전체 목록 → 제품 상세
// 공통 FAQ와 질문 게시판은 어느 화면에서든 헤더로 연다.
// 화면 안의 뒤로 버튼은 브라우저 뒤로 가기와 같은 곳으로 간다. 앱 밖에서 바로 들어왔으면 정해 둔 화면으로 간다.
export function App() {
  const pathname = usePathname();
  const search = useSearch();

  // 없는 주소 화면이 제목을 바꿀 수 있게 먼저 정한다. 부모의 useEffect 는 자식보다 늦게 돌아 자식이 바꾼 제목을 덮는다.
  useLayoutEffect(() => {
    document.title = titleOf(pathname);
  }, [pathname]);

  // 제품 유형 선택과 제품 목록은 주소가 같고 category 쿼리로만 나뉜다. 둘 사이를 오가는 것도 화면이 바뀐 것으로 본다.
  const choosingCategory = pathname === PATHS.products && new URLSearchParams(search).get('category') === null;

  // 새 화면은 맨 위에서, 뒤로 가기로 돌아온 화면은 보던 위치에서 연다. 목록은 제품을 그린 뒤 한 번 더 맞춘다.
  // biome-ignore lint/correctness/useExhaustiveDependencies: 화면이 바뀔 때마다 돌아야 한다. 같은 화면에서 조건만 바꿀 때는 스크롤을 건드리지 않는다.
  useEffect(() => {
    window.scrollTo(0, currentNavState().scrollY ?? 0);
  }, [pathname, choosingCategory]);

  const params = new URLSearchParams(search);
  const screen = screenOf(pathname);

  const boardHref = boardEntryUrl(pathname, params);
  const inBoard = isBoardScreen(screen);

  // 게시판 진입점은 헤더의 "질문 게시판" 링크다. 게시판 밖의 화면이 바뀔 때마다 그 링크가 보인 것으로 센다.
  // biome-ignore lint/correctness/useExhaustiveDependencies: 같은 화면에서 조건만 바꿀 때는 다시 세지 않는다.
  useEffect(() => {
    if (!inBoard) {
      track('board_entry_viewed', {});
    }
  }, [pathname]);

  // 게시판 밖에서 게시판으로 들어올 때만 센다. 게시판 안에서 목록·글·작성을 오가는 것은 세지 않는다.
  useEffect(() => {
    if (inBoard) {
      track('board_entered', {});
    }
  }, [inBoard]);

  // 가이드는 선택한 제품·목적과 관계없이 공통 FAQ를 연다.
  const openFaq = () => {
    if (screen !== 'faq' || search !== '') {
      go(PATHS.faq);
    }
  };

  // 헤더의 "제품 목록". 상세에서 누르면 들어오기 전 목록으로 돌아간다.
  // 추천 흐름에서 이미 유형을 골랐다면 유형 선택을 건너뛰고 그 목록을 연다. 첫 화면은 유형을 고르는 중이라 예외다.
  const openSearch = () => {
    if (screen === 'products') {
      return;
    }
    if (screen === 'detail' && currentNavState().from === 'products') {
      window.history.back();
      return;
    }
    const category = screen === 'category' ? null : params.get('category');
    go(`${PATHS.products}${query([['category', category]])}`);
  };

  return (
    <>
      <SiteHeader
        onGuide={openFaq}
        onProductList={openSearch}
        boardHref={boardHref}
        onBoard={() => go(boardHref)}
        boardActive={inBoard}
        home={screen === 'home'}
      />
      <main className={screen === 'home' ? 'probe probe--home' : 'probe'} id="main-content">
        <Switch>
          <Route path={PATHS.home}>
            <LegacyRedirect params={params} />
          </Route>
          <Route path={PATHS.category}>
            <CategoryPage />
          </Route>
          <Route path={PATHS.purpose}>
            <PurposePage params={params} />
          </Route>
          <Route path={PATHS.intro}>
            <IntroPage params={params} search={search} />
          </Route>
          <Route path={PATHS.question}>{({ step }) => <QuestionPage params={params} step={step} />}</Route>
          <Route path={PATHS.result}>
            <ResultPage params={params} />
          </Route>
          <Route path={PATHS.matched}>
            <MatchedListPage params={params} search={search} />
          </Route>
          <Route path={PATHS.products}>
            <AllListPage params={params} search={search} />
          </Route>
          <Route path={PATHS.detail}>{({ id }) => <DetailPage id={positiveOf(id)} />}</Route>
          <Route path={PATHS.faq}>
            <FaqPage />
          </Route>
          <Route path={PATHS.boardWrite}>
            <BoardPage params={params} writing />
          </Route>
          <Route path={PATHS.boardPost}>{({ id }) => <BoardPostPage id={id} />}</Route>
          <Route path={PATHS.board}>
            <BoardPage params={params} />
          </Route>
          <Route>
            <NotFound />
          </Route>
        </Switch>
      </main>
      <SiteFooter />
    </>
  );
}

type PageProps = {
  params: URLSearchParams;
};

// 주소를 두기 전에는 헤더 링크가 /?view=products, /?view=faq 였다. 이미 퍼진 링크가 계속 열리게 새 주소로 옮긴다.
function LegacyRedirect({ params }: PageProps) {
  const view = params.get('view');
  if (view === 'products') {
    return <Redirect to={PATHS.products} replace />;
  }
  if (view === 'faq') {
    return <Redirect to={PATHS.faq} replace />;
  }
  return <HomeHero onStart={() => go(PATHS.category)} />;
}

function CategoryPage() {
  const navState = useHistoryState<NavState | null>();
  return (
    <SelectionView
      categoryCode={null}
      back={{ label: backLabelOf(navState?.from ?? 'home'), onClick: () => goBack(PATHS.home) }}
      onSelect={(code) => go(`${PATHS.purpose}${query([['category', code]])}`)}
    />
  );
}

function PurposePage({ params }: PageProps) {
  const { category } = flowOf(params);
  if (category === null) {
    return <NotFound />;
  }
  return (
    <SelectionView
      categoryCode={category}
      back={{ label: '← 제품 다시 고르기', onClick: () => goBack(PATHS.category, 'category') }}
      onSelect={(code) => go(`${PATHS.intro}${query(flowEntries({ category, purpose: code }))}`)}
    />
  );
}

// 질문은 사용 목적마다 다르다. 기본 권장 사양 화면과 질문 화면이 같은 응답을 쓴다.
function useQuestions(purpose: string | null) {
  return useFetched(purpose === null ? null : `questions:${purpose}`, () => fetchQuestions(purpose ?? ''));
}

// 질문 중의 답은 주소에 담지 않는다. 문항마다 주소에 복사해 두면 앞으로 가기로 바꾸기 전 답이 되살아난다.
// 탭 저장소에 한 벌만 두어 새로고침에서만 복원하고, 공유 링크로는 넘기지 않는다.
const ANSWERS_KEY = 'devica.answers';

function loadAnswers(purpose: string): Answers {
  try {
    const saved: unknown = JSON.parse(sessionStorage.getItem(ANSWERS_KEY) ?? 'null');
    if (typeof saved === 'object' && saved !== null && 'answers' in saved && 'purpose' in saved) {
      return saved.purpose === purpose ? (saved.answers as Answers) : {};
    }
  } catch {
    // 저장소를 쓸 수 없으면(사생활 보호 모드 등) 답 없이 시작한다.
  }
  return {};
}

function saveAnswers(purpose: string, answers: Answers): void {
  try {
    sessionStorage.setItem(ANSWERS_KEY, JSON.stringify({ purpose, answers }));
  } catch {
    // 저장하지 못해도 진행에는 지장이 없다. 새로고침하면 답이 사라질 뿐이다.
  }
}

function IntroPage({ params, search }: PageProps & { search: string }) {
  const { category, purpose } = flowOf(params);
  const questions = useQuestions(purpose);
  if (category === null || purpose === null) {
    return <NotFound />;
  }
  const screens = resolveScreens(questions.data ?? [], {});
  return (
    <IntroView
      purposeCode={purpose}
      onBack={() => goBack(`${PATHS.purpose}${query([['category', category]])}`, 'purpose')}
      onStart={() => {
        track('onboarding_started', { category, purpose });
        saveAnswers(purpose, {});
        go(`/questions/1${search}`);
      }}
      canStart={screens.length > 0}
      questionsFailed={questions.failed}
      questionsLoading={questions.data === null && !questions.failed}
      // 답을 비워 보내면 조정 전 기본 권장 사양이 온다. 원본도 답을 무시하고 기본으로 되돌린다.
      onBaseline={() => {
        saveAnswers(purpose, {});
        go(`${PATHS.result}${search}`);
      }}
    />
  );
}

function QuestionPage({ params, step }: PageProps & { step: string }) {
  const flow = flowOf(params);
  const { purpose } = flow;
  const questions = useQuestions(purpose);
  const [answers, setAnswers] = useState<Answers>(() => (purpose === null ? {} : loadAnswers(purpose)));
  const number = positiveOf(step);
  const suffix = query(flowEntries(flow));

  if (flow.category === null || purpose === null || number === null) {
    return <NotFound />;
  }
  if (questions.failed) {
    return (
      <section className="view-panel">
        <p className="notice" role="alert">
          질문을 불러오지 못했습니다. 잠시 뒤에 새로고침해 주세요.
        </p>
      </section>
    );
  }
  if (questions.data === null) {
    return (
      <section className="view-panel">
        <p className="section-description">질문을 불러오는 중입니다…</p>
      </section>
    );
  }

  const screens = resolveScreens(questions.data, answers);
  // 답이 바뀌어 문항이 줄었거나 없는 번호로 들어왔으면 남은 마지막 문항으로 보낸다.
  // 옮긴 칸의 바로 앞 칸은 이전 문항이 아닐 수 있다. from 을 지워 "이전"이 기록을 되돌리지 않고 이전 문항으로 가게 한다.
  if (number > screens.length && screens.length > 0) {
    return (
      <Redirect
        to={`/questions/${screens.length}${suffix}`}
        replace
        state={{ ...currentNavState(), from: undefined }}
      />
    );
  }

  const answer = (code: string, values: string[]) => {
    // 답이 바뀌면 딸린 질문이 사라질 수 있다. 사라진 질문의 답은 함께 지운다.
    const next = pruneHiddenAnswers(questions.data ?? [], { ...answers, [code]: values });
    setAnswers(next);
    saveAnswers(purpose, next);
  };

  const goPrevious = () => {
    if (number === 1) {
      goBack(`${PATHS.intro}${suffix}`, 'intro');
      return;
    }
    goBack(`/questions/${number - 1}${suffix}`, 'question');
  };

  const goNext = () => {
    if (number < screens.length) {
      go(`/questions/${number + 1}${suffix}`);
      return;
    }
    track('onboarding_completed', flow);
    go(`${PATHS.result}${query([...flowEntries(flow), ...answerEntries(answers)])}`);
  };

  return (
    <QuestionView
      screens={screens}
      index={number - 1}
      answers={answers}
      onAnswer={answer}
      onPrevious={goPrevious}
      onNext={goNext}
    />
  );
}

// 결과와 맞춤 목록은 주소의 사용 목적과 답으로 권장 사양을 받는다. 새로고침과 공유 링크도 같은 길을 탄다.
// 유형이 없으면 권장 사양을 받아도 제품을 찾을 수 없다. 두 화면이 같은 조건으로 주소를 거른다.
function useRecommendation(params: URLSearchParams) {
  const { category, purpose } = flowOf(params);
  const answers = toSearchParams(answersOf(params));
  return useFetched(category === null || purpose === null ? null : `recommendation:${purpose}?${answers}`, () =>
    fetchRecommendation(purpose ?? '', answers),
  );
}

// 주소에 OS 가 없으면, 권장안이 한쪽 OS 만 나왔을 때 그 OS 를 보여준다.
function resultOsOf(params: URLSearchParams, specs: Spec[]): ResultOs {
  return specOsOf(params) ?? (specs.length === 1 ? (osValueOf(specs[0]) as ResultOs) : 'BOTH');
}

function RecommendationStatus({ failed, onRetry }: { failed: boolean; onRetry: () => void }) {
  return (
    <section className="view-panel" aria-live="polite">
      {failed ? (
        <>
          <p className="notice" role="alert">
            권장 사양을 불러오지 못했습니다. 잠시 뒤에 다시 시도해 주세요.
          </p>
          <button className="button button--primary button--wide" type="button" onClick={onRetry}>
            다시 시도
          </button>
        </>
      ) : (
        <p className="section-description">권장 사양을 불러오는 중입니다…</p>
      )}
    </section>
  );
}

function ResultPage({ params }: PageProps) {
  const recommendation = useRecommendation(params);
  const specs = recommendation.data;
  const { category, purpose } = flowOf(params);
  const loaded = specs !== null;

  // 질문을 거쳤든 건너뛰었든, 다시 들어왔든 권장 사양이 보이면 보낸다. OS 를 바꿔도 권장 사양은 다시 받지 않아 또 보내지 않는다.
  useEffect(() => {
    if (loaded) {
      track('recommendation_result_viewed', { category, purpose });
    }
  }, [loaded, category, purpose]);

  if (category === null || purpose === null) {
    return <NotFound />;
  }
  if (specs === null) {
    return <RecommendationStatus failed={recommendation.failed} onRetry={recommendation.retry} />;
  }
  const os = resultOsOf(params, specs);
  const withOs = (next: ResultOs) => {
    const copy = new URLSearchParams(params);
    copy.set('specOs', next);
    return copy.toString();
  };
  return (
    <ResultView
      specs={specs}
      os={os}
      onChangeOs={(next) => replace(`${PATHS.result}?${withOs(next)}`)}
      onSearch={() => go(`${PATHS.matched}?${withOs(os)}`, { searching: true })}
    />
  );
}

// 추천순은 권장안이 하나일 때만 쓸 수 있다. 그때만 기본값이 추천순이다.
function listSortOf(params: URLSearchParams, defaultSort: SortType): SortType {
  const sort = sortOf(params) ?? defaultSort;
  return sort === 'RECOMMENDED' && defaultSort !== 'RECOMMENDED' ? defaultSort : sort;
}

/**
 * 목록의 정렬·검색 조건은 주소에, 조건 패널을 펼쳤는지는 방문 기록 칸에 둔다.
 * 조건을 바꿀 때는 기록을 쌓지 않는다. 목록의 뒤로 버튼은 이전 조건이 아니라 들어오기 전 화면으로 간다.
 */
function useListState(search: string, defaultSort: SortType) {
  const navState = useHistoryState<NavState | null>();
  const state = useMemo<Omit<ProductListState, 'filterOpen'>>(() => {
    const parsed = new URLSearchParams(search);
    return { sort: listSortOf(parsed, defaultSort), condition: conditionOf(parsed) };
  }, [search, defaultSort]);

  // 늘 같은 함수여야 한다. 목록이 검색어 입력을 기다리는 effect 의 의존성으로 쓴다. 그래서 값은 호출할 때 주소에서 읽는다.
  const onChangeState = useCallback(
    (update: (previous: ProductListState) => ProductListState) => {
      const current = new URLSearchParams(window.location.search);
      const next = update({
        sort: listSortOf(current, defaultSort),
        condition: conditionOf(current),
        filterOpen: currentNavState().filterOpen === true,
      });
      for (const [key, value] of listEntries(next.sort, next.condition)) {
        if (value === null) {
          current.delete(key);
        } else {
          current.set(key, String(value));
        }
      }
      replace(`${window.location.pathname}?${current}`, { ...currentNavState(), filterOpen: next.filterOpen });
    },
    [defaultSort],
  );

  return { state: { ...state, filterOpen: navState?.filterOpen === true }, onChangeState, navState: navState ?? {} };
}

// 사양 대조 화면은 권장 사양에서 막 넘어왔을 때 한 번만 띄운다. 새로고침이나 뒤로 가기로 돌아오면 다시 띄우지 않는다.
// 그래서 권장 사양으로 제품을 찾아본 것은 여기서 한 번만 센다. 목록을 받지 못했으면 세지 않는다.
const finishSearching = (failed: boolean) => {
  if (!failed) {
    track('recommended_products_viewed', flowOf(new URLSearchParams(window.location.search)));
  }
  replace(`${window.location.pathname}${window.location.search}`, {
    ...currentNavState(),
    searching: false,
  });
};

function MatchedListPage({ params, search }: PageProps & { search: string }) {
  const recommendation = useRecommendation(params);
  const specs = recommendation.data ?? [];
  const os = resultOsOf(params, specs);
  const visibleSpecs = os === 'BOTH' ? specs : specs.filter((spec) => osValueOf(spec) === os);
  // 권장안이 둘이면 두 목록을 합쳐야 해서 추천순을 쓸 수 없다.
  const defaultSort = initialListState(visibleSpecs).sort;
  const { state, onChangeState, navState } = useListState(search, defaultSort);
  const { category } = flowOf(params);

  if (category === null || flowOf(params).purpose === null) {
    return <NotFound />;
  }
  if (recommendation.data === null) {
    return <RecommendationStatus failed={recommendation.failed} onRetry={recommendation.retry} />;
  }

  // 앱 밖에서 바로 들어왔으면 이 목록의 권장 사양 화면으로 보낸다. 목록 조건은 권장 사양과 상관없어 뺀다.
  const resultParams = new URLSearchParams(params);
  for (const [key] of listEntries(null, conditionOf(params))) {
    resultParams.delete(key);
  }

  return (
    <ProductListView
      // 유형이 바뀌면 새로 그린다. 받아 둔 이전 목록이 새 결과처럼 보이지 않고 불러오는 중부터 시작한다.
      key={`MATCHED-${category}`}
      categoryCode={category}
      mode="MATCHED"
      specs={visibleSpecs}
      backLabel={backLabelOf(navState.from ?? 'result')}
      state={state}
      onChangeState={onChangeState}
      searching={navState.searching === true}
      onSearched={finishSearching}
      onBack={() => goBack(`${PATHS.result}?${resultParams}`)}
      onDetail={(id) => go(`/products/${id}`)}
      // 보고 있던 유형 그대로 연다. 맞춤 목록의 조건은 주소에 남아 있어 돌아오면 그대로다.
      onShowAll={() => go(`${PATHS.products}${query([['category', category]])}`)}
    />
  );
}

function AllListPage({ params, search }: PageProps & { search: string }) {
  const { state, onChangeState, navState } = useListState(search, 'PRICE_ASC');
  const category = params.get('category');
  const onBack = () => goBack(PATHS.home);

  // 유형을 고르기 전에는 추천 흐름의 제품 선택과 같은 화면을 띄우고, 고르면 목록 화면으로 넘어간다.
  if (category === null) {
    return (
      <CategoryCards
        back={{ label: backLabelOf(navState.from ?? 'home'), onClick: onBack }}
        onSelect={(code) => go(`${PATHS.products}${query([['category', code]])}`)}
      />
    );
  }
  // 유형 선택에서 왔으면 뒤로 가기는 그 선택 화면으로 돌아간다.
  const backLabel = navState.from === 'products' ? '← 제품 다시 고르기' : backLabelOf(navState.from ?? 'home');
  return (
    <ProductListView
      key={`ALL-${category}`}
      categoryCode={category}
      mode="ALL"
      specs={[]}
      backLabel={backLabel}
      state={state}
      onChangeState={onChangeState}
      searching={false}
      onSearched={finishSearching}
      onBack={onBack}
      onDetail={(id) => go(`/products/${id}`)}
      onShowAll={() => {}}
    />
  );
}

function DetailPage({ id }: { id: number | null }) {
  const navState = useHistoryState<NavState | null>();
  if (id === null) {
    return <NotFound />;
  }
  return (
    <ProductDetailView
      productId={id}
      backLabel={backLabelOf(navState?.from ?? 'products')}
      onBack={() => goBack(PATHS.products)}
    />
  );
}

function FaqPage() {
  const navState = useHistoryState<NavState | null>();
  return (
    <FaqView
      categoryCode={null}
      purposeCode={null}
      backLabel={backLabelOf(navState?.from ?? 'home')}
      onBack={() => goBack(PATHS.home)}
    />
  );
}

// 주소에 없는 화면이거나 화면에 필요한 값이 빠졌다. fallback 이 어떤 경로에나 앱을 돌려주므로 여기서 가려낸다.
function NotFound() {
  // 경로는 있지만 값이 빠진 경우(/intro 만 연 경우 등) 제목이 그 화면 이름으로 남지 않게 한다.
  useEffect(() => {
    document.title = '페이지를 찾을 수 없습니다 — Devica';
    return () => {
      document.title = titleOf(window.location.pathname);
    };
  }, []);

  return (
    <section className="view-panel" aria-labelledby="not-found-title">
      <h1 id="not-found-title">페이지를 찾을 수 없습니다</h1>
      <p className="section-description">주소가 바뀌었거나 잘못 입력되었을 수 있습니다.</p>
      <button className="button button--primary button--wide" type="button" onClick={() => go(PATHS.home)}>
        처음으로
      </button>
    </section>
  );
}
