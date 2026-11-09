#!/usr/bin/env python3
# Recommendation Improver 예약 작업의 설문 개선 지시 스크립트. snapshot.py 의 명령(common.py 포함)에 아래 전용 명령을 더한다.
# common.py, snapshot.py 와 같은 디렉터리에 두고 실행한다.
from snapshot import *  # noqa: F401,F403
from snapshot import COMMANDS, FEEDBACK_PREFIX, case_id, case_names, json, row, snapshot_lines, sys
import common as C
import copy
from common import EMPTY, TRUE, Impossible, rewrite

#   survey.py next           COMMENTS OUT                       산출물 댓글(JSON 줄, 이슈마다 {issue, id, created_at, html_url, body})에서
#                                                               결과가 없는 가장 오래된 지시. 그 data 를 OUT 에 쓴다. {target, invalid}
#   survey.py survey-check   BASE PURPOSE INSTRUCTION PLAN [TODAY]  설문 개선 지시를 BASE(git ref) 정의서에 실행할 수 있는지. 되면 고칠 내용을 PLAN 에 쓴다
#   survey.py survey-apply   PLAN WT                            WT 에 지운 코드 목록을 쓰고, 스냅숏의 이름 있는 사례 답을 지금 코드로 고쳐 쓴다(PLAN 에 기록)
#   survey.py survey-verify  PLAN DEF                           고친 정의서가 PLAN 과 같은지. 함께 더할 규칙은 점수만 빼고 본다
#   survey.py survey-compare BEFORE AFTER INSTRUCTION PLAN [DECISIONS]  설문 변경 전후 스냅숏 비교. DECISIONS 는 답을 지운 피드백 사례 이슈의 댓글(JSON 줄)
#   survey.py survey-rule    INSTRUCTION                        함께 더할 규칙을 개선 지시 모양으로(snapshot.py add-cases·check·compare 에 넘긴다)
#   survey.py survey-affected PLAN ISSUES...                    지우거나 바꾼 코드가 든 열린 피드백 번호. ISSUES 는 {number, body} JSON 줄 파일
#   survey.py survey-undeployed REF_A REF_B PURPOSE             두 정의서 사이에 다른 질문 코드(배포되지 않은 설문 변경)
# INSTRUCTION 은 설문 개선 지시 yaml 블록을 JSON 으로 바꾼 파일 경로다.

FAMILIES = {'decision': 'result', 'survey-decision': 'survey-result'}
KINDS = {'REWORD': '문구 수정', 'ADD_OPTION': '선택지 추가', 'ADD_QUESTION': '질문 추가', 'RESTORE': '되살리기',
         'MERGE_OPTION': '선택지 합치기', 'REPLACE_OPTION': '선택지 대체', 'REPLACE_QUESTION': '질문 대체',
         'DELETE_OPTION': '선택지 삭제', 'DELETE_QUESTION': '질문 삭제', 'MOVE_QUESTION': '질문 옮기기',
         'SPLIT_OPTION': '선택지 나누기', 'CHANGE_INPUT': '선택 방식 변경'}
NEW_CODE_KINDS = ('ADD_OPTION', 'REPLACE_OPTION', 'SPLIT_OPTION', 'RESTORE', 'REPLACE_QUESTION')
RETIRING_KINDS = ('MERGE_OPTION', 'REPLACE_OPTION', 'REPLACE_QUESTION', 'DELETE_OPTION', 'DELETE_QUESTION', 'SPLIT_OPTION')
FIXED_QUESTIONS = ('CURRENT_OS', 'CURRENT_MAC_CPU', 'CURRENT_WINDOWS_CPU')  # 화면이 전제하는 현재 사양 질문
MAX_STEPS = 12
RETIRED_KEYS = ('question', 'option', 'to', 'drop', 'dropInvalid', 'was', 'retiredAt')


def marker_name(body):
    first = body.lstrip().split('\n', 1)[0].strip()
    return first[len(C.MARKER_PREFIX):-len(' -->')].strip() if first.startswith(C.MARKER_PREFIX) else None


