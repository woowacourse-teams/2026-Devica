import { useEffect, useState } from 'react';
import { IntroView } from './IntroView';
import { QuestionView } from './QuestionView';
import { ResultView, type ResultOs } from './ResultView';
import { SearchLoadingView } from './SearchLoadingView';
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

type View = 'INTRO' | 'QUESTION' | 'RESULT' | 'SEARCH_LOADING';

export function App() {
  const [view, setView] = useState<View>('INTRO');
  const [questions, setQuestions] = useState<Question[]>([]);
  const [answers, setAnswers] = useState<Answers>({});
  const [index, setIndex] = useState(0);
  const [specs, setSpecs] = useState<Spec[]>([]);
  const [resultOs, setResultOs] = useState<ResultOs>('BOTH');
  // 권장 사양을 기다리는 동안 답을 바꾸면 먼저 보낸 응답이 바뀐 답을 덮는다. 기다리는 동안은 질문 화면을 잠근다.
  const [waiting, setWaiting] = useState(false);
  const [resultFailed, setResultFailed] = useState(false);

  useEffect(() => {
    fetchQuestions(PURPOSE_CODE).then(setQuestions).catch(() => setQuestions([]));
  }, []);

  const screens = resolveScreens(questions, answers);

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
      setView('RESULT');
    } catch {
      // 빈 권장안과 서버·네트워크 오류는 다르다. 답은 그대로 두고 질문 화면에 머무른다.
      setResultFailed(true);
    } finally {
      setWaiting(false);
    }
  };

  const start = () => {
    setAnswers({});
    setIndex(0);
    setResultFailed(false);
    setView('QUESTION');
  };

  return (
    <>
      <SiteHeader/>
      <main className="probe" id="main-content">
        {view === 'INTRO' && <IntroView onStart={start} canStart={screens.length > 0}/>}
        {view === 'RESULT' && <ResultView specs={specs} os={resultOs} onChangeOs={setResultOs}/>}
        {view === 'SEARCH_LOADING' && <SearchLoadingView specs={specs} onDone={() => setView('RESULT')} /* 제품 목록 화면이 생기면 그쪽으로 넘긴다 *//>}
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
