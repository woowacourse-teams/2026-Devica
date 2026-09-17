import { useEffect, useState } from 'react';
import { IntroView } from './IntroView';
import { QuestionView } from './QuestionView';
import { SiteFooter } from './SiteFooter';
import { SiteHeader } from './SiteHeader';
import {
  fetchQuestions,
  pruneHiddenAnswers,
  resolveScreens,
  type Answers,
  type Question,
} from './questions';

// V1 은 백엔드 개발 목적 하나만 다룬다.
const PURPOSE_CODE = 'BACKEND_DEVELOPMENT';

type View = 'INTRO' | 'QUESTION';

export function App() {
  const [view, setView] = useState<View>('INTRO');
  const [questions, setQuestions] = useState<Question[]>([]);
  const [answers, setAnswers] = useState<Answers>({});
  const [index, setIndex] = useState(0);

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
    }
    // 마지막 화면 다음은 권장 사양 결과다. 그 화면은 아직 없다.
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
