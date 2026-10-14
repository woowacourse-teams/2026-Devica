import exec from 'k6/execution';
import { getPosts, PAGE_SIZE } from './requests.js';

// 방문 흐름에서 발생하는 목록 요청만 추린 가정이다. 상세·댓글 조회는 포함하지 않는다.
// 게시판 진입 → 글 읽기 → 목록으로 복귀 → 다음 페이지: 목록 요청은 0, 0, 1의 3건.
const LIST_REQUESTS = [
  { page: 0, action: 'enter' },
  { page: 0, action: 'return' },
  { page: 1, action: 'next' },
];

// 미지정이면 방문 흐름의 요청 비율을 사용한다. 지정하면 그 페이지만 조회하는 진단 테스트다.
const rawPage = __ENV.POST_PAGE;
const fixedPage = rawPage === undefined ? null : Number(rawPage);
const maxPage = Math.floor(2147483647 / PAGE_SIZE);
if (rawPage !== undefined && (
  !/^\d+$/.test(rawPage) || !Number.isSafeInteger(fixedPage) || fixedPage > maxPage
)) {
  throw new Error(`POST_PAGE는 0 이상 ${maxPage} 이하의 정수여야 합니다.`);
}

export function runListRequest() {
  // VU별 반복 횟수 대신 전체 시나리오의 반복 번호를 써서 모든 회차에 같은 비율을 적용한다.
  const request = fixedPage === null
    ? LIST_REQUESTS[exec.scenario.iterationInTest % LIST_REQUESTS.length]
    : { page: fixedPage, action: 'fixed' };

  // 반복 한 번에 요청 한 건: 초당 2회 반복이면 목록 API 전체에 2 RPS다.
  // 이는 여러 방문자의 요청을 합친 부하이며, 한 방문자의 클릭·대기 시간을 재현하지 않는다.
  getPosts(request.page, { action: request.action });
}
