#!/usr/bin/env python3
# Recommendation Improver 예약 작업의 스크립트. 함께 쓰는 명령(rec_common.py 머리 주석)에 아래 전용 명령을 더한다.
# rec_common.py 와 같은 디렉터리에 두고 실행한다.
from rec_common import *  # noqa: F401,F403
from rec_common import COMMANDS, json, sys

#   rec.py add-cases SNAPSHOT INSTRUCTION      지시의 재현 사례를 스냅숏에 더한다(이미 있는 이름은 건너뛴다). 더한 이름 목록
#   rec.py compare   BEFORE AFTER INSTRUCTION  수정 전후 스냅숏 비교: 기대 결과 판정, 결과 변화, 영향 범위
# INSTRUCTION 은 개선 지시 yaml 블록을 JSON 으로 바꾼 파일 경로다.
from collections import Counter

FEEDBACK_PREFIX = '피드백 #'


def case_names(instruction):
    names, seen = [], Counter()
    for c in instruction['cases']:
        seen[c['issue']] += 1
        n = seen[c['issue']]
        names.append(f"{FEEDBACK_PREFIX}{c['issue']}" + (f' ({n})' if n > 1 else ''))
    return names


def snapshot_lines(path):
    # 스냅숏은 한 줄에 사례 하나인 JSON 배열이다(RecommendationServiceTest.writeSnapshot)
    lines = read(path).rstrip('\n').split('\n')
    assert lines[0] == '[' and lines[-1] == ']', '스냅숏 모양이 예상과 다르다'
    return [json.loads(l.strip().rstrip(',')) for l in lines[1:-1]]


def add_cases(path, instruction_path):
    # 이름 있는 사례(대표 사용자, 앞선 피드백 사례) 맨 뒤에 한 줄씩 끼운다. 다른 줄은 글자 그대로 둔다
    ins = json.loads(read(instruction_path))
    lines = read(path).rstrip('\n').split('\n')
    cases = snapshot_lines(path)
    names = {c.get('name') for c in cases}
    at = 1 + max((i + 1 for i, c in enumerate(cases) if c.get('name')), default=0)
    added = []
    for name, c in zip(case_names(ins), ins['cases']):
        if name in names:
            continue
        answers = {k: list(v) for k, v in sorted(c['answers'].items())}
        # 응답은 UPDATE_SNAPSHOT 이 지금 정의서로 채운다
        lines.insert(at, '  ' + json.dumps({'name': name, 'answers': answers, 'response': None},
                                           ensure_ascii=False, separators=(',', ':')) + ',')
        at += 1
        added.append(name)
    if lines[-2].endswith(','):
        lines[-2] = lines[-2][:-1]
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')
    return added


def case_id(c):
    return c.get('name') or json.dumps(c['answers'], sort_keys=True)


def when_pairs(c):
    if 'question' in c:
        return [f"{c['question']}.{o}" for o in c['anyOf']]
    return [p for x in (c.get('all') or c.get('any') or [c.get('not')]) for p in when_pairs(x)]


def row(name, v1, v2, criterion):
    return {'case': name,
            'plans': {p: {i: f"{v1[p][i]['display']} → {v2[p][i]['display']}" for i in ITEMS} for p in v1},
            'reasons': {p: {'before': v1[p][criterion]['reasons'], 'after': v2[p][criterion]['reasons']}
                        for p in v1 if criterion in ITEMS and v1[p][criterion]['reasons'] != v2[p][criterion]['reasons']}}


def compare(before_path, after_path, instruction_path):
    ins = json.loads(read(instruction_path))
    criterion, change = ins['rule']['criterion'], ins['change']
    before = {case_id(c): c for c in snapshot_lines(before_path)}
    after = {case_id(c): c for c in snapshot_lines(after_path)}
    val = lambda c: values(c['response'])
    plain = lambda v: {p: [v[p]['preferred']] + [v[p][i]['value'] for i in ITEMS] for p in v}

    checks, rows = [], []
    reason_only = set(change) == {'reason'}
    for name, c in zip(case_names(ins), ins['cases']):
        if name not in before or name not in after:
            checks.append({'case': name, 'ok': False, 'why': '스냅숏에 사례가 없다'})
            continue
        v1, v2 = val(before[name]), val(after[name])
        rows.append(row(name, v1, v2, criterion))
        fails = []
        if reason_only:
            if plain(v1) != plain(v2):
                fails.append('근거 문장만 바꾸는 지시인데 결과 값이 바뀌었다')
        for e in c.get('expected') or []:
            a, b = v1[e['plan']][e['item']]['rank'], v2[e['plan']][e['item']]['rank']
            if not (b > a if e['direction'] == 'UP' else b < a):
                fails.append(f"{e['plan']} {e['item']} 이 {e['direction']} 쪽으로 바뀌지 않았다"
                             f" ({v1[e['plan']][e['item']]['display']} → {v2[e['plan']][e['item']]['display']})")
        if 'reason' in change and not change.get('delete'):
            for p in v2:
                rs = v2[p][criterion]['reasons'] if criterion in ITEMS else []
                if change['reason']['to'] not in rs or change['reason']['from'] in rs:
                    fails.append(f'{p} 의 {criterion} 근거 문장이 지시한 문장으로 바뀌지 않았다')
        checks.append({'case': name, 'ok': not fails, 'why': '; '.join(fails)})

    own = set(case_names(ins))
    changed_values, changed_reasons, personas, by_answer, others = [], [], [], Counter(), []
    pairs = when_pairs(change['when']['to'] if 'when' in change else ins['rule']['when'])
    for k, c in after.items():
        if k not in before or before[k]['response'] == c['response']:
            continue
        v1, v2 = val(before[k]), val(c)
        if plain(v1) != plain(v2):
            changed_values.append(k)
            present = [p for p in pairs if p.split('.', 1)[1] in c['answers'].get(p.split('.', 1)[0], [])]
            for p in present or ['조건 밖']:
                by_answer[p] += 1
        else:
            changed_reasons.append(k)
        name = c.get('name')
        if name and not name.startswith(FEEDBACK_PREFIX):
            personas.append(name)
        if name and name.startswith(FEEDBACK_PREFIX) and name not in own:
            others.append(row(name, v1, v2, criterion))
    total = len(after)
    return {'ok': bool(checks) and all(x['ok'] for x in checks), 'checks': checks, 'rows': rows,
            'other_feedback_rows': others,
            'impact': {'total': total, 'value_changed': len(changed_values), 'reason_only': len(changed_reasons),
                       'value_changed_by_answer': dict(by_answer), 'personas_changed': personas}}


COMMANDS.update({
    'add-cases': lambda r: add_cases(*r),
    'compare': lambda r: compare(*r),
})

if __name__ == '__main__':
    cmd, *rest = sys.argv[1:]
    if cmd not in COMMANDS:
        sys.exit(f'알 수 없는 명령: {cmd}')
    print(json.dumps(COMMANDS[cmd](rest), ensure_ascii=False, indent=1))
