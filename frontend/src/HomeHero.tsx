import { HomeRecentQuestions } from './HomeRecentQuestions';
import './HomeHero.css';

type Props = {
  onStart: () => void;
};

export function HomeHero({ onStart }: Props) {
  return (
    <section className="home-hero" aria-labelledby="home-hero-title">
      <div className="home-hero__heading">
        <h1 id="home-hero-title">개발 장비 고민, 함께 풀어봐요</h1>
        <p>추천받은 사양이 고민되면, 다른 개발자와 질문을 나눠 보세요.</p>
      </div>
      <div className="home-hero__cards">
        <section className="home-hero__intro" aria-labelledby="home-intro-title">
          <p className="home-hero__label">DEVICA 서비스 소개</p>
          <h2 id="home-intro-title">
            내 사용 목적에 맞게,
            <br />
            구매 고민은 더 가볍게.
          </h2>
          <p className="home-hero__description">
            전자제품 정보는 많은데, 무엇을 골라야 할지 막막한가요? <br />
            DEVICA가 사용 목적에 맞는 사양을 정리해 <br />
            어려운 구매 고민을 덜어드려요.
          </p>
        </section>
        <section className="home-hero__finder" aria-labelledby="home-finder-title">
          <h2 id="home-finder-title">내게 맞는 사양은?</h2>
          <p className="home-hero__finder-description">
            사용 환경에 맞는
            <br />
            CPU · RAM · SSD를 찾아보세요.
          </p>
          <p className="home-hero__meta">약 2분 · 질문 9~10개</p>
          <button className="button button--primary home-hero__start" type="button" onClick={onStart}>
            내 사양 찾기 시작 <span aria-hidden="true">→</span>
          </button>
        </section>
      </div>
      <HomeRecentQuestions />
    </section>
  );
}
