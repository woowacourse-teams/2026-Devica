// 최종 시안 v7의 문구와 실제 서비스 캡처 범위. 원본은 public/landing 에 둔다.
export const LANDING_GUIDES = [
  {
    id: 'find-specs',
    eyebrow: '내 목적에 맞는 제품이 궁금하다면',
    title: '제품 추천받기',
    description: '제품과 사용 목적을 선택하고, 내 환경에 맞는 제품 추천 결과를 확인해요.',
    linkText: '제품 추천 시작',
    href: '/category',
    cards: [
      {
        step: '01 / 제품 선택',
        title: '어떤 제품이\n필요한가요?',
        description: '찾고 있는 제품을 골라 주세요.\n지금은 노트북으로 시작할 수 있어요.',
        note: '제품을 선택하면 사용 목적 선택으로 이어져요.',
        capture: {
          src: '/landing/actual-category.jpg',
          alt: '실제 제품 선택 화면',
          ratio: '1076/340',
          style: {
            width: '118.95911%',
            left: '-9.47955%',
            top: '-60.58824%',
          },
        },
      },
      {
        step: '02 / 사용 목적 선택',
        title: '어떤 용도로\n사용하시나요?',
        description: '제품을 주로 사용할 목적을 선택해요.\n지금은 백엔드 개발을 지원해요.',
        note: '선택한 목적에 맞는 질문으로 이어져요.',
        capture: {
          src: '/landing/actual-purpose.jpg',
          alt: '실제 사용 목적 선택 화면',
          ratio: '1076/340',
          style: {
            width: '118.95911%',
            left: '-9.47955%',
            top: '-60.58824%',
          },
        },
      },
      {
        step: '03 / 내 환경 알려주기',
        title: '내 환경을\n알려주세요.',
        description: '사용 목적과 개발 환경에 대한\n몇 가지 질문에 답해요.',
        note: '사용 언어 · 개발 도구 · 작업 환경',
        capture: {
          src: '/landing/actual-question.jpg',
          alt: '실제 사용 언어 선택 화면',
          ratio: '1057/493',
          style: {
            width: '119.67833%',
            left: '-9.64995%',
            top: '-44.42191%',
          },
        },
      },
      {
        step: '04 / 제품 추천 결과 확인',
        title: '필요한 것을,\n이유와 함께.',
        description: '추천 이유를 읽고,\n내 환경에 맞는 추천 결과를 확인해요.',
        note: 'CPU · RAM · SSD와 추천 근거',
        capture: {
          src: '/landing/actual-result-mac.jpg',
          alt: '실제 Mac 제품 추천 결과와 추천 이유',
          ratio: '1025/534',
          style: {
            width: '123.41463%',
            left: '-11.70732%',
            top: '-111.79775%',
          },
        },
      },
      {
        step: '05 / 제품 확인',
        title: '추천 결과에 맞는\n제품을 살펴봐요.',
        description: '제품 추천 결과에 맞는\n제품을 살펴보세요.',
        note: '더 궁금한 점이 있다면 질문 게시판에서도 나눠요.',
        capture: {
          src: '/landing/actual-products.jpg',
          alt: '제품 추천 결과에 맞는 실제 제품 목록',
          ratio: '1044/389',
          style: {
            width: '121.16858%',
            left: '-9.86590%',
            top: '-100.77121%',
          },
        },
      },
    ],
  },
  {
    id: 'use-board',
    eyebrow: '나와 비슷한 고민이 궁금하다면',
    title: '질문 게시판 이용',
    description: '제품 추천 전에도, 추천받은 뒤에도.\n궁금한 질문과 다른 개발자들의 경험을 바로 둘러볼 수 있어요.',
    linkText: '질문 게시판 둘러보기',
    href: '/board',
    cards: [
      {
        step: '01 / 비슷한 고민 둘러보기',
        title: '누군가 나와 같은\n고민을 했을까요?',
        description: '제품 추천을 받기 전에도 바로 들어와,\n제품과 사용 목적에 맞는 질문을 둘러봐요.',
        note: '제품 추천을 받지 않아도 바로 이용할 수 있어요.',
        capture: {
          src: '/landing/actual-board.jpg',
          alt: '실제 질문 게시판의 제품 및 사용 목적 분류와 질문 목록',
          ratio: '1048/612',
          style: {
            width: '120.70611%',
            left: '-9.92366%',
            top: '-32.67974%',
          },
        },
      },
      {
        step: '02 / 질문과 답변 읽기',
        title: '다른 개발자의\n경험을 읽어보세요.',
        description: '나와 비슷한 환경의 질문을 열어,\n다른 개발자들이 남긴 답변을 살펴봐요.',
        note: '내가 궁금했던 점과 비교하며 읽어보세요.',
        capture: {
          src: '/landing/actual-board-detail.jpg',
          alt: '실제 원격 개발 질문과 개발자들의 댓글',
          ratio: '902/623',
          style: {
            width: '140.24390%',
            left: '-20.28825%',
            top: '-47.83307%',
          },
        },
      },
      {
        step: '03 / 직접 질문하기',
        title: '남은 궁금증은\n직접 나눠요.',
        description: '비슷한 질문을 찾지 못했거나,\n제품 추천 결과가 고민된다면 질문해 보세요.',
        note: '사용 환경과 궁금한 내용을 함께 적어주세요.',
        capture: {
          src: '/landing/actual-board-write.jpg',
          alt: '실제 질문 작성 화면',
          ratio: '902/729',
          style: {
            width: '140.24390%',
            left: '-20.17738%',
            top: '-28.94376%',
          },
        },
      },
    ],
  },
] as const;

export type LandingGuideContent = (typeof LANDING_GUIDES)[number];
