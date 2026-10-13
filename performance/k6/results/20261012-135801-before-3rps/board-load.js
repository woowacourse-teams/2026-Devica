import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { getBoardPosts } from './requests.js';

const test = __ENV.BOARD_TEST ?? 'load';
const rps = Number(__ENV.RPS ?? 3);
const vus = Number(__ENV.VUS ?? 20);
const pageSize = 20;
const page = Number(__ENV.POST_PAGE ?? (test === 'deep' ? 1000 : 0));

if (test !== 'load' && test !== 'deep') {
  throw new Error('BOARD_TEST는 load 또는 deep이어야 합니다.');
}

if (!Number.isInteger(rps) || rps < 1) {
  throw new Error('RPS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(vus) || vus < 1) {
  throw new Error('VUS는 1 이상의 정수여야 합니다.');
}

if (!Number.isInteger(page) || page < 0 || page * pageSize > 2147483647) {
  throw new Error('POST_PAGE는 조회 가능한 범위의 0 이상 정수여야 합니다.');
}

if (test === 'deep' && page === 0) {
  throw new Error('깊은 페이지 테스트의 POST_PAGE는 1 이상이어야 합니다.');
}

// 시작률과 정상 응답 처리율을 구분한다. http_reqs에는 오류·타임아웃도 포함된다.
const started = new Counter('board_requests_started');
const succeeded = new Counter('board_requests_succeeded');

export const options = {
  tags: {
    board_test: test,
    post_page: String(page),
    post_size: String(pageSize),
    target_rps: String(rps),
    run_label: __ENV.RUN_LABEL || 'unlabeled',
  },
  scenarios: {
    board_posts: {
      executor: 'constant-arrival-rate',
      rate: rps,
      timeUnit: '1s',
      duration: __ENV.DURATION || '10m',
      preAllocatedVUs: vus,
      gracefulStop: '10s',
    },
  },

  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
    dropped_iterations: ['count==0'],
  },

  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  started.add(1);
  const response = getBoardPosts(page, pageSize);
  const valid = check(response, {
    '게시글 목록이 요청한 페이지에서 1건 이상, 요청한 크기 이하로 반환된다': (res) => {
      try {
        const body = res.json();
        // 실제 게시글 조회 성능을 측정하기 위해 빈 페이지는 성공으로 세지 않는다.
        return res.status === 200 &&
          Array.isArray(body?.content) &&
          body.content.length > 0 && body.content.length <= pageSize &&
          body.page === page && body.size === pageSize &&
          typeof body.hasNext === 'boolean';
      } catch {
        return false;
      }
    },
  });
  // 실패만 발생한 실행도 정상 응답 수 0을 결과에 남긴다.
  succeeded.add(valid ? 1 : 0);
}
