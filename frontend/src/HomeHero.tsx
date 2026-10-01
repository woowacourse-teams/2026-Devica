import { useFetched } from './fetched';
import { EMPTY_CONDITION, fetchProductsFor } from './products';
import { ResultPreview } from './ResultPreview';
import './HomeHero.css';

// V1 이 지원하는 제품은 노트북 하나다. 고르기 전 화면이라 노트북 수를 보여준다.
const SAMPLE_CATEGORY = 'LAPTOP';

const WORRIES = ['RAM 16GB면 충분할까?', '컨테이너 여러 개 띄워도 버틸까?', '맥이랑 윈도우 중 뭘 사야 할까?'];

const STEPS = [
  { index: '01', title: '질문에 답하기', meta: '지금 쓰는 장비와 작업 방식' },
  { index: '02', title: '권장 사양 확인', meta: 'CPU · RAM · SSD 와 그 근거' },
  { index: '03', title: '맞는 제품 보기', meta: '사양을 만족하는 노트북만' },
];

type Props = {
  onStart: () => void;
};

/** 첫 화면의 서비스 소개. 무엇을 해 주는지와 어떤 결과를 받는지 먼저 보여주고, 제품 선택 화면으로 넘긴다. */
export function HomeHero({ onStart }: Props) {
  const products = useFetched(`home:products:${SAMPLE_CATEGORY}`, () =>
    fetchProductsFor(SAMPLE_CATEGORY, [], 'PRICE_ASC', EMPTY_CONDITION),
  );

  // 받지 못한 수치는 칸째로 뺀다. 소개 화면이라 오류를 따로 알리지 않는다.
  const stats = [
    { value: '약 2분', label: '질문 9~10개' },
    ...(products.data === null ? [] : [{ value: `${products.data.length}종`, label: '비교할 수 있는 노트북' }]),
    { value: '2가지', label: 'Mac · Windows 권장안' },
  ];

  return (
    <section className="view-panel home-hero" aria-labelledby="home-hero-title">
      <p className="eyebrow">백엔드 개발자를 위한 노트북 사양 찾기</p>
      <h1 id="home-hero-title">
        내 개발 환경에 맞는
        <br />
        노트북 사양을 찾아보세요
      </h1>
      <p className="home-hero__description">
        몇 가지 질문에 답하면 필요한 CPU · RAM · SSD 를 정리하고, 그 사양을 만족하는 노트북만 골라 보여드려요.
      </p>
      <button className="button button--primary home-hero__start" type="button" onClick={onStart}>
        내 사양 찾기 시작
      </button>

      <dl className="home-hero__stats">
        {stats.map((stat) => (
          <div className="home-hero__stat" key={stat.label}>
            <dt>{stat.label}</dt>
            <dd>{stat.value}</dd>
          </div>
        ))}
      </dl>

      <section className="bordered-panel home-hero__worries" aria-labelledby="home-hero-worries">
        <h2 className="home-hero__heading" id="home-hero-worries">
          이런 고민, 해 보셨나요?
        </h2>
        <ul>
          {WORRIES.map((worry) => (
            <li key={worry}>{worry}</li>
          ))}
        </ul>
      </section>

      <ResultPreview />

      <section aria-labelledby="home-hero-steps">
        <h2 className="home-hero__heading" id="home-hero-steps">
          이렇게 진행됩니다
        </h2>
        <ol className="home-hero__steps">
          {STEPS.map((step) => (
            <li key={step.index}>
              <span className="home-hero__step-index">{step.index}</span>
              <span className="home-hero__step-title">{step.title}</span>
              <span className="home-hero__step-meta">{step.meta}</span>
            </li>
          ))}
        </ol>
      </section>

      <button className="button button--primary button--wide" type="button" onClick={onStart}>
        내 사양 찾기 시작
      </button>
    </section>
  );
}
