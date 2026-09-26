const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

// 제품 유형·사용 목적 API 는 지원하는 항목의 코드와 이름만 준다.
export type Served = {
  code: string;
  name: string;
};

// 설명 문구와 준비 중 항목은 API 에 없어 여기 둔다. 고를 수 있는지는 서버 응답만 보고 정한다.
type Display = {
  code: string;
  name: string;
  description: string;
};

export type SelectionCard = Display & {
  available: boolean;
};

// 준비 중 항목은 와이어프레임의 "기타 기기" 대신 개발자가 함께 사는 주변 기기로 둔다 (#109).
export const CATEGORY_DISPLAY: Display[] = [
  { code: 'LAPTOP', name: '노트북', description: '고성능 모바일 워크스테이션' },
  { code: 'MONITOR', name: '모니터', description: '코드와 문서를 함께 띄우는 넓은 화면' },
  { code: 'MOUSE', name: '마우스', description: '오래 잡아도 편한 포인팅 기기' },
  { code: 'KEYBOARD', name: '키보드', description: '타이핑이 많은 작업을 위한 입력 기기' },
];

export const PURPOSE_DISPLAY: Display[] = [
  {
    code: 'BACKEND_DEVELOPMENT',
    name: '백엔드 개발',
    description: '무거운 IDE 실행, 다수의 컨테이너 구동, 원활한 컴파일 작업.',
  },
  { code: 'GAMING', name: '게이밍', description: '고성능 그래픽 작업 및 최신 게임 구동 환경.' },
  { code: 'OFFICE', name: '사무/문서 작업', description: '웹 서핑, 문서 작성, 가벼운 멀티태스킹.' },
  { code: 'VIDEO_EDITING', name: '영상 편집', description: '4K 영상 렌더링, 모션 그래픽, 전문 편집 작업.' },
  { code: 'STUDY', name: '학습용', description: '온라인 강의 시청, 과제 수행, 코딩 입문.' },
];

/**
 * 서버가 준 항목은 고를 수 있고, 표시 목록에만 있는 항목은 준비 중이다.
 * 서버가 새 항목을 지원하기 시작하면 프론트엔드를 고치지 않아도 고를 수 있게 된다.
 */
export function toCards(served: Served[], display: Display[]): SelectionCard[] {
  const known = display.map((item) => {
    const match = served.find((candidate) => candidate.code === item.code);
    return { ...item, name: match?.name ?? item.name, available: match !== undefined };
  });
  // 문구를 아직 적지 않은 새 항목도 이름만으로 고를 수 있게 붙인다.
  const unknown = served
    .filter((candidate) => !display.some((item) => item.code === candidate.code))
    .map((candidate) => ({ ...candidate, description: '', available: true }));
  // 고를 수 있는 항목을 앞에 둔다. 같은 쪽끼리는 표시 목록 순서를 지킨다.
  return [...known, ...unknown].sort((a, b) => Number(b.available) - Number(a.available));
}

async function fetchServed(path: string, subject: string): Promise<Served[]> {
  const response = await fetch(`${BASE_URL}${path}`, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`${subject}을 불러오지 못했습니다 (${response.status})`);
  }
  return response.json();
}

export function fetchCategories(): Promise<Served[]> {
  return fetchServed('/api/product-categories', '제품 유형');
}

export function fetchPurposes(categoryCode: string): Promise<Served[]> {
  return fetchServed(`/api/product-categories/${categoryCode}/usage-purposes`, '사용 목적');
}
