import { useCallback, useEffect, useRef, useState } from 'react';

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

export type Page<T> = {
  content: T[];
  hasNext: boolean;
};

type Pages<T> = {
  items: T[];
  hasNext: boolean;
  next: number;
};

// 같은 페이지를 두 번 요청하지 않는다. "키#페이지" 로 적는다.
const loading = new Set<string>();
// 실패도 key 마다 적는다. 하나만 두면 늦게 실패한 이전 조건이 지금 조건의 실패를 덮는다.
const failedKeys = new Set<string>();

export type Paged<T> = {
  // 첫 페이지를 받기 전(null)을 빈 목록과 구분한다.
  data: T[] | null;
  hasNext: boolean;
  loading: boolean;
  failed: boolean;
  // 다음 페이지를 받는다. 실패한 뒤에 부르면 그 페이지를 다시 받는다.
  more: () => void;
};

/**
 * 페이지로 나뉜 목록을 이어 받는다. 받은 페이지는 key 마다 남겨, 화면을 다녀와도 다시 요청하지 않는다.
 * 응답은 요청한 key 의 자리에만 쌓인다. 그래서 조건이 바뀐 뒤 늦게 온 응답이 새 목록에 섞이지 않는다.
 */
export function usePaged<T extends { id: number }>(
  key: string | null,
  fetchPage: (page: number) => Promise<Page<T>>,
): Paged<T> {
  const [, rerender] = useState(0);
  // fetchPage 는 렌더마다 새 함수다. more 를 늘 같은 함수로 두려고 마지막 것을 따로 든다.
  const latest = useRef(fetchPage);
  latest.current = fetchPage;

  const more = useCallback(() => {
    if (key === null) {
      return;
    }
    const entry = received.get(key) as Pages<T> | undefined;
    const page = entry?.next ?? 0;
    const flight = `${key}#${page}`;
    if (loading.has(flight) || entry?.hasNext === false) {
      return;
    }
    loading.add(flight);
    failedKeys.delete(key);
    rerender((count) => count + 1);
    latest
      .current(page)
      .then(({ content, hasNext }) => {
        const previous = (received.get(key) as Pages<T> | undefined)?.items ?? [];
        // 페이지 사이에 순서가 바뀌면 같은 제품이 다시 올 수 있다.
        const seen = new Set(previous.map((item) => item.id));
        received.set(key, {
          items: [...previous, ...content.filter((item) => !seen.has(item.id))],
          hasNext,
          next: page + 1,
        });
      })
      .catch(() => failedKeys.add(key))
      .finally(() => {
        loading.delete(flight);
        rerender((count) => count + 1);
      });
  }, [key]);

  useEffect(() => {
    if (key !== null && !received.has(key)) {
      more();
    }
  }, [key, more]);

  const entry = key === null ? undefined : (received.get(key) as Pages<T> | undefined);
  return {
    data: entry?.items ?? null,
    hasNext: entry?.hasNext ?? false,
    loading: key !== null && loading.has(`${key}#${entry?.next ?? 0}`),
    failed: key !== null && failedKeys.has(key),
    more,
  };
}
