import { useEffect, useState } from 'react';

// 화면을 오가도 이미 받은 응답은 다시 요청하지 않는다. 결과 화면에서 받은 권장 사양을 맞춤 목록이 그대로 쓴다.
// 실패한 요청은 남기지 않아 다시 시도할 수 있다.
const received = new Map<string, unknown>();

type Attempt<T> = {
  key: string;
  data: T | null;
  failed: boolean;
};

export type Fetched<T> = {
  // 아직 받지 못한 상태(null)를 빈 응답과 구분한다.
  data: T | null;
  failed: boolean;
  retry: () => void;
};

/** key 가 같으면 같은 요청이다. key 가 null 이면 요청하지 않는다. */
export function useFetched<T>(key: string | null, fetcher: () => Promise<T>): Fetched<T> {
  const [attempt, setAttempt] = useState<Attempt<T> | null>(null);
  const [round, setRound] = useState(0);

  // 키가 바뀌면 늦게 온 이전 응답은 화면에 쓰지 않는다. 받은 값은 그 키의 것으로 남긴다.
  // biome-ignore lint/correctness/useExhaustiveDependencies: fetcher 는 렌더마다 새 함수다. 요청 내용은 key 가 대표한다. round 는 다시 시도할 때만 바뀐다.
  useEffect(() => {
    if (key === null || received.has(key)) {
      return;
    }
    let stale = false;
    setAttempt({ key, data: null, failed: false });
    fetcher()
      .then((data) => {
        received.set(key, data);
        if (!stale) {
          setAttempt({ key, data, failed: false });
        }
      })
      .catch(() => {
        if (!stale) {
          setAttempt({ key, data: null, failed: true });
        }
      });
    return () => {
      stale = true;
    };
  }, [key, round]);

  const cached = key !== null && received.has(key) ? (received.get(key) as T) : null;
  const current = attempt?.key === key ? attempt : null;
  return {
    data: cached ?? current?.data ?? null,
    failed: cached === null && (current?.failed ?? false),
    retry: () => setRound((previous) => previous + 1),
  };
}
