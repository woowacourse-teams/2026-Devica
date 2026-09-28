import { type SyntheticEvent, useEffect, useState } from 'react';
import { type FaqSummary, fetchFaq, fetchFaqs } from './faq';
import './FaqView.css';

type Props = {
  categoryCode: string | null;
  purposeCode: string | null;
  backLabel: string;
  onBack: () => void;
};

/** UC-10. 질문을 펼치면 그 자리에서 답변을 보여준다. 답이 짧고 여러 개를 한 번에 훑는 경우가 많아 화면을 옮기지 않는다. */
export function FaqView({ categoryCode, purposeCode, backLabel, onBack }: Props) {
  // 아직 받지 못한 상태(null)와 실패를 빈 목록과 구분한다.
  const [faqs, setFaqs] = useState<FaqSummary[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let stale = false;
    setFaqs(null);
    setFailed(false);
    fetchFaqs(categoryCode, purposeCode)
      .then((received) => {
        if (!stale) {
          setFaqs(received);
        }
      })
      .catch(() => {
        if (!stale) {
          setFailed(true);
        }
      });
    return () => {
      stale = true;
    };
  }, [categoryCode, purposeCode]);

  return (
    <section className="view-panel faq-view" aria-labelledby="faq-title">
      <button className="text-button view-back-button" type="button" onClick={onBack}>
        {backLabel}
      </button>
      <h1 className="faq-view__title" id="faq-title">
        자주 묻는 질문
      </h1>
      <p className="section-description">
        {purposeCode === null
          ? '서비스 이용에 관해 자주 묻는 질문입니다.'
          : '고른 사용 목적에 관해 자주 묻는 질문입니다.'}
      </p>

      {faqs === null && !failed && <p className="section-description">질문을 불러오는 중입니다…</p>}
      {failed && (
        <p className="notice" role="alert">
          질문을 불러오지 못했습니다. 잠시 뒤에 새로고침해 주세요.
        </p>
      )}
      {faqs !== null && faqs.length === 0 && <p className="section-description">아직 등록된 질문이 없습니다.</p>}
      {faqs !== null && faqs.length > 0 && (
        <ul className="faq-list">
          {faqs.map((faq) => (
            <li key={faq.slug}>
              <FaqItem faq={faq} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// 여러 개를 함께 펼쳐 둘 수 있다. 펼침 상태는 브라우저의 details 가 관리한다.
function FaqItem({ faq }: { faq: FaqSummary }) {
  // 목록 API 에는 답이 없다. 처음 펼칠 때 받고, 접었다 다시 펼쳐도 다시 받지 않는다.
  const [answer, setAnswer] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);
  const [requested, setRequested] = useState(false);

  const load = (event: SyntheticEvent<HTMLDetailsElement>) => {
    if (!event.currentTarget.open || requested) {
      return;
    }
    setRequested(true);
    setFailed(false);
    fetchFaq(faq.slug)
      .then((received) => setAnswer(received.answer))
      .catch(() => {
        // 실패하면 다시 펼칠 때 다시 받는다.
        setFailed(true);
        setRequested(false);
      });
  };

  return (
    <details className="faq-item" onToggle={load}>
      <summary className="faq-item__question">
        <span>{faq.question}</span>
        <span className="faq-item__marker" aria-hidden="true">
          ▾
        </span>
      </summary>
      <div className="faq-item__answer">
        {answer === null && !failed && <p className="section-description">답변을 불러오는 중입니다…</p>}
        {/* 없거나 비공개인 질문도 같은 실패로 온다(#72). */}
        {failed && (
          <p className="notice" role="alert">
            답변을 불러오지 못했습니다. 접었다가 다시 펼쳐 주세요.
          </p>
        )}
        {answer !== null && <p className="faq-answer">{answer}</p>}
      </div>
    </details>
  );
}
