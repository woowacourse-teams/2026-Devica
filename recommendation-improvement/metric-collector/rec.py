#!/usr/bin/env python3
# Metric Collector 예약 작업의 스크립트. 함께 쓰는 명령(rec_common.py 머리 주석)에 아래 전용 명령을 더한다.
# rec_common.py 와 같은 디렉터리에 두고 실행한다.
from rec_common import *  # noqa: F401,F403
from rec_common import COMMANDS, json, sys

#   rec.py signals SESSIONS_FILE PROD_BASE_URL LOOKBACK_DAYS   신호를 같은 신호끼리 묶은 결과(제목·본문 포함)
#   rec.py issue   BODY_FILE                                    열린 지표 피드백의 신호 키와 배포를 기다리는 걸린 규칙
# SESSIONS_FILE 은 prompt.md 의 HogQL 결과에서 세션마다 views 한 칸을 꺼내 모은 JSON 배열이다.
# view = [유닉스 초, 이벤트, 경로, purpose, category, "질문.선택지&질문.선택지…"]
from collections import Counter
from datetime import timedelta

MAIN = os.environ.get('MAIN_REF', 'origin/main')
DEVELOP = os.environ.get('DEVELOP_REF', 'origin/develop')
RESULT_EVENT = 'recommendation_result_viewed'
PLAN_LABEL = {'MAC': 'Mac', 'WINDOWS': 'Windows', 'BOTH': '둘 다'}
SUBJECT = {'REQUIRED_CPU': 'CPU 가', 'MEMORY': '메모리가', 'STORAGE': '저장 공간이'}


def problem(item, direction):
    return f"{SUBJECT[item]} {'과해요' if direction == 'DOWN' else '부족해요'}"


def parse_view(v):
    ts, event, path, purpose, category, pairs = v
    answers = {}
    for pair in urllib.parse.unquote(pairs or '').split('&'):
        if '.' in pair:
            q, o = pair.split('.', 1)
            answers.setdefault(q, []).append(o)
    return {'ts': int(float(ts)), 'event': event, 'path': (path or '').rstrip('/') or '/',
            'purpose': urllib.parse.unquote(purpose or '') or None,
            'category': urllib.parse.unquote(category or '') or None,
            'answers': {k: sorted(set(o)) for k, o in sorted(answers.items())}}


def load_sessions(path):
    data = json.loads(read(path))
    if isinstance(data, dict):
        data = data.get('results') or []
    out = []
    for row in data:
        # 쿼리 결과 행([views])을 그대로 넣었어도 받는다
        if len(row) == 1 and isinstance(row[0], list) and row[0] and isinstance(row[0][0], list):
            row = row[0]
        out.append(sorted((parse_view(v) for v in row), key=lambda v: v['ts']))
    return out