def next_instruction(comments_path, out_path):
    # 이슈·종류마다 지시 I 의 결과는 I 뒤, 같은 종류의 다음 지시 앞에 있는 첫 결과다.
    # Feedback Analyzer 와 같게 형식이 맞는 지시와 결과만 짝짓는다
    comments = sorted((json.loads(l) for l in C.read(comments_path).splitlines() if l.strip()),
                      key=lambda c: (c['created_at'], c['id']))
    pending, waiting, invalid = [], {}, []
    for c in comments:
        name = marker_name(c['body'])
        if name not in FAMILIES and name not in FAMILIES.values():
            continue
        b = C.block(c['body'])
        if b['errors']:
            invalid.append({'issue': c['issue'], 'url': c['html_url'], 'errors': b['errors']})
            continue
        c = dict(c, name=name, data=b['data'])
        for ins, res in FAMILIES.items():
            k = (c['issue'], ins)
            if name == ins:
                if k in waiting:
                    pending.append(waiting[k])
                waiting[k] = c
            elif name == res:
                waiting.pop(k, None)
    todo = sorted(pending + list(waiting.values()), key=lambda c: (c['created_at'], c['id']))
    if not todo:
        return {'target': None, 'invalid': invalid}
    c = todo[0]
    with open(out_path, 'w', encoding='utf-8') as f:
        json.dump(c['data'], f, ensure_ascii=False, indent=1)
    return {'target': {'kind': c['name'], 'issue': c['issue'], 'url': c['html_url']}, 'invalid': invalid}


# ---- 정의서 다루기 ----

def questions_of(d):
    return {q['code']: q for step in d['survey']['steps'] for q in step['questions']}


def codes(q):
    return [o['code'] for o in q['options']] if q else []


def wording(q, o=None):
    return q['title'] if o is None else next(x['content'] for x in q['options'] if x['code'] == o)


def without(q, *keys):
    return {k: v for k, v in q.items() if k not in keys}


def bare(q):
    # 문구(질문 제목·설명, 선택지 문구·설명)를 뺀 모양
    return {**without(q, 'title', 'description', 'options'),
            'options': [without(o, 'content', 'description') for o in q['options']]}


def conditions(d):
    return ([r['when'] for r in d['rules']] + [m['when'] for m in d.get('mappings') or []]
            + [n['when'] for n in d.get('notes') or []]
            + [q['showWhen'] for q in questions_of(d).values() if q.get('showWhen')])


def used(d, q, o=None):
    # 규칙(점수 0 포함)·매핑·안내의 조건, 노출 조건, 배타 선택지 중 하나에 그 코드가 있다
    if o is None:
        return any(q in C.questions(c) for c in conditions(d))
    return any(o in C.options(c, q) for c in conditions(d)) or questions_of(d)[q].get('exclusive') == o


def shape_errors(d, ins):
    kind, q, o = ins['kind'], ins['target']['question'], ins['target'].get('option')
    f, t, qs = ins['from'], ins['to'], questions_of(d)
    if f != qs.get(q):
        return [f'from 이 지금 정의서의 {q} 와 다르다']
    if f is None and o is not None:
        return [f'선택지를 되살릴 질문 {q} 가 없다']
    errs = []
    if t and kind != 'REPLACE_QUESTION' and t['code'] != q:
        errs.append(f"to 의 질문 코드 {t['code']} 가 대상 {q} 와 다르다")
    if kind == 'REPLACE_QUESTION' and t['code'] in qs:
        errs.append(f"새 질문 코드가 아니다: {t['code']}")
    if kind in ('ADD_QUESTION', 'MOVE_QUESTION') or kind == 'RESTORE' and o is None:
        if ins.get('after') not in qs or ins.get('after') == q:
            errs.append(f"after 가 지금 다른 질문이 아니다: {ins.get('after')}")
    if errs or f is None or t is None:
        return errs
    removed, added = [c for c in codes(f) if c not in codes(t)], [c for c in codes(t) if c not in codes(f)]
    # 합치기는 합쳐지는 선택지의 문구를 합친 뜻에 맞게 고칠 수 있다. retire 의 줄 수·to 는 retire_errors 가 따로 검사한다
    into = {l.get('to') for l in ins.get('retire') or []
            if l.get('question') == q and l.get('option') == o} if kind == 'MERGE_OPTION' else set()
    loose = lambda x: without(x, 'content', 'description') if x['code'] in into else x
    kept_same = [loose(x) for x in f['options'] if x['code'] in codes(t)] == [loose(x) for x in t['options'] if x['code'] in codes(f)]
    rest_same = without(f, 'options', 'exclusive') == without(t, 'options', 'exclusive')
    exclusive = f.get('exclusive')
    if exclusive == o and kind in ('DELETE_OPTION', 'MERGE_OPTION', 'SPLIT_OPTION'):
        exclusive = None
    if exclusive == o and kind == 'REPLACE_OPTION' and len(added) == 1:
        exclusive = added[0]
    ok = {
        'REWORD': lambda: bare(f) == bare(t) and f != t,
        'MOVE_QUESTION': lambda: f == t,
        'CHANGE_INPUT': lambda: without(f, 'input', 'exclusive') == without(t, 'input', 'exclusive')
                                and (f.get('input') or 'SINGLE', f.get('exclusive')) != (t.get('input') or 'SINGLE', t.get('exclusive')),
        'ADD_OPTION': lambda: not removed and added == [o] and kept_same and f.get('exclusive') == t.get('exclusive') and rest_same,
        'DELETE_OPTION': lambda: removed == [o] and not added,
        'MERGE_OPTION': lambda: removed == [o] and not added,
        'SPLIT_OPTION': lambda: removed == [o] and len(added) >= 2,
        'REPLACE_OPTION': lambda: removed == [o] and len(added) == 1,
    }
    ok['RESTORE'] = ok['ADD_OPTION']
    if kind in ('DELETE_OPTION', 'MERGE_OPTION', 'SPLIT_OPTION', 'REPLACE_OPTION'):
        check = ok[kind]
        ok[kind] = lambda: check() and kept_same and rest_same and t.get('exclusive') == exclusive
    if kind in ok and not ok[kind]():
        errs.append(f'from 과 to 의 차이가 {kind} 와 맞지 않는다')
    return errs


