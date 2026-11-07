#!/usr/bin/env python3
# Feedback Analyzer 예약 작업의 설문 쪽 스크립트. Recommendation Improver 의 survey.py 명령(common.py·snapshot.py 포함)에 아래 전용 명령을 더한다.
# common.py, snapshot.py, survey.py 와 같은 디렉터리에 두고 실행한다.
from survey import *  # noqa: F401,F403
from survey import COMMANDS, json, marker_name, plan, questions_of, retired_history, sys, used, without
import common as C
import hashlib
import re
import subprocess
from datetime import date

#   survey_feedback.py survey-state REF ISSUES COMMENTS [PRS]   설문 라벨 이슈마다 칸·산출물·대상과, 그 이슈들에 달린 지시의 상태
#   survey_feedback.py seen-survey  MAIN DEVELOP STATE ISSUE    그 설문 피드백의 사용자가 본 설문 정의서와, 그 뒤 develop 에서 질문이 바뀌었는지
#   survey_feedback.py survey-ready REF COMMENT STATE RULES [TODAY]  설문 개선 지시 댓글 초안을 낼 수 있는지
# ISSUES 는 {number, title, state, created_at, author, author_association, labels, body}, COMMENTS 는 {issue, id, created_at,
# author_association, body}, PRS 는 {number, state, mergedAt, closedAt} 의 JSON 줄 파일이다. STATE 는 survey-state 의 출력 파일,
# RULES 는 규칙 쪽의 처리 중인 개선 지시(이번 실행에서 낸 것 포함) [{purpose, rule}] JSON 파일이다.

TEAM = ('OWNER', 'MEMBER', 'COLLABORATOR')
APPROVED = 'product-recommendation-approved'
UNUSED = '쓰이지 않는 질문'
FAMILY = {'REWORD': '문구 수정·옮기기', 'MOVE_QUESTION': '문구 수정·옮기기',
          'ADD_OPTION': '추가·되살리기', 'ADD_QUESTION': '추가·되살리기', 'RESTORE': '추가·되살리기'}
REMOVING = '삭제·합치기·대체·쪼개기·선택 방식'
USES_TWO = ('REPLACE_OPTION', 'REPLACE_QUESTION', 'SPLIT_OPTION', 'CHANGE_INPUT')


def jsonl(path):
    return [json.loads(l) for l in C.read(path).splitlines() if l.strip()] if path else []


def split(text):
    return [x.strip() for x in (text or '').split(',') if x.strip()]


def status(issue_open, result, prs, need):
    # binding.md 의 개선 지시 상태. PR 상태를 모르면 unknown 이고 need 에 PR 번호를 더한다
    if result is None:
        return ('processing' if issue_open else 'stopped'), None
    r = result['data']
    if r['outcome'] == 'NOT_OPENED':
        return 'failed', result['created_at']
    p = prs.get(r['pr'])
    if p is None:
        need.add(r['pr'])
        return 'unknown', None
    if p.get('mergedAt') or p['state'] == 'MERGED':
        return 'merged', None
    if p['state'] == 'CLOSED':
        return 'rejected', p.get('closedAt')
    return ('processing' if issue_open else 'stopped'), None


