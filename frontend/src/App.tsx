import { useCallback, useEffect, useState } from 'react';
import { CheckRow } from './CheckRow';
import { CHECKS, runCheck, type CheckResult } from './checks';

const BUILD_SHA = import.meta.env.VITE_BUILD_SHA ?? 'unknown';

export function App() {
  const [results, setResults] = useState<(CheckResult | null)[]>(() => CHECKS.map(() => null));
  const [running, setRunning] = useState(false);

  const run = useCallback(async () => {
    setRunning(true);
    setResults(CHECKS.map(() => null));
    for (const [index, check] of CHECKS.entries()) {
      const result = await runCheck(check);
      setResults((previous) => previous.map((item, order) => (order === index ? result : item)));
    }
    setRunning(false);
  }, []);

  useEffect(() => {
    void run();
  }, [run]);

  return (
    <main className="panel">
      <header className="panel__head">
        <h1 className="panel__title">DEVICA 연동 점검</h1>
        <span className="panel__build">BUILD {BUILD_SHA}</span>
      </header>
      <p className="panel__note">이 화면이 보이면 프론트엔드 배포는 정상입니다.</p>
      {CHECKS.map((check, index) => (
        <CheckRow key={check.path} check={check} result={results[index] ?? null}/>
      ))}
      <div className="panel__foot">
        <button className="button" type="button" onClick={() => void run()} disabled={running}>
          다시 실행
        </button>
      </div>
    </main>
  );
}
