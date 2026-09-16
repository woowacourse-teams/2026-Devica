import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// 배포 환경과 같은 동일 오리진을 개발 서버에서도 만든다.
// 배포할 때는 프론트엔드를 서빙하는 쪽이 같은 두 경로를 백엔드로 넘겨야 한다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
});