def survey_state(ref, issues_path, comments_path, prs_path=None):
    comments = {}
    for c in jsonl(comments_path):
        if c['author_association'] in TEAM:
            comments.setdefault(c['issue'], []).append(c)
    prs = {p['number']: p for p in jsonl(prs_path)}
    lines = {}
    out, instructions, need = [], [], set()
    for i in sorted(jsonl(issues_path), key=lambda i: i['number']):
        arts, invalid = [], []
        for c in sorted(comments.get(i['number'], []), key=lambda c: (c['created_at'], c['id'])):
            b = C.block(c['body'])
            if not b['schema']:
                continue
            if b['errors']:
                invalid.append({'id': c['id'], 'marker': marker_name(c['body']), 'errors': b['errors']})
            else:
                arts.append({'name': marker_name(c['body']), 'created_at': c['created_at'], 'data': b['data']})
        latest = lambda name: next((a for a in reversed(arts) if a['name'] == name), None)
        handover = next((a for a in arts if a['name'] == 'handover'), None)
        team, title, f = i['author_association'] in TEAM, i['title'], C.fields(i.get('body') or '')
        skip, counted = None, True
        if title.startswith('[제품 추천 설문 지표]') and team:
            kind = 'METRIC'
        elif title.startswith(('[제품 추천 설문 의견]', '[제품 추천 설문 지표]')):
            kind, counted = 'USER', not team
        elif title.startswith('[제품 추천 의견]'):
            kind, skip = 'HANDED', None if handover else '넘김 없음'
        else:
            kind, skip = None, '머리말이 설문 피드백이 아님'
        h = handover['data'] if handover else {}
        signal = re.search(r'^- 신호: (.+)$', f.get('근거 지표', ''), re.M)
        item = {'number': i['number'], 'state': i['state'], 'created_at': i['created_at'], 'author': i['author'], 'team': team,
                'kind': kind, 'counted': counted and not skip, 'skip': skip,
                'purpose': h.get('purpose') if kind == 'HANDED' else f.get('사용 목적', '').strip(),
                'questions': [h['question']] if h else split(f.get('질문')),
                'title': None if kind == 'HANDED' else f.get('질문 제목') or None,
                'version': None if kind == 'HANDED' else f.get('설문 버전') or None,
                'problems': h.get('problems') if kind == 'HANDED' else split(f.get('어떤 점이 맞지 않았나요?')),
                'details': f.get('더 알려 주실 내용', ''), 'signal': signal[1].strip() if signal else None,
                'approved': APPROVED in (i.get('labels') or []), 'handover': h or None,
                'analysis': latest('survey-analysis'), 'rule_analysis': latest('analysis'), 'invalid': invalid}
        if kind == 'HANDED':
            item['result_problems'] = split(f.get('어떤 점이 맞지 않았나요?'))
            ra = item['rule_analysis']
            item['handover_unfinished'] = bool(h) and not (ra and ra['created_at'] >= handover['created_at']
                                                            and ra['data']['judgment'] == 'HANDED_OVER')
        item['arts'] = arts
        out.append(item)
        pending = []
        for a in arts:
            if a['name'] in ('decision', 'survey-decision'):
                pending.append({'issue': i['number'], 'type': 'rule' if a['name'] == 'decision' else 'survey',
                                'created_at': a['created_at'], 'data': a['data'], 'result': None})
            elif a['name'] in ('result', 'survey-result'):
                kind_ = 'rule' if a['name'] == 'result' else 'survey'
                prev = next((x for x in reversed(pending) if x['type'] == kind_), None)
                if prev:
                    prev['result'] = a
        for x in pending:
            r = x.pop('result')
            x['status'], x['failed_at'] = status(i['state'] == 'open', r, prs, need)
            x['pr'] = r['data'].get('pr') if r else None
            x['reason'] = r['data'].get('reason') if r else None
            if x['type'] == 'survey':
                purpose = x['data']['purpose']
                lines.setdefault(purpose, C.retired(ref, purpose))
                x['question'] = C.renamed(lines[purpose], x['data']['target']['question'])[0]
                x['family'] = FAMILY.get(x['data']['kind'], REMOVING)
            instructions.append(x)
    by = {i['number']: i for i in out}
    for x in instructions:
        # 실패·반려 뒤에 근거 이슈 어디에도 판단이 없고, 근거가 겹치는 다른 지시도 없으면 다시 판단한다(규칙 쪽 R 과 같다)
        judged = any(a['created_at'] > (x['failed_at'] or '') and a['name'] in ('survey-analysis', 'survey-decision')
                     for n in x['data']['issues'] if n in by for a in by[n]['arts'])
        overlap = any(y is not x and y['created_at'] > x['created_at'] and set(y['data']['issues']) & set(x['data']['issues'])
                      for y in instructions if y['type'] == x['type'])
        x['rejudge'] = x['type'] == 'survey' and x['status'] in ('failed', 'rejected') and not judged and not overlap
    rejudged = {n for x in instructions if x['rejudge'] for n in x['data']['issues']}
    for i in out:
        a = i['analysis']
        fresh = i['state'] == 'open' and not i['skip'] and (a is None or a['data']['judgment'] == 'ON_HOLD')
        i['target'] = 'rejudge' if i['number'] in rejudged and i['state'] == 'open' else 'analyze' if fresh else None
        del i['arts']
    return {'issues': out, 'instructions': instructions, 'prs': sorted(need)}


def version_commit(ref, purpose, version):
    # 설문 버전은 정의서 파일 바이트의 SHA-256 앞 12자다(RecommendationDefinition)
    path = C.DEF_PATH.format(purpose)
    for sha in C.git('log', '--format=%H', ref, '--', path).stdout.split():
        blob = subprocess.run(['git', '-C', C.repo(), 'show', f'{sha}:{path}'], capture_output=True).stdout
        if hashlib.sha256(blob).hexdigest()[:12] == version.strip().lower():
            return sha
    return None