def retire_errors(ins):
    # 지시의 retire 가 종류별 표와 맞는지. 줄마다 (질문, 선택지) → (허용하는 운명, was)
    kind, q, o, f, t = ins['kind'], ins['target']['question'], ins['target'].get('option'), ins['from'], ins['to']
    given = ins.get('retire') or []
    fate = lambda l: 'dropInvalid' if l.get('dropInvalid') else 'drop' if l.get('drop') else ('to', l['to'])
    want = {}
    if kind in ('DELETE_OPTION', 'SPLIT_OPTION'):
        want[(q, o)] = ({'drop'}, wording(f, o))
    elif kind == 'MERGE_OPTION':
        want[(q, o)] = ({('to', x) for x in codes(t)}, wording(f, o))
    elif kind == 'REPLACE_OPTION':
        want[(q, o)] = ({('to', x) for x in codes(t) if x not in codes(f)} | {'drop'}, wording(f, o))
    elif kind == 'DELETE_QUESTION':
        want[(q, None)] = ({'drop'}, wording(f))
    elif kind == 'REPLACE_QUESTION':
        want[(q, None)] = ({('to', t['code']), 'drop'}, wording(f))
        if any(l['question'] == q and 'option' not in l and l.get('to') for l in given):
            for x in codes(f):
                if x not in codes(t):
                    want[(t['code'], x)] = ({('to', y) for y in codes(t)} | {'drop'}, wording(f, x))
    elif kind == 'CHANGE_INPUT' and drops_invalid(f, t):
        want[(q, None)] = ({'dropInvalid'}, None)
    got = {}
    for l in given:
        k = (l['question'], l.get('option'))
        if k in got:
            return [f'retire 에 같은 코드의 줄이 둘 있다: {k}']
        got[k] = (fate(l), l.get('was'))
    if set(got) != set(want):
        return [f"retire 의 코드가 {kind} 와 맞지 않는다(지시 {sorted(map(str, got))}, 맞는 것 {sorted(map(str, want))})"]
    return [f'retire 의 {k} 줄이 {kind} 와 맞지 않는다' for k, (fa, was) in got.items()
            if fa not in want[k][0] or was != want[k][1]]


def drops_invalid(f, t):
    # 여러 개 고르기를 하나 고르기로 바꾸거나 배타 선택지를 새로 두면 옛 답이 지금 규칙에 맞지 않을 수 있다
    multi_to_single = (f.get('input') or 'SINGLE') == 'MULTI' and (t.get('input') or 'SINGLE') == 'SINGLE'
    return multi_to_single or bool(t.get('exclusive')) and t.get('exclusive') != f.get('exclusive')


