import { useEffect, useState } from 'react';
import { CategoryTabs } from './CategoryTabs';
import { IntroView } from './IntroView';
import { ProductDetailView } from './ProductDetailView';
import { initialListState, type ListMode, type ProductListState, ProductListView } from './ProductListView';
import { QuestionView } from './QuestionView';
import {
  type Answers,
  fetchQuestions,
  pruneHiddenAnswers,
  type Question,
  resolveScreens,
  toSearchParams,
} from './questions';
import { type ResultOs, ResultView } from './ResultView';
import { fetchRecommendation, osValueOf, type Spec } from './recommendation';
import { SelectionView } from './SelectionView';
import { SiteFooter } from './SiteFooter';
import { SiteHeader } from './SiteHeader';

// 화면은 두 흐름으로 나뉜다.
// - 추천 흐름: CATEGORY → PURPOSE → INTRO → QUESTION → RESULT → 맞춤 목록(PRODUCT_LIST, MATCHED)
// - 검색 흐름(UC-07): 전체 목록(PRODUCT_LIST, ALL, 맨 위에 제품 유형 탭) → PRODUCT_DETAIL
// 뒤로 가기는 지금 흐름 안에서 한 단계 위로 간다. 검색 흐름의 맨 위(전체 목록)에서 뒤로 가면 들어오기 전 추천 흐름 화면으로 돌아간다.
type View = 'CATEGORY' | 'PURPOSE' | 'INTRO' | 'QUESTION' | 'RESULT' | 'PRODUCT_LIST' | 'PRODUCT_DETAIL';

// 검색 흐름을 빠져나올 때 돌아갈 수 있는 추천 흐름 화면이다.
const RETURNABLE = ['CATEGORY', 'PURPOSE', 'INTRO', 'QUESTION', 'RESULT'] as const;

type ReturnView = (typeof RETURNABLE)[number];

const BACK_LABELS: Record<ReturnView, string> = {
  CATEGORY: '← 처음으로',
  PURPOSE: '← 사용 목적 선택으로',
  INTRO: '← 기본 권장 사양으로',
  QUESTION: '← 질문으로',
  RESULT: '← 권장 사양으로',
};

// 다른 페이지의 헤더에서는 링크로 넘어온다. 주소에 view=products 가 있으면 전체 목록으로 시작한다.
function openedFromLink(): boolean {
  return new URLSearchParams(window.location.search).get('view') === 'products';
}

function isReturnable(view: View): view is ReturnView {
  return RETURNABLE.some((returnable) => returnable === view);
}