def seen_survey(main, develop, purpose, codes, version, created, title=None):
    codes = split(codes)
    version = None if version in ('', '-') else version
    sha = version_commit(main, purpose, version) or version_commit(develop, purpose, version) if version else C.at(main, created)
    d = C.definition(sha, purpose) if sha else None
    if d is None:
        return {'judgment': 'NOT_REPRODUCIBLE', 'commit': sha, 'reason': '사용자가 본 정의서를 찾지 못했다'}
    qs = questions_of(d)
    missing = [c for c in codes if c not in qs]
    if missing or not codes:
        return {'judgment': 'NOT_REPRODUCIBLE', 'commit': sha, 'reason': f'사용자가 본 정의서에 질문이 없다: {missing}'}
    if not version and title and all(qs[c]['title'] != title.strip() for c in codes):
        return {'judgment': 'NOT_REPRODUCIBLE', 'commit': sha, 'reason': '사용자가 본 정의서의 질문 제목이 다르다'}
    now = questions_of(C.definition(develop, purpose) or {'survey': {'steps': []}})
    lines = C.retired(develop, purpose)
    out = []
    for c in codes:
        current = C.renamed(lines, c)[0]
        q = now.get(current)
        # 문구·선택지·선택 방식을 본다. 노출 조건은 다른 질문의 변경으로 바뀔 수 있어 보지 않는다
        changed = q is None or without(q, 'code', 'showWhen') != without(qs[c], 'code', 'showWhen')
        out.append({'code': c, 'current': current if q else None, 'changed': changed, 'seen': qs[c], 'now': q})
    return {'judgment': 'ALREADY_CHANGED' if all(x['changed'] for x in out) else None, 'commit': sha, 'questions': out}


def seen_issue(main, develop, state_path, number):
    # 칸의 값(사용자가 쓴 글자일 수 있다)을 명령줄로 옮기지 않으려고 survey-state 의 출력에서 읽는다
    i = next(i for i in json.loads(C.read(state_path))['issues'] if i['number'] == int(number))
    return seen_survey(main, develop, i['purpose'], ','.join(i['questions']), i['version'] or '-', i['created_at'], i['title'])


def requirement(d, ins, users, metrics, approved):
    # 명세의 지시 조건 표. (필요한 근거, 만족하는지)
    kind, q, o = ins['kind'], ins['target']['question'], ins['target'].get('option')
    if kind == 'DELETE_QUESTION' and approved and not used(d, q):
        return '쓰이지 않는 질문 지표 피드백의 승인 라벨', True
    if kind in ('DELETE_OPTION', 'MERGE_OPTION', 'DELETE_QUESTION'):
        if not used(d, q, None if kind == 'DELETE_QUESTION' else o):
            return '사용자 의견 1건과 지표 신호 1개, 또는 서로 다른 사용자 의견 2건', users >= 1 and len(metrics) >= 1 or users >= 2
        return '서로 다른 사용자 의견 2건', users >= 2
    if kind in USES_TWO:
        return '서로 다른 사용자 의견 2건', users >= 2
    if kind == 'REWORD' and without(ins['from'], 'description') == without(ins['to'], 'description'):
        return '사용자 의견 1건, 또는 서로 다른 지표 신호 2개', users >= 1 or len({m['signal'] for m in metrics}) >= 2
    return '사용자 의견 1건', users >= 1