def code_maps(ins):
    kind, q, o, f, t = ins['kind'], ins['target']['question'], ins['target'].get('option'), ins['from'], ins['to']
    if kind == 'DELETE_QUESTION':
        return {q: None}, {}
    if kind == 'REPLACE_QUESTION':
        lines = {l['option']: l.get('to') for l in ins.get('retire') or [] if 'option' in l}
        omap = {(q, x): [x] if x in codes(t) else [lines[x]] if lines.get(x) else [] for x in codes(f)}
        return {q: t['code']}, omap
    if kind in ('DELETE_OPTION', 'MERGE_OPTION', 'SPLIT_OPTION'):
        return {}, {(q, o): []}
    if kind == 'REPLACE_OPTION':
        return {}, {(q, o): [x for x in codes(t) if x not in codes(f)]}
    return {}, {}


def step_of(steps, code):
    return next(i for i, s in enumerate(steps) if any(q['code'] == code for q in s['questions']))


def planned_definition(d, ins):
    kind, q, o, t = ins['kind'], ins['target']['question'], ins['target'].get('option'), ins['to']
    p = copy.deepcopy(d)
    steps = p['survey']['steps']
    if kind in ('MOVE_QUESTION', 'DELETE_QUESTION'):
        s = steps[step_of(steps, q)]
        s['questions'] = [x for x in s['questions'] if x['code'] != q]
        if not s['questions']:
            steps.remove(s)
    if kind in ('ADD_QUESTION', 'MOVE_QUESTION') or kind == 'RESTORE' and o is None:
        steps.insert(step_of(steps, ins['after']) + 1, {'questions': [t]})
    elif kind != 'DELETE_QUESTION':
        s = steps[step_of(steps, q)]
        s['questions'] = [t if x['code'] == q else x for x in s['questions']]
    if len(steps) > MAX_STEPS:
        raise Impossible(f'단계가 {len(steps)}개로 {MAX_STEPS}개를 넘는다')

    errs, effects = [], []
    for r in ins.get('reasons') or []:
        found = [x for x in p['rules'] if C.key(x) == C.key(r)]
        if len(found) != 1 or found[0]['reason'] != r['from']:
            errs.append(f"reasons 의 규칙이 지금 정의서에 그 문장으로 하나 있지 않다: {C.key(r)}")
        else:
            found[0]['reason'] = r['to']
            effects.append({'part': 'rules', 'before': {**found[0], 'reason': r['from']}, 'after': found[0]})
    if errs:
        raise Impossible('; '.join(errs))

    qmap, omap = code_maps(ins)
    for x in [x for step in steps for x in step['questions'] if x.get('showWhen')]:
        w = rewrite(x['showWhen'], qmap, omap)
        if w in (EMPTY, TRUE):
            raise Impossible(f"{x['code']} 의 노출 조건이 빈다")
        if w != x['showWhen']:
            effects.append({'part': 'showWhen', 'question': x['code'], 'before': x['showWhen'], 'after': w})
        x['showWhen'] = w
    for part in ('rules', 'mappings', 'notes'):
        kept = []
        for x in p.get(part) or []:
            w = rewrite(x['when'], qmap, omap)
            if w == TRUE:
                raise Impossible(f"조건 전체가 not 인 {part} 가 늘 참이 된다")
            if w != EMPTY:
                kept.append({**x, 'when': w})
            if w != x['when']:
                effects.append({'part': part, 'before': x, 'after': None if w == EMPTY else {**x, 'when': w}})
        if part in p:
            p[part] = kept
    keys = [C.key(r) for r in p['rules']]
    if len(set(keys)) != len(keys):
        raise Impossible('같은 규칙이 둘 생긴다')
    if ins.get('rule'):
        r = ins['rule']
        last = max((i for i, x in enumerate(p['rules']) if x['criterion'] == r['criterion']), default=len(p['rules']) - 1)
        p['rules'].insert(last + 1, {'criterion': r['criterion'], 'weight': None, 'when': r['when'], 'reason': r['reason'],
                                     **({'os': r['os']} if r.get('os') else {})})
    return p, effects


