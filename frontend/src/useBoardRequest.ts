import { useEffect, useState } from 'react';
import { BoardRequestError } from './board';

type RequestState<T> = { status: 'loading' } | { status: 'success'; data: T } | { status: 'failed'; notFound: boolean };

// 게시글은 계속 추가되므로 방문할 때마다 다시 받는다. 기존 useFetched의 영구 캐시를 사용하지 않는다.
export function useBoardRequest<T>(key: string, load: (signal: AbortSignal) => Promise<T>) {
  const [attempt, setAttempt] = useState<{ key: string; state: RequestState<T> } | null>(null);
  const [round, setRound] = useState(0);

  // biome-ignore lint/correctness/useExhaustiveDependencies: 요청에 쓰이는 모든 값은 key에 포함하며 load는 렌더마다 새 함수다.
  useEffect(() => {
    const controller = new AbortController();
    setAttempt({ key, state: { status: 'loading' } });
    load(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setAttempt({ key, state: { status: 'success', data } });
        }
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) {
          setAttempt({
            key,
            state: { status: 'failed', notFound: error instanceof BoardRequestError && error.status === 404 },
          });
        }
      });
    return () => controller.abort();
  }, [key, round]);

  const state: RequestState<T> = attempt?.key === key ? attempt.state : { status: 'loading' };
  return { state, retry: () => setRound((previous) => previous + 1) };
}
