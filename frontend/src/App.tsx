import { useEffect, useState } from 'react';
import { IntroView } from './IntroView';
import { QuestionView } from './QuestionView';
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

type View = 'INTRO' | 'QUESTION' | 'RESULT';

export function App() {
  const [view, setView] = useState<View>('INTRO');
  const [questions, setQuestions] = useState<Question[]>([]);
  const [answers, setAnswers] = useState<Answers>({});
  const [index, setIndex] = useState(0);
  const [specs, setSpecs] = useState<Spec[]>([]);
  const [resultOs, setResultOs] = useState<ResultOs>('BOTH');

  useEffect(() => {
    fetchQuestions(PURPOSE_CODE).then(setQuestions).catch(() => setQuestions([]));
  }, []);

  const screens = resolveScreens(questions, answers);

  const answer = (code: string, values: string[]) => {
    // 답이 바뀌면 딸린 질문이 사라질 수 있다. 사라진 질문의 답은 함께 지운다.
    setAnswers((previous) => pruneHiddenAnswers(questions, { ...previous, [code]: values }));
  };

  const goPrevious = () => {
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
    const received = await fetchRecommendation(PURPOSE_CODE, toSearchParams(given)).catch(() => []);
    setSpecs(received);
    // 권장안이 한쪽 OS 만 나오면 그 OS 를 보여준다.
    setResultOs(received.length === 1 ? (osValueOf(received[0]) as ResultOs) : 'BOTH');
    setView('RESULT');
  };

  const start = () => {
    setAnswers({});
    setIndex(0);
    setView('QUESTION');
  };

  return (
    <>
      <SiteHeader/>
      <main className="probe" id="main-content">
        {view === 'INTRO' && <IntroView onStart={start} canStart={screens.length > 0}/>}
        {view === 'RESULT' && <ResultView specs={specs} os={resultOs} onChangeOs={setResultOs}/>}
        {view === 'QUESTION' && (
          <QuestionView
            screens={screens}
            index={index}
            answers={answers}
            onAnswer={answer}
            onPrevious={goPrevious}
            onNext={goNext}
          />
        )}
      </main>
      <SiteFooter/>
    </>
  );
}