def forbidden_errors(d, ins):
    kind, q = ins['kind'], ins['target']['question']
    errs = []
    if kind in NEW_CODE_KINDS and any(q in C.questions(m['when']) for m in d.get('mappings') or []):
        errs.append(f'매핑이 쓰는 질문 {q} 에 새 코드를 만든다')
    if q in FIXED_QUESTIONS and kind in RETIRING_KINDS + ('MOVE_QUESTION',):
        errs.append(f'현재 사양 질문 {q} 를 옮기거나 코드를 지우거나 바꾼다')
    return errs


def all_codes(d):
    qs = questions_of(d)
    return set(qs) | {(q, o) for q in qs for o in codes(qs[q])}


def retired_history(ref, purpose):
    # ref 이력에서 지운 코드 목록 파일의 모든 판의 줄. 기한이 지나 지운 줄도 남는다
    path = C.RETIRED_PATH.format(purpose)
    lines = []
    for sha in C.git('log', '--format=%H', ref, '--', path).stdout.split():
        r = C.git('show', f'{sha}:{path}')
        if r.returncode == 0 and r.stdout.strip():
            lines += (C.y2j(r.stdout) or {}).get('codes') or []
    return lines


def reuse_errors(d, p, history, ins):
    # 새로 생긴 코드가 지운 코드 목록의 이력에 있으면 다시 쓰는 것이다. RESTORE 만 옛 문구 그대로면 된다
    pq = questions_of(p)
    errs, restored = [], False
    target = (ins['target']['question'], ins['target'].get('option'))
    for code in sorted(all_codes(p) - all_codes(d), key=str):
        q, o = code if isinstance(code, tuple) else (code, None)
        was = {l.get('was') for l in history if not l.get('dropInvalid') and l['question'] == q and l.get('option') == o}
        if not was:
            continue
        restored |= (q, o) == target
        if ins['kind'] != 'RESTORE' or wording(pq[q], o) not in was:
            errs.append(f"지운 코드를 다시 쓴다: {q}{'.' + o if o else ''}")
    if ins['kind'] == 'RESTORE' and not restored:
        errs.append('되살릴 코드가 지운 코드 목록 이력에 없다')
    return errs


def planned_retired(lines, ins, on):
    # 지시의 retire 줄을 더하고, 이번에 지우거나 바꾼 코드를 가리키던 줄을 고쳐 쓰고, 고친 파일이면 기한 지난 줄을 지운다.
    # 파일이 그대로면 None
    kind, q, o, t = ins['kind'], ins['target']['question'], ins['target'].get('option'), ins['to']
    given = [dict(l) for l in ins.get('retire') or []]
    qline = next((l for l in given if l['question'] == q and 'option' not in l and not l.get('dropInvalid')), None)
    gone_q = {q} if kind == 'DELETE_QUESTION' or qline and qline.get('drop') else set()
    new_q = qline['to'] if qline and qline.get('to') else None
    fate = {(l['question'], l['option']): l.get('to') for l in given if 'option' in l}
    nxt = {l['question']: l.get('to') for l in lines if 'option' not in l and not l.get('dropInvalid')}

    def ends_in_gone(code):
        # 질문 줄은 이름이 여러 번 바뀌면 to 가 이어진다. 끝이 지우는 질문이면 그 줄도 drop 이어야 서버가 받는다
        seen = set()
        while code and code not in seen:
            if code in gone_q:
                return True
            seen.add(code)
            code = nxt.get(code)
        return False

    out, changes = [], {'added': [], 'rewritten': [], 'removed': [], 'expired': []}
    for l in lines:
        before, l = l, dict(l)
        is_qline = 'option' not in l and not l.get('dropInvalid')
        restored = kind == 'RESTORE' and l['question'] == q and l.get('option') == o and not l.get('dropInvalid')
        if restored or not is_qline and l['question'] in gone_q:
            changes['removed'].append(before)
            continue
        if is_qline and ends_in_gone(l.get('to')):
            l.pop('to')
            l['drop'] = True
        if new_q and l['question'] == q and not is_qline:
            l['question'] = new_q
        if 'option' in l and (l['question'], l.get('to')) in fate:
            target = fate[(l['question'], l.pop('to'))]
            l.update({'to': target} if target else {'drop': True})
            l = {k: l[k] for k in RETIRED_KEYS if k in l}
        if l.get('dropInvalid') and l['question'] == q and any(x.get('dropInvalid') for x in given):
            l['retiredAt'] = on.isoformat()
            given = [x for x in given if not x.get('dropInvalid')]
        if l != before:
            changes['rewritten'].append({'before': before, 'after': l})
        out.append(l)
    for l in given:
        l['retiredAt'] = on.isoformat()
        out.append(l)
        changes['added'].append(l)
    if not (changes['added'] or changes['rewritten'] or changes['removed']):
        return None, changes
    changes['expired'] = [l for l in out if C.expired(l, on)]
    return [l for l in out if not C.expired(l, on)], changes


