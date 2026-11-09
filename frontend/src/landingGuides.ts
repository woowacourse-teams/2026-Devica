export const LANDING_GUIDES = [
  {
    id: 'find-specs',
    eyebrow: '내 목적에 맞는 제품이 궁금하다면',
    title: '제품 추천받기',
    linkText: '제품 추천 시작',
    href: '/category',
    steps: [
      { title: '제품 선택', description: '찾고 있는 제품을 골라요. 지금은 노트북으로 시작할 수 있어요.' },
      { title: '사용 목적 선택', description: '백엔드·프론트엔드 개발 등 직군별로 고를 수 있어요.' },
      { title: '내 환경 알려주기', description: '사용 언어 · 개발 도구 · 작업 환경에 답해요.' },
      { title: '제품 추천 결과 확인', description: 'CPU · RAM · SSD와 추천 근거를 함께 읽어요.' },
      { title: '제품 확인', description: '추천 결과에 맞는 제품을 살펴보고, 궁금하면 게시판에서 나눠요.' },
    ],
  },
  {
    id: 'use-board',
    eyebrow: '나와 비슷한 고민이 궁금하다면',
    title: '질문 게시판 이용',
    linkText: '질문 게시판 둘러보기',
    href: '/board',
    steps: [
      {
        title: '비슷한 고민 둘러보기',
        description: '제품 추천을 받기 전에도 바로 들어와, 제품과 사용 목적에 맞는 질문을 둘러봐요.',
      },
      {
        title: '질문과 답변 읽기',
        description: '나와 비슷한 환경의 질문을 열어, 다른 개발자들이 남긴 답변을 살펴봐요.',
      },
      {
        title: '직접 질문하기',
        description: '비슷한 질문을 찾지 못했거나, 제품 추천 결과가 고민된다면 질문해 보세요.',
      },
    ],
  },
] as const;

export type LandingGuideContent = (typeof LANDING_GUIDES)[number];
