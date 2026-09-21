import { useEffect, useState } from 'react';
import { IntroView } from './IntroView';
import { QuestionView } from './QuestionView';
import { ProductDetailView } from './ProductDetailView';
import { ProductListView, initialListState, type ListMode, type ProductListState } from './ProductListView';
import { ResultView, type ResultOs } from './ResultView';
import { SiteFooter } from './SiteFooter';
import { SiteHeader } from './SiteHeader';
import {
  fetchQuestions,
  pruneHiddenAnswers,
  resolveScreens,
  toSearchParams,
  type Answers,
  type Question,
} from './questions';
import { fetchRecommendation, osValueOf, type Spec } from './recommendation';

// V1 은 백엔드 개발 목적 하나만 다룬다.
const PURPOSE_CODE = 'BACKEND_DEVELOPMENT';

// 목록 조회는 사용 목적이 아니라 제품 유형을 기준으로 한다. V1 은 노트북 하나만 다룬다.
const CATEGORY_CODE = 'LAPTOP';

type View = 'INTRO' | 'QUESTION' | 'RESULT' | 'PRODUCT_LIST' | 'PRODUCT_DETAIL';

// 전체 목록은 이 세 화면에서 열 수 있고, 닫으면 열었던 화면으로 돌아간다.
const RETURNABLE = ['INTRO', 'QUESTION', 'RESULT'] as const;

type ReturnView = (typeof RETURNABLE)[number];

const BACK_LABELS: Record<ReturnView, string> = {
  INTRO: '← 처음으로',
  QUESTION: '← 질문으로',
  RESULT: '← 권장 사양으로',
};

function isReturnable(view: View): view is ReturnView {
  return RETURNABLE.some((returnable) => returnable === view);
}

export function App() {
  const [view, setView] = useState<View>('INTRO');
  const [questions, setQuestions] = useState<Question[]>([]);
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
  const [listMode, setListMode] = useState<ListMode>('MATCHED');
  const [returnView, setReturnView] = useState<ReturnView>('RESULT');
  // 사양 대조 화면은 목록이 제품을 받아올 때까지 띄운다. 상세를 다녀올 때는 다시 띄우지 않는다.
  const [searching, setSearching] = useState(false);
  const [productId, setProductId] = useState<number | null>(null);

  useEffect(() => {
    fetchQuestions(PURPOSE_CODE).then(setQuestions).catch(() => setQuestionsFailed(true));
  }, []);

  const screens = resolveScreens(questions, answers);
  // 검색 로딩과 제품 목록은 결과 화면이 보여주고 있던 OS 만 대상으로 삼는다.
  const visibleSpecs = resultOs === 'BOTH' ? specs : specs.filter((spec) => osValueOf(spec) === resultOs);

  const answer = (code: string, values: string[]) => {
    // 답이 바뀌면 딸린 질문이 사라질 수 있다. 사라진 질문의 답은 함께 지운다.
    setAnswers((previous) => pruneHiddenAnswers(questions, { ...previous, [code]: values }));
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
    setWaiting(true);
    setResultFailed(false);
    try {
      const received = await fetchRecommendation(PURPOSE_CODE, toSearchParams(given));
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

  const showAllProducts = (from: View) => {
    setListMode('ALL');
    // 목록·상세에서 열면 그 화면은 돌아갈 곳이 될 수 없다. 이미 전체 목록이었다면 복귀 지점을 그대로 잇는다.
    setReturnView((previous) => {
      if (isReturnable(from)) {
        return from;
      }
      return listMode === 'ALL' ? previous : 'RESULT';
    });
    setListState(initialListState([]));
    setView('PRODUCT_LIST');
  };

  // 다른 페이지의 헤더에서는 링크로 넘어오므로, 여기서 제품 목록 화면을 바로 연다.
  useEffect(() => {
    if (new URLSearchParams(window.location.search).get('view') === 'products') {
      showAllProducts('INTRO');
    }
  }, []);

  const start = () => {
    setAnswers({});
    setIndex(0);
    setResultFailed(false);
    setView('QUESTION');
  };

  return (
    <>
      <SiteHeader onProductList={() => showAllProducts(view)}/>
      <main className="probe" id="main-content">
        {view === 'INTRO' && (
          <IntroView
            onStart={start}
            canStart={screens.length > 0}
            questionsFailed={questionsFailed}
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
        {view === 'PRODUCT_LIST' && (
          <ProductListView
            categoryCode={CATEGORY_CODE}
            mode={listMode}
            specs={listMode === 'ALL' ? [] : visibleSpecs}
            backLabel={listMode === 'ALL' ? BACK_LABELS[returnView] : BACK_LABELS.RESULT}
            state={listState}
            onChangeState={setListState}
            searching={searching}
            onSearched={() => setSearching(false)}
            onBack={() => setView(listMode === 'ALL' ? returnView : 'RESULT')}
            onDetail={(id) => {
              setProductId(id);
              setView('PRODUCT_DETAIL');
            }}
            onShowAll={() => showAllProducts(view)}
          />
        )}
        {view === 'PRODUCT_DETAIL' && productId !== null && (
          <ProductDetailView productId={productId} onBack={() => setView('PRODUCT_LIST')}/>
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
      <SiteFooter/>
    </>
  );
}
