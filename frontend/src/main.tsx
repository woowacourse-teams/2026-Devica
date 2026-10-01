import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './tokens.css';
import './index.css';
import './shared.css';

// 목록은 제품을 받은 뒤에 그려져, 브라우저가 스크롤을 되돌릴 때는 페이지가 아직 짧다. 앱이 직접 되돌린다.
window.history.scrollRestoration = 'manual';

// 지표 수집 라이브러리는 앱만큼 무거워 따로 받는다. 키가 없는 개발·미리보기 환경에서는 받지도 않는다.
// 받는 사이 앱이 주소를 바꾸면 첫 주소의 UTM 을 놓친다. 접속하자마자 주소를 바꾸는 곳은 예전 링크를 옮기는 LegacyRedirect 뿐이다.
const posthogKey = import.meta.env.VITE_POSTHOG_KEY;
if (posthogKey) {
  import('posthog-js').then(({ default: posthog }) => {
    posthog.init(posthogKey, {
      api_host: 'https://us.i.posthog.com',
      // 주소만 바뀌는 SPA 라 화면 이동마다 $pageview 를 보내게 한다.
      capture_pageview: 'history_change',
      // 탭을 닫거나 사이트를 떠날 때 $pageleave 를 보낸다. 앱 안에서 이동할 때의 체류 시간은 다음 $pageview 에 담긴다.
      capture_pageleave: true,
      autocapture: false,
    });
  });
}

const root = document.getElementById('root');
if (root === null) {
  throw new Error('root 엘리먼트를 찾지 못했습니다.');
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
