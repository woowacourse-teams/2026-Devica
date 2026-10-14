import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080')
  .replace(/\/+$/, '');

export function url(path) {
  return `${baseUrl}${path}`;
}

// SLO 오류율에는 5xx와 타임아웃만 넣는다
const notServerError = http.expectedStatuses({ min: 200, max: 499 });

export function params(tags) {
  return {
    tags,
    timeout: '5s',
    redirects: 0,
    responseCallback: notServerError,
  };
}

export function jsonBody(response) {
  try {
    return response.json();
  } catch {
    return null;
  }
}

// 3xx·4xx는 의도와 다른 요청이라 무효로 만든다. 5xx와 타임아웃(상태 0)은 오류율로 판정하므로 여기서 실패로 세지 않는다
export function checkResponse(response, label, isValidBody) {
  check(response, {
    [`${label} 3xx·4xx가 아니다`]: (res) => res.status === 200 || res.status === 0 || res.status >= 500,
  });

  if (response.status === 200) {
    check(response, {
      [`${label} 응답 형식이 올바르다`]: (res) => isValidBody(jsonBody(res)),
    });
  }
}