def iso(ts):
    return datetime.fromtimestamp(ts, timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def changed_lines(first, last):
    lines = []
    for q in sorted(set(first) | set(last)):
        a, b = set(first.get(q, [])), set(last.get(q, []))
        if a != b:
            lines.append(f"{q}: {', '.join(sorted(a - b)) or '-'} → {', '.join(sorted(b - a)) or '-'}")
    return lines


def apply_lines(first, lines):
    out = {k: set(v) for k, v in first.items()}
    for line in lines:
        q, rest = line.split(':', 1)
        left, right = (s.strip() for s in rest.split('→', 1))
        q = q.strip()
        for o in ([] if left == '-' else [x.strip() for x in left.split(',')]):
            out.setdefault(q, set()).discard(o)
        for o in ([] if right == '-' else [x.strip() for x in right.split(',')]):
            out.setdefault(q, set()).add(o)
    return {k: sorted(v) for k, v in sorted(out.items()) if v}


def shape(v1, v2, plan):
    moved = {}
    for item in ITEMS:
        a, b = v1[plan][item]['rank'], v2[plan][item]['rank']
        if a != b:
            moved[item] = 'UP' if b > a else 'DOWN'
    if len(set(moved.values())) > 1:
        return 'conflict'
    return moved


def compare(v1, v2):
    # 처음·마지막 결과에서 (권장안, 항목, 방향) 신호를 만든다. 비교할 수 없으면 (None, 버린 이유)
    pref = lambda v: next((p for p in v if v[p]['preferred']), None)
    p1, p2 = pref(v1), pref(v2)
    if p1 != p2:
        return None, '권장안이 달라짐'
    if p1:
        s = shape(v1, v2, p1)
        if s == 'conflict':
            return None, '항목이 반대로 바뀜'
        if not s:
            return None, '바뀐 항목 없음'
        return [(p1, item, d) for item, d in s.items()], None
    shapes = {p: shape(v1, v2, p) for p in ('MAC', 'WINDOWS')}
    if 'conflict' in shapes.values():
        return None, '항목이 반대로 바뀜'
    dirs = {d for s in shapes.values() for d in s.values()}
    if not dirs:
        return None, '바뀐 항목 없음'
    if len(dirs) > 1:
        return None, '항목이 반대로 바뀜'
    out = []
    for item in ITEMS:
        on = [p for p in ('MAC', 'WINDOWS') if item in shapes[p]]
        if on:
            out.append(('BOTH' if len(on) == 2 else on[0], item, shapes[on[0]][item]))
    return out, None


def signal_key(purpose, plan_label, problem_text, lines):
    return json.dumps([purpose, plan_label, problem_text, lines], ensure_ascii=False)


def signals(path, prod, days):
    now = datetime.now(timezone.utc)
    start = now - timedelta(days=int(days))
    start_iso, start_ts = start.strftime('%Y-%m-%dT%H:%M:%SZ'), int(start.timestamp())
    dropped, defs, rule_state, cache, groups = Counter(), {}, {}, {}, {}

    def prod_values(purpose, answers):
        k = json.dumps([purpose, answers], sort_keys=True)
        if k not in cache:
            r = api(prod, purpose, answers)
            cache[k] = values(r['body']) if r['status'] == 200 else None
        return cache[k]

    for views in load_sessions(path):
        if not views or views[0]['ts'] < start_ts:
            dropped['조회 기간 전에 시작한 세션'] += 1
            continue
        results = [v for v in views if v['event'] == RESULT_EVENT and v['path'] == '/result']
        if len({json.dumps([v['purpose'], v['answers']], sort_keys=True) for v in results}) < 2:
            dropped['다른 답 조합의 결과를 두 번 이상 보지 않음'] += 1
            continue
        first, last = results[0], results[-1]
        purpose = first['purpose']
        if not purpose or purpose != last['purpose']:
            dropped['처음과 마지막의 사용 목적이 다르거나 없음'] += 1
            continue
        if first['answers'] == last['answers']:
            dropped['처음과 마지막 답이 같음'] += 1
            continue
        if purpose not in defs:
            dm, dd = definition(MAIN, purpose), definition(DEVELOP, purpose)
            defs[purpose] = dm
            rule_state[purpose] = (diff(dm, dd), history(MAIN, purpose, start_iso)) if dm else None
        dm = defs[purpose]
        if dm is None:
            dropped['main 에 정의서가 없음(정의서 전체가 배포를 기다림)'] += 1
            continue
        pending, deployed = rule_state[purpose]
        seen = [v['answers'] for v in results]
        hit_any = lambda ch: any(hit(dm, ch['before'], a) or hit(dm, ch['after'], a) for a in seen)
        if any(hit_any(ch) for ch in pending):
            dropped['배포를 기다리는 규칙에 걸림'] += 1
            continue
        if any(first['ts'] < int(datetime.fromisoformat(c['time'].replace('Z', '+00:00')).timestamp())
               and any(hit_any(ch) for ch in c['changes']) for c in deployed):
            dropped['배포 전에 본 바뀐 규칙에 걸림'] += 1
            continue
        v1, v2 = prod_values(purpose, first['answers']), prod_values(purpose, last['answers'])
        if v1 is None or v2 is None:
            dropped['운영 결과를 받지 못함(질문이 바뀜)'] += 1
            continue
        found, reason = compare(v1, v2)
        if found is None:
            dropped[reason] += 1
            continue
        lines = changed_lines(first['answers'], last['answers'])
        for plan, item, direction in found:
            k = signal_key(purpose, PLAN_LABEL[plan], problem(item, direction), lines)
            g = groups.setdefault(k, {'key': k, 'purpose': purpose, 'plan': PLAN_LABEL[plan], 'item': item,
                                      'direction': direction, 'problem': problem(item, direction),
                                      'changed': lines, 'count': 0, 'urls': set()})
            g['count'] += 1
            g['urls'].add(result_url(first['category'], purpose, first['answers']))

    period = f"{start_iso[:10]} ~ {now.strftime('%Y-%m-%d')}"
    out = []
    for g in sorted(groups.values(), key=lambda g: (-g['count'], g['key'])):
        g['urls'] = sorted(g['urls'])
        plan_title = 'Mac·Windows' if g['plan'] == '둘 다' else g['plan']
        moved = '내려간' if g['direction'] == 'DOWN' else '올라간'
        g['title'] = f"[제품 추천 지표] {plan_title} 권장안에서 {g['problem']}"
        g['body'] = '\n'.join([
            '### 결과 화면 주소', '', *g['urls'], '',
            '### 어느 권장안인가요?', '', g['plan'], '',
            '### 어떤 점이 맞지 않았나요?', '', g['problem'], '',
            '### 더 알려 주실 내용', '', '_No response_', '',
            '### 바꾼 답', '', *g['changed'], '',
            '### 근거 지표', '',
            f'- 기간: {period}',
            '- 신호: 답을 바꿔 다시 본 세션',
            f"- 건수: {g['count']}",
            f"- \"{g['problem']}\"는 사용자가 고른 것이 아니라, 답을 바꾼 뒤 {SUBJECT[g['item']]} {moved} 것을 보고 추정한 것이다.",
            ''])
        out.append(g)
    return {'period': period, 'start': start_iso, 'groups': out, 'dropped': dict(dropped)}


def issue(body_path):
    f = fields(read(body_path))
    urls = [l.strip() for l in f.get('결과 화면 주소', '').splitlines() if l.strip().startswith(RESULT_PREFIXES)]
    lines = [l.strip() for l in f.get('바꾼 답', '').splitlines() if l.strip()]
    if not urls:
        return {'key': None, 'error': '결과 화면 주소가 없다'}
    purpose = parse_url(urls[0])['purpose']
    out = {'key': signal_key(purpose, f.get('어느 권장안인가요?', ''), f.get('어떤 점이 맞지 않았나요?', ''), lines),
           'purpose': purpose, 'pending': []}
    dm, dd = definition(MAIN, purpose), definition(DEVELOP, purpose)
    if dm is None:
        out['pending'].append('main 에 정의서가 없음')
        return out
    combos = []
    for u in urls:
        a = parse_url(u)['answers']
        combos += [a, apply_lines(a, lines)]
    hit_any = lambda ch: any(hit(dm, ch['before'], a) or hit(dm, ch['after'], a) for a in combos)
    out['pending'] = [ch['key'] for ch in diff(dm, dd) if hit_any(ch)]
    return out


COMMANDS.update({
    'signals': lambda r: signals(*r),
    'issue': lambda r: issue(*r),
})

if __name__ == '__main__':
    cmd, *rest = sys.argv[1:]
    if cmd not in COMMANDS:
        sys.exit(f'알 수 없는 명령: {cmd}')
    print(json.dumps(COMMANDS[cmd](rest), ensure_ascii=False, indent=1))
