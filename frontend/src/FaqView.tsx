import { useEffect, useState } from 'react';
import { type FaqDetail, type FaqSummary, fetchFaq, fetchFaqs } from './faq';
import './FaqView.css';

type Props = {
  categoryCode: string | null;
  purposeCode: string | null;
  backLabel: string;
  onBack: () => void;
};

/** UC-10. 목록에서 질문을 고르면 답변을 보여준다. 답변에서 뒤로 가면 목록, 목록에서 뒤로 가면 들어오기 전 화면이다. */
export function FaqView({ categoryCode, purposeCode, backLabel, onBack }: Props) {
  // 아직 받지 못한 상태(null)와 실패를 빈 목록과 구분한다.
  const [faqs, setFaqs] = useState<FaqSummary[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [slug, setSlug] = useState<string | null>(null);

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

  if (slug !== null) {
    return <FaqAnswer slug={slug} onBack={() => setSlug(null)} />;
  }

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
              <button className="faq-list__item" type="button" onClick={() => setSlug(faq.slug)}>
                <span>{faq.question}</span>
                <span aria-hidden="true">→</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function FaqAnswer({ slug, onBack }: { slug: string; onBack: () => void }) {
  const [faq, setFaq] = useState<FaqDetail | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let stale = false;
    setFaq(null);
    setFailed(false);
    fetchFaq(slug)
      .then((received) => {
        if (!stale) {
          setFaq(received);
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
  }, [slug]);

  return (
    <section className="view-panel faq-view" aria-labelledby="faq-answer-title">
      <button className="text-button view-back-button" type="button" onClick={onBack}>
        ← 질문 목록으로
      </button>
      {faq === null && !failed && <p className="section-description">답변을 불러오는 중입니다…</p>}
      {/* 없거나 비공개인 질문도 같은 실패로 온다(#72). 목록으로 돌아가도록 안내한다. */}
      {failed && (
        <p className="notice" role="alert">
          질문을 찾지 못했습니다. 질문 목록에서 다시 골라 주세요.
        </p>
      )}
      {faq !== null && (
        <>
          <h1 className="faq-view__title" id="faq-answer-title">
            {faq.question}
          </h1>
          <p className="faq-answer">{faq.answer}</p>
        </>
      )}
    </section>
  );
}