export function App() {
  const [view, setView] = useState<View>(() => (openedFromLink() ? 'PRODUCT_LIST' : 'CATEGORY'));
  // 첫 화면 흐름에서 고른 유형과 전체 목록에서 고른 유형은 따로 둔다. 헤더로 다른 유형을 봐도 진행 중인 흐름은 그대로다.
  const [categoryCode, setCategoryCode] = useState<string | null>(null);
  const [listCategoryCode, setListCategoryCode] = useState<string | null>(null);
  const [purposeCode, setPurposeCode] = useState<string | null>(null);
  // 아직 받지 못한 상태(null)를 질문이 없는 경우와 구분한다.
  const [questions, setQuestions] = useState<Question[] | null>(null);
  const [questionsFailed, setQuestionsFailed] = useState(false);
  const [answers, setAnswers] = useState<Answers>({});
  const [index, setIndex] = useState(0);
  const [specs, setSpecs] = useState<Spec[]>([]);
  const [resultOs, setResultOs] = useState<ResultOs>('BOTH');
  // 권장 사양을 기다리는 동안 답을 바꾸면 먼저 보낸 응답이 바뀐 답을 덮는다. 기다리는 동안은 질문 화면을 잠근다.
  const [waiting, setWaiting] = useState(false);
  const [resultFailed, setResultFailed] = useState(false);
  // 목록의 정렬·검색 조건은 여기 둔다. 상세를 다녀와도 조작이 풀리지 않아야 한다.
  const [listState, setListState] = useState<ProductListState>(() => initialListState([]));
  const [listMode, setListMode] = useState<ListMode>(() => (openedFromLink() ? 'ALL' : 'MATCHED'));
  // 맞춤 목록의 "전체 제품 보기"로 연 전체 목록은 검색 흐름이 아니라 맞춤 목록에서 필터를 푼 것이다. 뒤로 가면 맞춤 목록으로 돌아간다.
  const [allFromMatched, setAllFromMatched] = useState(false);
  const [matchedListState, setMatchedListState] = useState<ProductListState>(() => initialListState([]));
  const [returnView, setReturnView] = useState<ReturnView>(() => (openedFromLink() ? 'CATEGORY' : 'RESULT'));
  // 사양 대조 화면은 목록이 제품을 받아올 때까지 띄운다. 상세를 다녀올 때는 다시 띄우지 않는다.
  const [searching, setSearching] = useState(false);
  const [productId, setProductId] = useState<number | null>(null);

  // 질문은 사용 목적마다 다르다. 목적을 고른 뒤에 받고, 목적을 바꾸면 늦게 온 이전 응답은 버린다.
  useEffect(() => {
    if (purposeCode === null) {
      return;
    }
    let stale = false;
    setQuestions(null);
    setQuestionsFailed(false);
    fetchQuestions(purposeCode)
      .then((received) => {
        if (!stale) {
          setQuestions(received);
        }
      })
      .catch(() => {
        if (!stale) {
          setQuestionsFailed(true);
        }
      });
    return () => {
      stale = true;
    };
  }, [purposeCode]);

  // 전체 목록은 헤더에서 고른 유형을, 추천 목록은 첫 화면 흐름에서 고른 유형을 보여준다.
  const shownCategoryCode = listMode === 'ALL' ? listCategoryCode : categoryCode;
  const screens = resolveScreens(questions ?? [], answers);
  // 검색 로딩과 제품 목록은 결과 화면이 보여주고 있던 OS 만 대상으로 삼는다.
  const visibleSpecs = resultOs === 'BOTH' ? specs : specs.filter((spec) => osValueOf(spec) === resultOs);

  const answer = (code: string, values: string[]) => {
    // 답이 바뀌면 딸린 질문이 사라질 수 있다. 사라진 질문의 답은 함께 지운다.
    setAnswers((previous) => pruneHiddenAnswers(questions ?? [], { ...previous, [code]: values }));
  };

  const goPrevious = () => {
    // 마지막 화면을 떠나면 권장 사양 실패 안내도 함께 거둔다.
    setResultFailed(false);
    if (index === 0) {
      setView('INTRO');
      return;
    }
    setIndex(index - 1);
  };

  const goNext = () => {
    if (index < screens.length - 1) {
      setIndex(index + 1);
      return;
    }
    void showResult(answers);
  };

  // 사양 계산은 서버가 한다. 답을 그대로 실어 보낸다.
  const showResult = async (given: Answers) => {
    if (purposeCode === null) {
      return;
    }
    setWaiting(true);
    setResultFailed(false);
    try {
      const received = await fetchRecommendation(purposeCode, toSearchParams(given));
      setSpecs(received);
      // 권장안이 한쪽 OS 만 나오면 그 OS 를 보여준다.
      setResultOs(received.length === 1 ? (osValueOf(received[0]) as ResultOs) : 'BOTH');
      // 기다리는 동안 헤더로 제품 목록에 갔다면 그 화면을 덮지 않는다.
      setView((current) => (current === 'INTRO' || current === 'QUESTION' ? 'RESULT' : current));
    } catch {
      // 빈 권장안과 서버·네트워크 오류는 다르다. 답은 그대로 두고 질문 화면에 머무른다.
      setResultFailed(true);
    } finally {
      setWaiting(false);
    }
  };

  const inSearchFlow = (view === 'PRODUCT_LIST' || view === 'PRODUCT_DETAIL') && listMode === 'ALL' && !allFromMatched;

  // 검색 흐름의 유형은 탭에서 고른다. null 이면 탭이 고를 수 있는 첫 유형을 대신 고른다.
  const openAllProducts = (category: string | null, fromMatched: boolean) => {
    setListCategoryCode(category);
    setAllFromMatched(fromMatched);
    setListMode('ALL');
    setListState(initialListState([]));
    setView('PRODUCT_LIST');
  };

  // 헤더의 "제품 목록". 검색 흐름 밖에서 누르면 지금 화면을 돌아갈 곳으로 기억한다(맞춤 목록 쪽이면 권장 사양).
  // 추천 흐름에서 이미 유형을 골랐다면 그 탭을 미리 고른다. 첫 화면은 유형을 고르는 중이라 예외다.
  const openSearch = () => {
    if (inSearchFlow) {
      setView('PRODUCT_LIST');
      return;
    }
    setReturnView(isReturnable(view) ? view : 'RESULT');
    openAllProducts(view === 'CATEGORY' ? null : categoryCode, false);
  };

  const changeListCategory = (code: string) => {
    setListCategoryCode(code);
    // 검색·필터 조건은 유형마다 다르다. 유형을 바꾸면 처음 상태로 연다.
    setListState(initialListState([]));
  };

  const closeList = () => {
    if (listMode === 'MATCHED') {
      setView('RESULT');
      return;
    }
    if (allFromMatched) {
      setListMode('MATCHED');
      setListState(matchedListState);
      return;
    }
    setView(returnView);
  };

  const listBackLabel =
    listMode === 'MATCHED' ? BACK_LABELS.RESULT : allFromMatched ? '← 맞춤 목록으로' : BACK_LABELS[returnView];

  const start = () => {
    setAnswers({});
    setIndex(0);
    setResultFailed(false);
    setView('QUESTION');
  };

  return (
    <>
      <SiteHeader onProductList={openSearch} />
      <main className="probe" id="main-content">
        {view === 'CATEGORY' && (
          <SelectionView
            categoryCode={null}
            onSelect={(code) => {
              setCategoryCode(code);
              setView('PURPOSE');
            }}
          />
        )}
        {view === 'PURPOSE' && categoryCode !== null && (
          <SelectionView
            categoryCode={categoryCode}
            back={{ label: '← 제품 다시 고르기', onClick: () => setView('CATEGORY') }}
            onSelect={(code) => {
              setPurposeCode(code);
              setView('INTRO');
            }}
          />
        )}
        {view === 'INTRO' && purposeCode !== null && (
          <IntroView
            purposeCode={purposeCode}
            onBack={() => setView('PURPOSE')}
            onStart={start}
            canStart={screens.length > 0}
            questionsFailed={questionsFailed}
            questionsLoading={questions === null && !questionsFailed}
            waiting={waiting}
            resultFailed={resultFailed}
            onBaseline={() => {
              setAnswers({});
              setIndex(0);
              void showResult({});
            }}
          />
        )}
        {view === 'RESULT' && (
          <ResultView
            specs={specs}
            os={resultOs}
            onChangeOs={setResultOs}
            onSearch={() => {
              setListMode('MATCHED');
              setListState(initialListState(visibleSpecs));
              setSearching(true);
              setView('PRODUCT_LIST');
            }}
          />
        )}
        {view === 'PRODUCT_LIST' && inSearchFlow && (
          <CategoryTabs selected={listCategoryCode} onSelect={changeListCategory} />
        )}
        {view === 'PRODUCT_LIST' && shownCategoryCode !== null && (
          <ProductListView
            // 유형이 바뀌면 새로 그린다. 받아 둔 이전 유형의 제품이 새 유형의 결과처럼 보이지 않고 불러오는 중부터 시작한다.
            key={shownCategoryCode}
            categoryCode={shownCategoryCode}
            mode={listMode}
            specs={listMode === 'ALL' ? [] : visibleSpecs}
            backLabel={listBackLabel}
            state={listState}
            onChangeState={setListState}
            searching={searching}
            onSearched={() => setSearching(false)}
            onBack={closeList}
            onDetail={(id) => {
              setProductId(id);
              setView('PRODUCT_DETAIL');
            }}
            // 맞춤 목록의 "전체 제품 보기"는 보고 있던 유형 그대로 열고, 돌아올 때를 위해 맞춤 목록의 조작을 남겨 둔다.
            onShowAll={() => {
              if (categoryCode === null) {
                return;
              }
              setMatchedListState(listState);
              openAllProducts(categoryCode, true);
            }}
          />
        )}
        {view === 'PRODUCT_DETAIL' && productId !== null && (
          <ProductDetailView productId={productId} onBack={() => setView('PRODUCT_LIST')} />
        )}
        {view === 'QUESTION' && (
          <QuestionView
            screens={screens}
            index={index}
            answers={answers}
            onAnswer={answer}
            onPrevious={goPrevious}
            onNext={goNext}
            waiting={waiting}
            failed={resultFailed}
          />
        )}
      </main>
      <SiteFooter />
    </>
  );
}
