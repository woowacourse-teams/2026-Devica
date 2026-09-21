import type { Check, CheckResult } from './checks';
import './ConnectionCheck.css';

type State = 'pending' | 'ok' | 'fail';

const BADGE: Record<State, string> = {
  pending: '점검 중',
  ok: '성공',
  fail: '실패',
};

type Props = {
  check: Check;
  result: CheckResult | null;
};

export function CheckRow({ check, result }: Props) {
  const state: State = result === null ? 'pending' : result.ok ? 'ok' : 'fail';
  const request = result === null || result.status === null
    ? `GET ${check.path}`
    : `GET ${check.path} · ${result.status}`;

  return (
    <section className={`check check--${state}`}>
      <div className="check__head">
        <span className="check__label">{check.label}</span>
        <span className="check__badge">{BADGE[state]}</span>
        <span className="check__elapsed">{result === null ? '' : `${result.elapsedMs}ms`}</span>
      </div>
      <p className="check__request">{request}</p>
      {result !== null && <p className="check__summary">{result.summary}</p>}
      {result?.diagnosis != null && <p className="check__diagnosis">{result.diagnosis}</p>}
    </section>
  );
}
