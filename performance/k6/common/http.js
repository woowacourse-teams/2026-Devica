// performance/k6/common/http.js
import http from 'k6/http';
import { check } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080')
  .replace(/\/+$/, '');

export function url(path) {
  return `${baseUrl}${path}`;
}

// SLO의 오류는 5xx와 타임아웃(5초)이다. 4xx는 http_req_failed에 넣지 않고 checkResponse에서 걸러 낸다
const notServerError = http.expectedStatuses({ min: 200, max: 499 });

export function params(tags) {
  return {
    tags,
    timeout: '5s',
    redirects: 0,
    responseCallback: notServerError,
  };
}

export function get(path, tags) {
  return http.get(url(path), params(tags));
}

export function jsonBody(response) {
  try {
    return response.json();
  } catch {
    return null;
  }
}

// 결과 유효성 검사. 4xx는 스크립트 버그나 API 계약 변경이라 무효로 만든다.
// 5xx와 타임아웃은 SLO 오류율로 판정하므로 여기서 실패로 세지 않고, 형식 검사도 200 응답에만 한다
export function checkResponse(response, label, isValidBody) {
  check(response, {
    [`${label} 4xx가 아니다`]: (res) => res.status < 400 || res.status >= 500,
  });

  if (response.status === 200) {
    check(response, {
      [`${label} 응답 형식이 올바르다`]: (res) => isValidBody(jsonBody(res)),
    });
  }
}
