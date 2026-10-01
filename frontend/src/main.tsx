import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { initAnalytics } from './analytics';
import './tokens.css';
import './index.css';
import './shared.css';

// 목록은 제품을 받은 뒤에 그려져, 브라우저가 스크롤을 되돌릴 때는 페이지가 아직 짧다. 앱이 직접 되돌린다.
window.history.scrollRestoration = 'manual';

initAnalytics();

const root = document.getElementById('root');
if (root === null) {
  throw new Error('root 엘리먼트를 찾지 못했습니다.');
}

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
