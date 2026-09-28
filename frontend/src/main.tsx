import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './tokens.css';
import './index.css';
import './shared.css';

const root = document.getElementById('root');
if (root === null) {
  throw new Error('root 엘리먼트를 찾지 못했습니다.');
}

createRoot(root).render(
  <StrictMode>
    <App/>
  </StrictMode>,
);