def survey_ready(ref, comment_path, state_path, rules_path, on=None):
    b = C.block(C.read(comment_path))
    if b['schema'] != 'survey-instruction':
        return {'ok': False, 'stage': 'schema', 'errors': ['첫 줄이 설문 개선 지시 표시가 아니다']}
    if b['errors']:
        return {'ok': False, 'stage': 'schema', 'errors': b['errors']}
    ins, state = b['data'], json.loads(C.read(state_path))
    purpose, kind, q, o = ins['purpose'], ins['kind'], ins['target']['question'], ins['target'].get('option')
    d = C.definition(ref, purpose)
    if d is None:
        return {'ok': False, 'stage': 'plan', 'errors': [f'{ref} 에 {purpose} 정의서가 없다']}
    lines = C.retired(ref, purpose)

    # 막는 조건: 같은 질문의 처리 중인 설문 개선 지시, 그 질문을 조건에 쓰는 처리 중인 개선 지시
    mine = [x for x in state['instructions'] if x['data']['purpose'] == purpose]
    hold = [f"같은 질문에 처리 중인 설문 개선 지시 #{x['issue']}" for x in mine
            if x['type'] == 'survey' and x['status'] == 'processing' and x['question'] == q]
    rules = [x['data']['rule'] for x in mine if x['type'] == 'rule' and x['status'] == 'processing']
    rules += [x['rule'] for x in json.loads(C.read(rules_path)) if x['purpose'] == purpose]
    hold += [f"그 질문을 조건에 쓰는 처리 중인 개선 지시의 규칙 {C.key(u['rule'])}" for u in C.uses_question(lines, [q], rules, C.merged_options(ref, purpose))]
    if hold:
        return {'ok': False, 'stage': 'hold', 'errors': hold}

    by = {i['number']: i for i in state['issues']}
    errs = [] if ins['issues'] == sorted(set(ins['issues'])) else ['issues 가 오름차순이 아니거나 겹친다']
    users, metrics, approved = set(), [], False
    check_question = not (kind == 'ADD_QUESTION' or kind == 'RESTORE' and o is None)
    for n in ins['issues']:
        i = by.get(n)
        if not i or i['state'] != 'open' or i['skip'] or i['purpose'] != purpose:
            errs.append(f'#{n} 은 이 사용 목적의 열린 설문 피드백이 아니다')
            continue
        if check_question and q not in {C.renamed(lines, x)[0] for x in i['questions']}:
            errs.append(f'#{n} 의 질문에 {q} 가 없다')
        if not i['counted']:
            continue
        if i['kind'] == 'METRIC':
            metrics.append(i)
            approved |= i['approved'] and i['signal'] == UNUSED
        else:
            users.add(i['author'])
    basis = 'USER_AND_METRIC' if users and metrics else 'USER' if users else 'METRIC'
    if ins['basis'] != basis:
        errs.append(f'basis 는 {basis} 이어야 한다')
    if errs:
        return {'ok': False, 'stage': 'evidence', 'errors': errs}

    p = plan(d, lines, retired_history(ref, purpose) + lines, ins, on or C.today())
    if not p['ok']:
        return {'ok': False, 'stage': 'plan', 'errors': p['errors']}
    if ins.get('rule'):
        misses = [c['issue'] for c in ins['cases'] if not C.ev(ins['rule']['when'], C.visible(p['definition'], c['answers']))]
        if misses:
            return {'ok': False, 'stage': 'rule', 'errors': [f'사례 #{n} 의 답이 함께 더할 규칙에 걸리지 않는다' for n in misses]}
    need, met = requirement(d, ins, len(users), metrics, approved)
    counts = {'need': need, 'users': len(users), 'metrics': [m['number'] for m in metrics]}
    if not met:
        return {'ok': False, 'stage': 'count', 'errors': [f'근거가 모자란다: {need}'], **counts}

    # 같은 질문·같은 묶음의 실패·반려. 한 번 반려된 것만 문구를 고쳐 다시 낼 수 있다
    family = FAMILY.get(kind, REMOVING)
    previous = [{'issue': x['issue'], 'status': x['status'], 'pr': x['pr'], 'reason': x['reason']} for x in mine
                if x['type'] == 'survey' and x['status'] in ('failed', 'rejected') and x['question'] == q and x['family'] == family]
    retry = len(previous) == 1 and previous[0]['status'] == 'rejected'
    if previous and not retry:
        return {'ok': False, 'stage': 'previous', 'errors': ['같은 질문·같은 묶음의 설문 개선이 실패·반려된 적이 있다'],
                'previous': previous}
    return {'ok': True, 'stage': None, 'errors': [], 'basis': basis, **counts, 'previous': previous,
            'screen': p['screen'], 'effects': p['effects'], 'gone': p['gone']}


COMMANDS.update({
    'survey-state': lambda r: survey_state(*r),
    'seen-survey': lambda r: seen_issue(*r),
    'survey-ready': lambda r: survey_ready(*r[:4], date.fromisoformat(r[4]) if len(r) > 4 else None),
})

if __name__ == '__main__':
    cmd, *rest = sys.argv[1:]
    if cmd not in COMMANDS:
        sys.exit(f'알 수 없는 명령: {cmd}')
    print(json.dumps(COMMANDS[cmd](rest), ensure_ascii=False, indent=1))