def screen(d, code):
    # 질문 화면: 현재 사양 단계가 아니고 질문이 하나뿐인 하나 고르기 화면은 고르면 바로 넘어간다(QuestionView)
    steps = d['survey']['steps']
    for i, s in enumerate(steps):
        for q in s['questions']:
            if q['code'] == code:
                auto = s.get('layout') != 'CURRENT_SPEC' and len(s['questions']) == 1 and (q.get('input') or 'SINGLE') == 'SINGLE'
                return {'step': i + 1, 'title': q['title'], 'description': q.get('description'),
                        'options': [x['content'] + (f" ({x['description']})" if x.get('description') else '') for x in q['options']], 'input': q.get('input') or 'SINGLE',
                        'exclusive': q.get('exclusive'), 'next': '고르면 바로 넘어감' if auto else '다음 버튼'}
    return None


def plan(d, lines, history, ins, on):
    errs = shape_errors(d, ins)
    if not errs:
        errs = retire_errors(ins) + forbidden_errors(d, ins)
    if errs:
        return {'ok': False, 'errors': errs}
    try:
        p, effects = planned_definition(d, ins)
    except Impossible as e:
        return {'ok': False, 'errors': [f'참조 정리를 할 수 없다: {e}']}
    errs = reuse_errors(d, p, history, ins)
    if errs:
        return {'ok': False, 'errors': errs}
    retired, changes = planned_retired(lines, ins, on)
    q = ins['target']['question']
    gone = sorted(f'{c[0]}.{c[1]}' if isinstance(c, tuple) else c for c in all_codes(d) - all_codes(p))
    gone += [l['question'] for l in changes['added'] if l.get('dropInvalid')]
    return {'ok': True, 'errors': [], 'purpose': ins['purpose'], 'today': on.isoformat(), 'kind': ins['kind'],
            'label': KINDS[ins['kind']], 'question': q, 'definition': p, 'retired': retired, 'retired_changes': changes,
            'lines': lines if retired is None else retired,
            'effects': effects, 'gone': gone, 'new_rule': ins.get('rule'),
            'screen': {'before': screen(d, q), 'after': screen(p, ins['to']['code']) if ins['to'] else None}}


def survey_check(base, purpose, ins_path, plan_path, on=None):
    ins = json.loads(C.read(ins_path))
    d = C.definition(base, purpose)
    if d is None or ins['purpose'] != purpose:
        return {'ok': False, 'errors': [f'{base} 에 {purpose} 정의서가 없다']}
    lines = C.retired(base, purpose)
    out = plan(d, lines, retired_history(base, purpose) + lines, ins, date.fromisoformat(on) if on else C.today())
    if out['ok']:
        with open(plan_path, 'w', encoding='utf-8') as f:
            json.dump(out, f, ensure_ascii=False, indent=1)
    return {k: out[k] for k in out if k not in ('definition', 'retired')}


def retired_yaml(lines):
    # 서버가 읽는 모양. was 는 ? 같은 글자가 있어 따옴표로 감싸고, retiredAt 은 따옴표 없는 날짜다
    item = lambda l: '{ ' + ', '.join(f'{k}: {json.dumps(l[k], ensure_ascii=False) if k == "was" else "true" if l[k] is True else l[k]}'
                                      for k in RETIRED_KEYS if k in l) + ' }'
    return 'codes:\n' + ''.join(f'  - {item(l)}\n' for l in lines) if lines else 'codes: []\n'


def rewrite_named(path, d, lines, on):
    # 이름 있는 사례의 답을 서버가 읽는 지금 코드로 고쳐 쓴다. 응답은 UPDATE_SNAPSHOT 이 채운다
    raw = C.read(path).rstrip('\n').split('\n')
    out = []
    for i in range(1, len(raw) - 1):
        c = json.loads(raw[i].strip().rstrip(','))
        if not c.get('name'):
            continue
        r = C.reread(d, lines, c['answers'], on)
        if not r['outdated']:
            continue
        new = {k: r['answers'][k] for k in sorted(r['answers'])}
        out.append({'name': c['name'], 'before': c['answers'], 'after': new, 'dropped': r['dropped']})
        c['answers'] = new
        raw[i] = '  ' + json.dumps(c, ensure_ascii=False, separators=(',', ':')) + (',' if raw[i].endswith(',') else '')
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(raw) + '\n')
    return out


