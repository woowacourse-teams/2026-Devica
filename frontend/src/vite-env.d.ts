/// <reference types="vite/client" />

interface ImportMetaEnv {
  // 비워 두면 상대 경로로 호출한다. 프론트엔드와 백엔드를 다른 오리진에 두게 될 때만 채운다.
  readonly VITE_API_BASE_URL?: string;
  // 배포 구성이 정해지면 빌드 시점에 주입한다.
  readonly VITE_BUILD_SHA?: string;
  // 운영 배포에만 넣는다. 비어 있으면 지표를 수집하지 않는다.
  readonly VITE_POSTHOG_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