def snapshot_path(wt, purpose):
    return os.path.join(wt, 'src/test/resources/recommendation', purpose.lower().replace('_', '-') + '-snapshot.json')


def survey_apply(plan_path, wt):
    p = json.loads(C.read(plan_path))
    on = date.fromisoformat(p['today'])
    if p['retired'] is not None:
        with open(os.path.join(wt, C.RETIRED_PATH.format(p['purpose'])), 'w', encoding='utf-8') as f:
            f.write(retired_yaml(p['retired']))
    p['named'] = rewrite_named(snapshot_path(wt, p['purpose']), p['definition'], p['lines'], on)
    with open(plan_path, 'w', encoding='utf-8') as f:
        json.dump(p, f, ensure_ascii=False, indent=1)
    return {'retired_written': p['retired'] is not None, 'named': p['named']}


def survey_verify(plan_path, def_path):
    p = json.loads(C.read(plan_path))
    d, want = C.definition(def_path, p['purpose']), p['definition']
    new = p.get('new_rule')
    nk = C.key(new) if new else None
    by_when = lambda xs: sorted(json.dumps({**x, 'when': C.canon(x['when'])}, sort_keys=True, ensure_ascii=False) for x in xs or [])
    errs = [f'{k} 가 계획과 다르다' for k in sorted(set(d) | set(want))
            if k not in ('rules', 'mappings', 'notes') and d.get(k) != want.get(k)
            or k in ('mappings', 'notes') and by_when(d.get(k)) != by_when(want.get(k))]
    norm = lambda rs: sorted(json.dumps([C.key(r), r.get('weight'), r['reason']], ensure_ascii=False) for r in rs if C.key(r) != nk)
    got, exp = norm(d['rules']), norm(want['rules'])
    errs += [f'계획에 없는 규칙: {x}' for x in got if x not in exp] + [f'계획한 규칙이 없다: {x}' for x in exp if x not in got]
    added = [r for r in d['rules'] if nk and C.key(r) == nk]
    weight = None
    if added:
        weight = added[0]['weight']
        if added[0]['reason'] != new['reason'] or not weight or (weight > 0) != (new['direction'] == 'UP'):
            errs.append(f"더한 규칙의 근거 문장이나 점수 방향이 지시와 다르다(weight {weight})")
    return {'ok': not errs, 'errors': errs, 'new_rule_weight': weight}


def plain(v):
    return {p: [v[p]['preferred']] + [v[p][i]['value'] for i in ITEMS] for p in v}


def decisions(path):
    # 오래된 것부터. 같은 사례를 가리키는 지시가 여럿이면 뒤의 것이 이긴다
    rows = sorted((json.loads(l) for l in (C.read(path).splitlines() if path else []) if l.strip()),
                  key=lambda c: (c['created_at'], c['id']))
    out = []
    for c in rows:
        if marker_name(c['body']) == 'decision':
            b = C.block(c['body'])
            if not b['errors']:
                out.append(b['data'])
    return out


def survey_compare(before_path, after_path, ins_path, plan_path, decisions_path=None):
    ins, p = json.loads(C.read(ins_path)), json.loads(C.read(plan_path))
    before = {case_id(c): c for c in snapshot_lines(before_path)}
    after = {case_id(c): c for c in snapshot_lines(after_path)}
    on = date.fromisoformat(p['today'])
    # 지운 코드가 든 옛 답은 서버가 바꿔 읽어 결과가 달라질 수 있다. 지금 설문으로 답한 사례만 그대로여야 한다
    old = {k for k, c in before.items() if C.convert_or_error(p['definition'], p['lines'], c['answers'], on).get('outdated', True)}
    swap = {r['from']: r['to'] for r in ins.get('reasons') or []}
    expected = {}
    for d in decisions(decisions_path):
        for name, c in zip(case_names(d), d['cases']):
            # 지시는 근거 중 번호가 가장 작은 이슈에 달린다. 피드백 #N 사례는 근거에 N 이 든 지시에서 찾는다
            if c['issue'] in d['issues']:
                expected[name] = c.get('expected') or []
    fails, reason_only, rows, unchecked = [], [], [], []
    for k, c in before.items():
        if k not in after:
            fails.append(f'{k}: 스냅숏에서 사례가 없어졌다')
            continue
        v1, v2 = values(c['response']), values(after[k]['response'])
        if k in old:
            if c.get('name'):
                rows.append(row(k, v1, v2, ''))
            if k.startswith(FEEDBACK_PREFIX) and k not in expected:
                unchecked.append(k)
            for e in expected.get(k, []):
                a, b = v1[e['plan']][e['item']]['rank'], v2[e['plan']][e['item']]['rank']
                if b < a if e['direction'] == 'UP' else b > a:
                    fails.append(f"{k}: {e['plan']} {e['item']} 이 개선 지시의 {e['direction']} 과 반대로 움직였다")
            continue
        if plain(v1) != plain(v2):
            fails.append(f'{k}: 결과 값이 바뀌었다')
        swapped = {pl: {i: [swap.get(x, x) for x in v1[pl][i]['reasons']] for i in ITEMS} for pl in v1}
        if swapped != {pl: {i: v2[pl][i]['reasons'] for i in ITEMS} for pl in v2}:
            fails.append(f'{k}: reasons 밖의 근거 문장이 바뀌었다')
        elif any(v1[pl][i]['reasons'] != v2[pl][i]['reasons'] for pl in v1 for i in ITEMS):
            reason_only.append(k)
    return {'ok': not fails, 'fails': fails, 'total': len(before), 'reason_only': len(reason_only), 'old_answers': len(old),
            'named_rows': rows, 'unchecked': unchecked}


def survey_rule(ins_path):
    ins = json.loads(C.read(ins_path))
    r = ins['rule']
    return {'issues': ins['issues'], 'basis': 'USER', 'purpose': ins['purpose'],
            'rule': {k: r[k] for k in ('criterion', 'when', 'os') if k in r},
            'change': {'add': {'direction': r['direction']}, 'reason': {'to': r['reason']}},
            'cases': ins['cases'], 'reason': ins['reason']}


def survey_affected(plan_path, *paths):
    p = json.loads(C.read(plan_path))
    gone, out = set(p['gone']), []
    for path in paths:
        for l in C.read(path).splitlines():
            if not l.strip():
                continue
            issue = json.loads(l)
            f = C.fields(issue.get('body') or '')
            pairs = [(q, o) for u in f.get('결과 화면 주소', '').split() if u.startswith(C.RESULT_PREFIXES)
                     for x in [C.parse_url(u)] if x['purpose'] == p['purpose'] for q, os_ in x['answers'].items() for o in os_]
            if f.get('사용 목적', '').strip() == p['purpose']:
                pairs += [tuple(x.split('.', 1)) for k in ('지금까지 고른 답', '이 화면에서 고른 답')
                          for x in f.get(k, '').split(',') if '.' in x]
                pairs += [(q.strip(), None) for q in f.get('질문', '').split(',') if q.strip()]
            if any(q in gone or f'{q}.{o}' in gone for q, o in pairs):
                out.append(issue['number'])
    return sorted(set(out))


def undeployed(d1, d2):
    q1, q2 = questions_of(d1) if d1 else {}, questions_of(d2) if d2 else {}
    return sorted(q for q in set(q1) | set(q2) if q1.get(q) != q2.get(q))


COMMANDS.update({
    'next': lambda r: next_instruction(*r),
    'survey-check': lambda r: survey_check(*r),
    'survey-apply': lambda r: survey_apply(*r),
    'survey-verify': lambda r: survey_verify(*r),
    'survey-compare': lambda r: survey_compare(*r),
    'survey-rule': lambda r: survey_rule(*r),
    'survey-affected': lambda r: survey_affected(*r),
    'survey-undeployed': lambda r: undeployed(C.definition(r[0], r[2]), C.definition(r[1], r[2])),
})

if __name__ == '__main__':
    cmd, *rest = sys.argv[1:]
    if cmd not in COMMANDS:
        sys.exit(f'알 수 없는 명령: {cmd}')
    print(json.dumps(COMMANDS[cmd](rest), ensure_ascii=False, indent=1))
