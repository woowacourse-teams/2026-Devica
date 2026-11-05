#!/usr/bin/env python3
# 제품 추천 개선 예약 작업이 함께 쓰는 스크립트. 정의서 읽기·규칙 적중·이력·제품 추천 API·피드백 칸 읽기를 맡는다.
# 세 구성 요소의 예약 작업이 함께 쓴다. 구성 요소 전용 명령은 각 구성 요소 디렉터리의 스크립트(signals.py, snapshot.py) 가 이 파일을 불러와 더한다.
#
# 레포는 환경 변수 REPO(없으면 현재 디렉터리). REF 는 git ref(origin/main 등)이거나 정의서 파일 경로다.
# ANSWERS 는 {"질문": ["선택지", ...]} JSON 이고, '-' 를 주면 표준 입력에서 읽는다.
#
#   common.py def     REF PURPOSE                       정의서(JSON). 없으면 null
#   common.py hits    REF PURPOSE ANSWERS               답 조합에 걸린 규칙(노출 조건을 적용한 답 기준)
#   common.py visible REF PURPOSE ANSWERS               노출 조건을 적용한 답 조합
#   common.py diff    REF_A REF_B PURPOSE [CRITERION]   두 정의서 사이에 다른 규칙(before=A, after=B)
#   common.py history REF PURPOSE [SINCE_ISO]           first-parent 이력에서 규칙이 바뀐 커밋
#   common.py api     BASE_URL PURPOSE ANSWERS          제품 추천 API 결과 {status, body}
#   common.py url     RESULT_URL                        결과 화면 주소 → {category, purpose, answers}
#   common.py values  RESPONSE_JSON                     api 의 출력이나 그 body → 권장안별 항목 값·순위·근거
#   common.py fields  BODY_FILE                         피드백 이슈 본문 → 칸 제목별 값
#   common.py edited  BODY_FILE                         지표 피드백의 "고친 값" 칸 → 줄마다 주소 번호·항목·제품 추천 결과 값·고친 값(rank 포함)·건수
#   common.py metric  BODY_FILE                         지표 피드백 → 주소(번호·answers), 주소별 건수, 고친 값, 주소별 바꾼 답
#   common.py cluster ITEMS_JSON                       주소마다 가리키는 규칙 [{address, count, rules}] → 함께 가리키는 규칙과 그 주소 묶음
#   common.py block   COMMENT_FILE                      산출물 댓글 → 표시(첫 줄), 첫 ```yaml 블록(JSON), 스키마 검증 오류(errors)
#   common.py key     RULE [REF PURPOSE]                규칙을 구분하는 값(조건 안의 순서 무시, os 포함). RULE 은 {criterion, when, os?} JSON.
#                                                       REF 를 주면 옛 조건을 REF 의 지운 코드 목록으로 참조 정리처럼 지금 코드로 바꿔 본다
#                                                       (대체한 코드는 새 코드로, 합치거나 지운 코드는 뺌). 조건이 비면 null
#   common.py same    REF PURPOSE RULE                  같은 규칙(same)과 같은 항목·OS 에서 같은 질문들로 된 규칙(same_questions). RULE 의 옛 조건은 key 처럼 바꿔 본다
#   common.py uses    REF PURPOSE QUESTION CRITERION    그 질문을 보는 그 항목의 규칙(점수 0 포함)과 매핑
#   common.py hit     REF PURPOSE RULE ANSWERS          답 조합이 RULE 의 조건에 걸리는지(노출 조건을 적용한 답 기준)
#   common.py ends    REF PURPOSE PLAN ITEM             권장안·항목에서 낼 수 있는 가장 낮은·높은 값과 rank
#   common.py target  REF PURPOSE RULE QUESTION         기댄 답(QUESTION)으로 RULE 을 더할지: exists | add | widen(넓힐 규칙) | seen
#   common.py retired REF PURPOSE                       지운 코드 목록의 줄(없으면 [])
#   common.py convert REF PURPOSE ANSWERS [TODAY]       서버처럼 지운 코드를 지금 코드로 바꿔 읽은 답 {answers, outdated, dropped} 또는 {error}.
#                                                       dropped 는 버려진 답(drop·dropInvalid·숨은 답)이 있는지다
#   common.py uses-question REF PURPOSE QUESTIONS RULES 규칙 중 그 질문을 조건에 쓰는 것 [{question, rule}]. 둘 다 JSON 배열이고, 옛 조건은 key 처럼 바꿔 본다
#   common.py at      REF ISO                           그 시각에 REF(first-parent)가 가리키던 커밋. 없으면 null
#   common.py days    ISO                               그 시각부터 지금까지 지난 날 수(소수)
#
# 테스트는 test.py 에 있다.
# 스키마(interface/artifacts/*.schema.yml)는 이 파일과 같은 디렉터리나 그 아래 interface/artifacts/ 에서 찾는다.
import calendar, http.client, json, os, re, subprocess, sys, urllib.parse
from datetime import date, datetime, timedelta, timezone
from time import sleep

DEF_PATH = 'src/main/resources/recommendation/purposes/{}.yml'
RETIRED_PATH = 'src/main/resources/recommendation/purposes/{}.retired.yml'
KEEP_MONTHS = 6  # 지운 코드 줄의 보관 기간. RetiredCodes.KEEP_MONTHS 와 같다
SEOUL = timezone(timedelta(hours=9))
Y2J = ('f=lambda{|o| case o when Hash then o.map{|k,v|[k.to_s,f.(v)]}.to_h when Array then o.map{|v|f.(v)} '
       'when Time then o.utc.iso8601 when Date then o.iso8601 else o end}; '
       'puts JSON.generate(f.(YAML.safe_load($stdin.read, permitted_classes: [Date, Time], aliases: true)))')
CPU_RANK = {'BASIC': 0, 'PRO': 1, 'MAX': 2, 'U': 0, 'P_HS': 1, 'H': 2, 'HX': 3}
ITEMS = ('REQUIRED_CPU', 'MEMORY', 'STORAGE')
RESULT_PREFIXES = ('https://devica.co.kr/result', 'https://2026-devica.vercel.app/result')
MARKER_PREFIX = '<!-- product-recommendation:'
API_TRIES = 5  # 끊김(#259) 때 api 가 보내는 최대 횟수
# 댓글 표시 → 그 블록이 따라야 하는 스키마 (interface/binding.md)
MARKER_SCHEMAS = {'analysis': 'analysis-result', 'decision': 'improvement-instruction', 'result': 'improvement-result',
                  'survey-analysis': 'survey-analysis', 'survey-decision': 'survey-instruction', 'survey-result': 'improvement-result',
                  'handover': 'handover'}
TYPES = {'object': dict, 'array': list, 'string': str, 'integer': int, 'number': (int, float), 'boolean': bool, 'null': type(None)}


def repo():
    return os.environ.get('REPO') or os.getcwd()


def git(*args):
    return subprocess.run(['git', '-C', repo(), *args], capture_output=True, text=True)


def y2j(text):
    # macOS 기본 python 에는 YAML 파서가 없어 기본 ruby 로 읽는다. 댓글도 읽으므로 객체를 만들지 않는 safe_load 를 쓴다
    out = subprocess.run(['ruby', '-ryaml', '-rjson', '-rtime', '-rdate', '-e', Y2J],
                         input=text, capture_output=True, text=True, check=True)
    return json.loads(out.stdout)


def definition(ref, purpose):
    if os.path.isfile(ref):
        with open(ref, encoding='utf-8') as f:
            return y2j(f.read())
    r = git('show', f'{ref}:{DEF_PATH.format(purpose)}')
    return y2j(r.stdout) if r.returncode == 0 else None


def retired(ref, purpose):
    # ref 가 정의서 파일이면 그 옆의 지운 코드 목록 파일을 읽는다
    if os.path.isfile(ref):
        path = re.sub(r'\.yml$', '.retired.yml', ref)
        text = read(path) if os.path.isfile(path) else None
    else:
        r = git('show', f'{ref}:{RETIRED_PATH.format(purpose)}')
        text = r.stdout if r.returncode == 0 else None
    return ((y2j(text) if text else None) or {}).get('codes') or []


def today():
    return datetime.now(SEOUL).date()


def expiry(retired_at):
    # RetiredCodes.activeOn 과 같다: LocalDate.plusMonths 는 그 달에 같은 날이 없으면 말일이다
    d = date.fromisoformat(retired_at)
    m = d.month - 1 + KEEP_MONTHS
    y, m = d.year + m // 12, m % 12 + 1
    return date(y, m, min(d.day, calendar.monthrange(y, m)[1]))


def expired(line, on):
    return expiry(line['retiredAt']) < on


class Rejected(Exception):
    pass


def current_code(qs, lines, q, o=None):
    # Survey.currentQuestionCode·currentOptionCode 와 같다. 지금 코드 (질문, 선택지), 버리면 None, 줄이 없으면 Rejected
    while q not in qs:
        line = next((l for l in lines if l['question'] == q and 'option' not in l), None)
        if line is None:
            raise Rejected('QUESTION_NOT_FOUND')
        if line.get('drop'):
            return None
        q = line['to']
    if o is None or any(x['code'] == o for x in qs[q]['options']):
        return q, o
    line = next((l for l in lines if l['question'] == q and l.get('option') == o), None)
    if line is None:
        raise Rejected('ANSWER_NOT_ALLOWED')
    return None if line.get('drop') else (q, line['to'])


def convert(d, lines, params, on):
    # SurveyResponse.from 과 같다. 같은 변환 사례표(retired-cases.json)로 테스트한다
    qs = {q['code']: q for step in d['survey']['steps'] for q in step['questions']}
    live = [l for l in lines if not expired(l, on)]
    convertible = [l for l in live if not l.get('dropInvalid')]
    current, given, converted = {}, set(), False
    for code, opts in params.items():
        found = current_code(qs, convertible, code)
        converted |= found is None or found[0] != code
        if found is None or opts is None:
            continue
        q = found[0]
        for o in opts:
            if o == qs[q].get('exclusive'):
                given.add(q)
            c = current_code(qs, convertible, q, o)
            converted |= c is None or c[1] != o
            if c is not None and c[1] not in current.setdefault(q, []):
                current[q].append(c[1])
    selected = {}
    for q, opts in current.items():
        question, ex = qs[q], qs[q].get('exclusive')
        absorbed = question.get('input') == 'MULTI' and len(opts) > 1 and ex in opts and q not in given
        if absorbed:
            opts = [o for o in opts if o != ex]
        if not ((question.get('input') or 'SINGLE') == 'SINGLE' and len(opts) > 1 or len(opts) > 1 and ex in opts):
            selected[q] = opts
            converted |= absorbed
        elif any(l.get('dropInvalid') and l['question'] == q for l in live):
            converted = True
        else:
            raise Rejected('ANSWER_NOT_ALLOWED')
    shown = {}
    for step in d['survey']['steps']:
        for q in step['questions']:
            if q['code'] in selected and (q.get('showWhen') is None or ev(q['showWhen'], shown)):
                shown[q['code']] = selected[q['code']]
    return {'answers': shown, 'outdated': converted or len(shown) < len(selected)}


def reread(d, lines, params, on):
    # convert 에 버려진 답이 있는지(dropped)를 더한다. 원래 답을 코드만 지금 것으로 바꾼 답과 다르면 버려진 것이다.
    # 원래부터 노출 조건에 맞지 않던 답은 지운 코드와 상관없이 빠지므로 버려진 것으로 보지 않는다
    r = convert(d, lines, params, on)
    qs = {q['code']: q for step in d['survey']['steps'] for q in step['questions']}
    convertible = [l for l in lines if not expired(l, on) and not l.get('dropInvalid')]
    image = [current_code(qs, convertible, q, o) for q in params for o in params[q] or []]
    if None in image:
        return {**r, 'dropped': True}
    imaged = {}
    for q, o in image:
        imaged.setdefault(q, []).append(o)
    pairs = lambda a: {(q, o) for q, os_ in a.items() for o in os_}
    return {**r, 'dropped': pairs(r['answers']) != pairs(visible(d, imaged))}


def renamed(lines, q, o=None):
    # 지운 코드 목록의 to 줄을 따라간 지금 코드 (질문, 선택지). to 줄이 없으면 그대로다
    seen = set()
    while q not in seen:
        seen.add(q)
        line = next((l for l in lines if l['question'] == q and 'option' not in l and l.get('to')), None)
        if line is None:
            break
        q = line['to']
    line = next((l for l in lines if l['question'] == q and l.get('option') == o and l.get('to')), None) if o else None
    return q, line['to'] if line else o


EMPTY, TRUE = 'EMPTY', 'TRUE'  # 코드를 빼서 늘 거짓·늘 참이 된 조건


class Impossible(Exception):
    pass


def rewrite(c, qmap, omap):
    # 지운 코드는 조건에서 빼고, 새 코드로 옮긴 코드만 바꿔 쓴다. 빠져서 늘 거짓이면 EMPTY, not 이 늘 참이 되면 TRUE
    if 'question' in c:
        q = c['question']
        if q in qmap and qmap[q] is None:
            return EMPTY
        opts = []
        for o in c['anyOf']:
            opts += [n for n in omap.get((q, o), [o]) if n not in opts]
        return {'question': qmap.get(q, q), 'anyOf': opts} if opts else EMPTY
    if 'not' in c:
        x = rewrite(c['not'], qmap, omap)
        return TRUE if x == EMPTY else EMPTY if x == TRUE else {'not': x}
    k = 'all' if 'all' in c else 'any'
    xs = [rewrite(x, qmap, omap) for x in c[k]]
    if k == 'all':
        if EMPTY in xs:
            return EMPTY
        xs = [x for x in xs if x != TRUE]
        return {'all': xs} if xs else TRUE
    if TRUE in xs:
        raise Impossible('any 안의 not 이 늘 참이 된다')
    xs = [x for x in xs if x != EMPTY]
    return {'any': xs} if xs else EMPTY


def merged_options(ref, purpose):
    # 지운 코드 목록의 선택지 to 줄 중 합치기(MERGE_OPTION)로 생긴 것. 줄 하나로는 대체와 가를 수 없어, 그 줄을 처음 더한 커밋 전 정의서에
    # to 대상이 있었으면 합치기, 없었으면 대체(REPLACE_*)로 본다. 합쳐진 코드로 가던 줄을 고쳐 쓴 줄과 질문 대체로 옮긴 줄은 합치기를 이어받는다
    if os.path.isfile(ref):
        return set()
    path = RETIRED_PATH.format(purpose)
    merged, before = set(), {}
    for sha in reversed(git('log', '--format=%H', ref, '--', path).stdout.split()):
        r = git('show', f'{sha}:{path}')
        codes = ((y2j(r.stdout) if r.returncode == 0 and r.stdout.strip() else None) or {}).get('codes') or []
        now = {(l['question'], l['option']): l['to'] for l in codes if 'option' in l and l.get('to')}
        moved = {l['to']: l['question'] for l in codes if 'option' not in l and l.get('to')}
        d = definition(f'{sha}^', purpose)
        had = {(q['code'], o['code']) for step in d['survey']['steps'] for q in step['questions'] for o in q['options']} if d else set()
        new = [(k, to) for k, to in now.items() if k not in before]
        for (q, o), to in new:
            if (q, to) in had or (moved.get(q), o) in merged:
                merged.add((q, o))
        for (q, o), to in now.items():
            if (q, o) in before and before[(q, o)] != to and (q, before[(q, o)]) in merged:
                merged.add((q, o))
        before = now
    return merged


def current_rule(rule, lines, merged=frozenset()):
    # 옛 조건을 Recommendation Improver 의 참조 정리(rewrite)처럼 지금 코드로 바꾼다. 대체한 코드는 새 코드로 바꾸고, 합치거나 지운 코드는 빼고,
    # 조건이 비면(늘 참이 되는 것도) 그 규칙은 없는 것으로 보고 None
    dropped = {l['question'] for l in lines if 'option' not in l and l.get('drop')}
    qmap = {l['question']: None if renamed(lines, l['question'])[0] in dropped else renamed(lines, l['question'])[0]
            for l in lines if 'option' not in l and not l.get('dropInvalid')}
    omap = {(l['question'], l['option']): [] if l.get('drop') or (l['question'], l['option']) in merged else [l['to']]
            for l in lines if 'option' in l}
    try:
        w = rewrite(rule['when'], qmap, {})
        w = w if w in (EMPTY, TRUE) else rewrite(w, {}, omap)
    except Impossible:
        return None
    return None if w in (EMPTY, TRUE) else {**rule, 'when': w}


def uses_question(lines, qs, rules, merged=frozenset()):
    current = [(r, current_rule(r, lines, merged)) for r in rules]
    return [{'question': q, 'rule': r} for q in qs for r, c in current if c and renamed(lines, q)[0] in questions(c['when'])]


def to_current(ref, purpose):
    # ref 의 지운 코드 목록으로 옛 규칙을 지금 코드로 바꾸는 함수
    lines, merged = retired(ref, purpose), merged_options(ref, purpose)
    return lambda rule: current_rule(rule, lines, merged)


def at(ref, iso):
    return git('rev-list', '-1', '--first-parent', f'--before={utc(iso)}', ref).stdout.strip() or None


def days(iso):
    return (datetime.now(timezone.utc) - datetime.fromisoformat(iso.replace('Z', '+00:00'))).total_seconds() / 86400


def ev(c, a):
    if 'question' in c:
        return any(o in a.get(c['question'], []) for o in c['anyOf'])
    if 'all' in c:
        return all(ev(x, a) for x in c['all'])
    if 'any' in c:
        return any(ev(x, a) for x in c['any'])
    if 'not' in c:
        return not ev(c['not'], a)
    raise ValueError(c)


def visible(d, a):
    # SurveyResponse 와 같다: 노출 조건을 만족하는 질문의 답만 남긴다
    out = {}
    for step in d['survey']['steps']:
        for q in step['questions']:
            if a.get(q['code']) and (q.get('showWhen') is None or ev(q['showWhen'], out)):
                out[q['code']] = sorted(a[q['code']])
    return out


def canon(c):
    # 같은 조건을 같은 모양으로: 선택지와 all·any 의 항목 순서를 무시하고, 안에 든 같은 종류(all 안의 all)와 항목 하나뿐인 all·any 를 편다
    if 'question' in c:
        return {'question': c['question'], 'anyOf': sorted(set(c['anyOf']))}
    if 'not' in c:
        return {'not': canon(c['not'])}
    k = 'all' if 'all' in c else 'any'
    items = []
    for x in map(canon, c[k]):
        items += x[k] if k in x else [x]
    items = sorted({json.dumps(x, sort_keys=True): x for x in items}.values(), key=lambda x: json.dumps(x, sort_keys=True))
    return items[0] if len(items) == 1 else {k: items}


def key(rule):
    # 규칙을 구분하는 값. os 가 다르면 다른 규칙이다. 없는 규칙(None)은 None
    if rule is None:
        return None
    k = rule['criterion'] + ' ' + json.dumps(canon(rule['when']), sort_keys=True, ensure_ascii=False, separators=(',', ':'))
    return k + (' os=' + rule['os'] if rule.get('os') else '')


def questions(c):
    if 'question' in c:
        return {c['question']}
    return set().union(*(questions(x) for x in (c.get('all') or c.get('any') or [c.get('not')])))


def same(d, rule):
    # 같은 규칙(key 가 같은 것)과, 같은 항목·OS 에서 같은 질문들로 된 규칙. 없는 규칙(None)이면 둘 다 비었다
    rules = d['rules'] if rule else []
    return {'same': [r for r in rules if key(r) == key(rule)],
            'same_questions': [r for r in rules if r['criterion'] == rule['criterion'] and r.get('os') == rule.get('os')
                               and questions(r['when']) == questions(rule['when']) and key(r) != key(rule)]}


def options(c, question):
    # 조건에서 그 질문에 쓴 선택지
    if 'question' in c:
        return set(c['anyOf']) if c['question'] == question else set()
    return set().union(*(options(x, question) for x in ([c['not']] if 'not' in c else c.get('all') or c.get('any') or [])))


def uses(d, question, criterion):
    # 그 질문을 보는 규칙(점수 0 포함)과 매핑. 하나라도 있으면 그 답으로 규칙을 추가하지 않는다
    return {'rules': [r for r in d['rules'] if r['criterion'] == criterion and question in questions(r['when'])],
            'mappings': [m for m in d.get('mappings') or [] if question in questions(m['when'])]}


def target(d, rule, question):
    # 사용자가 기댄 답(question)으로 rule 을 더할지. rule 은 사용자가 고른 선택지로 만든 새 규칙이다
    #   exists  같은 규칙이 이미 있거나, 같은 질문들로 된 규칙이 모든 질문에서 사용자가 고른 선택지를 이미 담고 있다(rule 에 그 규칙)
    #   add     그 질문을 보는 그 항목의 규칙도 매핑도 없다
    #   widen   그 질문을 보는 규칙이 같은 질문들로 된 규칙 하나뿐이고, 어느 질문에서 사용자가 고른 선택지를 담지 않는다.
    #           그 규칙의 선택지를 넓힌다(rule 에 그 규칙)
    #   seen    그 밖. 다른 모양의 규칙이나 매핑이 그 답을 본다
    u, s = uses(d, question, rule['criterion']), same(d, rule)
    if s['same']:
        return {'kind': 'exists', 'rule': s['same'][0]}
    if u['mappings']:
        return {'kind': 'seen', 'rule': None}
    if not u['rules']:
        return {'kind': 'add', 'rule': None}
    shaped = {key(r) for r in s['same_questions']}
    if len(s['same_questions']) == 1 and all(key(r) in shaped for r in u['rules']):
        old = s['same_questions'][0]
        covered = all(options(rule['when'], q) <= options(old['when'], q) for q in questions(rule['when']))
        return {'kind': 'exists' if covered else 'widen', 'rule': old}
    return {'kind': 'seen', 'rule': None}


def ends(d, plan, item):
    # 권장안·항목에서 정의서가 낼 수 있는 가장 낮은·높은 값. 사용자가 본 값이 그 방향의 끝이면 규칙을 더해도 바뀌지 않는다
    it = d['interpretation']
    if item == 'REQUIRED_CPU':
        vs = [r['value'] for r in it['cpu'][plan]]
        if plan == 'MAC' and it.get('macCpuLimit'):
            vs = [v for v in vs if CPU_RANK[v] <= CPU_RANK[it['macCpuLimit']]]
        ranks = [CPU_RANK[v] for v in vs]
    else:
        vs = ranks = [r['value'] for r in it['memory' if item == 'MEMORY' else 'storage']]
    return {'min': {'value': vs[0], 'rank': ranks[0]}, 'max': {'value': vs[-1], 'rank': ranks[-1]}}


def hits(d, a, os_=None):
    # 규칙의 os 는 그 OS 권장안에만 붙는다는 뜻이라, 권장안을 주면 다른 OS 전용 규칙을 뺀다
    v = visible(d, a)
    return [r for r in d['rules'] if ev(r['when'], v) and (os_ is None or r.get('os') in (None, os_))]


def hit(d, rule, a):
    return rule is not None and ev(rule['when'], visible(d, a))


def diff(d1, d2, criterion=None):
    m1 = {key(r): r for r in (d1 or {}).get('rules', [])}
    m2 = {key(r): r for r in (d2 or {}).get('rules', [])}
    return [{'key': k, 'before': m1.get(k), 'after': m2.get(k)}
            for k in sorted(set(m1) | set(m2))
            if m1.get(k) != m2.get(k) and (criterion is None or k.startswith(criterion + ' '))]


def utc(iso):
    t = datetime.fromisoformat(iso.replace('Z', '+00:00'))
    return t.astimezone(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def interpretation_changed(d1, d2):
    # 점수 해석 규칙(interpretation)은 규칙 점수의 뜻을 정해, 바뀌면 어느 답 조합의 결과든 달라질 수 있다.
    # 한쪽에만 있으면 코드에 있던 같은 구간을 정의서로 옮긴 것(#295)이라 바뀐 것으로 보지 않는다
    i1, i2 = (d1 or {}).get('interpretation'), (d2 or {}).get('interpretation')
    return i1 is not None and i2 is not None and i1 != i2


def definition_commits(ref, purpose, since=None):
    # 커밋 시각(%cI)을 그 변경이 ref 에 들어온 시각으로 본다. main 은 릴리스마다 squash 커밋 하나다
    path = DEF_PATH.format(purpose)
    lines = git('log', '--first-parent', '--format=%H%x1f%cI%x1f%s', ref, '--', path).stdout.splitlines()
    for line in lines:
        sha, time, subject = line.split('\x1f', 2)
        time = utc(time)
        if since and time < utc(since):
            continue
        parent = git('rev-parse', '--verify', '-q', sha + '^').stdout.strip()
        yield sha, time, subject, definition(parent, purpose) if parent else None, definition(sha, purpose)


def history(ref, purpose, since=None):
    out = []
    for sha, time, subject, before, after in definition_commits(ref, purpose, since):
        changes = diff(before, after)
        if changes:
            out.append({'commit': sha, 'time': time, 'subject': subject, 'changes': changes})
    return out


def interpretation_history(ref, purpose, since=None):
    # 점수 해석 규칙이 바뀐 커밋의 시각
    return [time for _, time, _, before, after in definition_commits(ref, purpose, since)
            if interpretation_changed(before, after)]


def api(base, purpose, a):
    # urllib 은 요청마다 Connection: close 를 보내는데, 사내망에서는 http 응답이 도중에 자주 끊긴다(#259).
    # 연결을 유지하는 http.client 로 보내고, 끊기면 다시 보낸다
    q = urllib.parse.urlencode([(k, o) for k in sorted(a) for o in a[k]])
    u = urllib.parse.urlsplit(base.rstrip('/'))
    path = f'{u.path}/api/usage-purposes/{purpose}/recommendation?{q}'
    Conn = http.client.HTTPSConnection if u.scheme == 'https' else http.client.HTTPConnection
    for attempt in range(API_TRIES):
        c = Conn(u.netloc, timeout=30)
        try:
            c.request('GET', path, headers={'Accept': 'application/json'})
            r = c.getresponse()
            raw = r.read()
        except (ConnectionResetError, http.client.IncompleteRead):  # RemoteDisconnected 는 ConnectionResetError 다
            if attempt == API_TRIES - 1:
                raise
            print(f'api: 연결이 끊겨 다시 보낸다 ({attempt + 1}/{API_TRIES - 1})', file=sys.stderr)
            sleep(1)
            continue
        finally:
            c.close()
        try:
            body = json.loads(raw)
        except ValueError:
            body = None
        return {'status': r.status, 'body': body}


def parse_url(u):
    qs = urllib.parse.parse_qs(urllib.parse.urlsplit(u.strip()).query)
    answers = {}
    for pair in qs.get('answer', []):
        if '.' in pair:
            q, o = pair.split('.', 1)
            answers.setdefault(q, []).append(o)
    return {'category': (qs.get('category') or [None])[0], 'purpose': (qs.get('purpose') or [None])[0],
            'answers': {k: sorted(set(v)) for k, v in sorted(answers.items())}}


def result_url(category, purpose, answers):
    # 개인을 식별할 수 있는 값이 남지 않게 category·purpose·answer 만으로 새로 만든다
    q = [('category', category or 'LAPTOP'), ('purpose', purpose)]
    q += [('answer', f'{k}.{o}') for k in sorted(answers) for o in sorted(answers[k])]
    return 'https://devica.co.kr/result?' + urllib.parse.urlencode(q, safe='.')


def values(body):
    body = body.get('body', body) if 'specs' not in body else body
    out = {}
    for spec in body['specs']:
        items = {i['code']: i for i in spec['items']}
        plan = items['OS']['value']
        out[plan] = {'preferred': spec['preferred']}
        for code in ITEMS:
            i = items[code]
            out[plan][code] = {'value': i['value'], 'display': i['displayValue'],
                               'rank': CPU_RANK[i['value']] if code == 'REQUIRED_CPU' else int(i['value']),
                               'reasons': i['reasons']}
    return out


def fields(text):
    # 이슈 양식이 만드는 본문: "### 칸 제목" 줄 뒤의 값. 값이 없으면 "_No response_" 이고 빈 문자열로 돌려준다
    out = {}
    for m in re.finditer(r'^### (.+?)[ \t]*\n(.*?)(?=^### |\Z)', text, re.M | re.S):
        v = m.group(2).strip()
        out[m.group(1).strip()] = '' if v == '_No response_' else v
    return out


EDIT_LINE = re.compile(r'^(CPU|메모리|저장 공간): (\S+) → (\S+) \((\d+)건\)$')
EDIT_ITEM = {'CPU': 'REQUIRED_CPU', '메모리': 'MEMORY', '저장 공간': 'STORAGE'}


def edit_value(item, text):
    # signals.py 가 적은 값: CPU 는 등급 코드, 메모리·저장 공간은 16GB·1TB. rank 는 values 와 같은 척도다. 읽을 수 없으면 None
    if item == 'REQUIRED_CPU':
        return {'value': text, 'rank': CPU_RANK[text]} if text in CPU_RANK else None
    m = re.fullmatch(r'(\d+)(GB|TB)', text)
    return {'value': text, 'rank': int(m[1]) * (1024 if m[2] == 'TB' else 1)} if m else None


ADDRESS_LINE = re.compile(r'^주소 (\d+): ?(.*)$')


def addressed(line):
    # 지표 피드백의 주소별 줄 "주소 N: …". N 은 결과 화면 주소 칸의 순서(1부터)다. 접두어가 없으면 주소 1 이다
    m = ADDRESS_LINE.match(line.strip())
    return (int(m[1]), m[2].strip()) if m else (1, line.strip())


def edited(text):
    out = []
    for line in fields(text).get('고친 값', '').splitlines():
        address, rest = addressed(line)
        m = EDIT_LINE.match(rest)
        if not m:
            continue
        item = EDIT_ITEM[m[1]]
        recommended, changed = edit_value(item, m[2]), edit_value(item, m[3])
        # 사람이 본문을 고쳐 읽을 수 없게 된 줄은 건너뛴다
        if recommended and changed:
            out.append({'address': address, 'item': item, 'recommended': recommended, 'edited': changed, 'count': int(m[4])})
    return out


def metric(text):
    # 지표 피드백의 주소별 정보. 주소별 건수가 없는 옛 본문은 주소가 하나일 때만 건수를 그 주소의 것으로 본다
    f = fields(text)
    urls = [l.strip() for l in f.get('결과 화면 주소', '').splitlines() if l.strip().startswith(RESULT_PREFIXES)]
    basis = f.get('근거 지표', '')
    total, per = re.search(r'^- 건수: (\d+)', basis, re.M), re.search(r'^- 주소별 건수: (.+)$', basis, re.M)
    counts = [int(x) for x in per[1].split(',')] if per else [int(total[1])] if total and len(urls) == 1 else []
    changed = {}
    for line in f.get('바꾼 답', '').splitlines():
        if line.strip():
            n, rest = addressed(line)
            changed.setdefault(n, []).append(rest)
    return {'urls': [{'address': i + 1, **parse_url(u)} for i, u in enumerate(urls)], 'counts': counts,
            'edits': edited(text), 'changed': [{'address': n, 'lines': ls} for n, ls in sorted(changed.items())]}


def cluster(items):
    # 주소마다 가리키는 규칙 [{address, count, rules}]. 모든 주소의 교집합이 있으면 그것이 가리키는 규칙이다.
    # 없으면 가장 많은 건수가 함께 가리키는 규칙의 주소들을 묶고, 그 묶음의 교집합을 가리키는 규칙으로 본다.
    # 가장 큰 건수가 서로 다른 주소 묶음에서 나오면 tie 다. rest 는 묶음 밖 주소(가리키는 규칙이 없는 주소 포함)다
    norm = lambda r: key(r) if isinstance(r, dict) else r
    items = [{**i, 'rules': sorted({norm(r) for r in i['rules']})} for i in items]
    pointing = [i for i in items if i['rules']]
    total = lambda g: sum(i['count'] for i in g)
    chosen, tie = pointing, False
    if pointing and not set.intersection(*(set(i['rules']) for i in pointing)):
        by_rule = {}
        for i in pointing:
            for r in i['rules']:
                by_rule.setdefault(r, []).append(i)
        best = max(total(g) for g in by_rule.values())
        tops = [g for g in by_rule.values() if total(g) == best]
        tie = len({tuple(i['address'] for i in g) for g in tops}) > 1
        chosen = [] if tie else tops[0]
    rules = sorted(set.intersection(*(set(i['rules']) for i in chosen))) if chosen else []
    ids = [i['address'] for i in chosen]
    return {'addresses': ids, 'rules': rules, 'count': total(chosen), 'tie': tie,
            'rest': [i['address'] for i in items if i['address'] not in ids]}


def schema_dir():
    here = os.path.dirname(os.path.abspath(__file__))
    under = os.path.join(here, 'interface', 'artifacts')
    return under if os.path.isdir(under) else here


def schema(name):
    return y2j(read(os.path.join(schema_dir(), f'{name}.schema.yml')))


def is_type(d, t):
    # bool 은 int 의 하위 타입이라 integer·number 에서 뺀다
    return isinstance(d, TYPES[t]) and not (t in ('integer', 'number') and isinstance(d, bool))


def validate(s, d, path='$', root=None):
    # ponytail: 우리 스키마가 쓰는 JSON Schema 키워드만 다루는 검증기. 그 밖의 키워드(format 등)는 무시한다.
    # 기본 python 에 검증 라이브러리가 없어 팀원 PC 에 설치를 요구하지 않으려고 둔다. 키워드를 더 쓰게 되면 jsonschema 로 바꾼다
    root = s if root is None else root
    if '$ref' in s:
        node = root
        for p in s['$ref'].lstrip('#/').split('/'):
            node = node[p]
        return validate(node, d, path, root)
    types = s.get('type')
    if types is not None and not any(is_type(d, t) for t in ([types] if isinstance(types, str) else types)):
        return [f'{path}: {types} 이어야 하는데 {type(d).__name__}']
    errs = []
    if 'enum' in s and d not in s['enum']:
        errs.append(f'{path}: {s["enum"]} 중 하나여야 하는데 {d!r}')
    if 'const' in s and d != s['const']:
        errs.append(f'{path}: {s["const"]!r} 이어야 하는데 {d!r}')
    if isinstance(d, str):
        if len(d) < s.get('minLength', 0):
            errs.append(f'{path}: 비어 있다')
        if 'pattern' in s and not re.search(s['pattern'], d):
            errs.append(f'{path}: {s["pattern"]} 에 맞지 않는다')
    if isinstance(d, dict):
        errs += [f'{path}.{k}: 없다' for k in s.get('required', []) if k not in d]
        if len(d) < s.get('minProperties', 0):
            errs.append(f'{path}: 키가 {s["minProperties"]}개 이상이어야 한다')
        props, extra = s.get('properties', {}), s.get('additionalProperties')
        for k, v in d.items():
            if k in props:
                errs += validate(props[k], v, f'{path}.{k}', root)
            elif extra is False:
                errs.append(f'{path}.{k}: 허용되지 않는 키')
            elif isinstance(extra, dict):
                errs += validate(extra, v, f'{path}.{k}', root)
    if isinstance(d, list):
        if len(d) < s.get('minItems', 0):
            errs.append(f'{path}: {s["minItems"]}개 이상이어야 한다')
        if 'items' in s:
            for i, v in enumerate(d):
                errs += validate(s['items'], v, f'{path}[{i}]', root)
    for sub in s.get('allOf', []):
        errs += validate(sub, d, path, root)
    if 'anyOf' in s and not any(not validate(sub, d, path, root) for sub in s['anyOf']):
        errs.append(f'{path}: anyOf 중 하나도 맞지 않는다')
    if 'oneOf' in s:
        branch_errs = [validate(sub, d, path, root) for sub in s['oneOf']]
        n = sum(1 for e in branch_errs if not e)
        if n != 1:
            detail = ' / '.join('; '.join(e) for e in branch_errs if e)
            errs.append(f'{path}: oneOf 중 정확히 하나여야 하는데 {n}개가 맞는다 ({detail})')
    if 'not' in s and not validate(s['not'], d, path, root):
        errs.append(f'{path}: 함께 올 수 없는 키가 있다')
    return errs


def block(text):
    m = re.search(r'```yaml\n(.*?)\n```', text, re.S)
    first = text.lstrip().split('\n', 1)[0].strip()
    marker = first if first.startswith(MARKER_PREFIX) else None
    name = MARKER_SCHEMAS.get(marker[len(MARKER_PREFIX):-len(' -->')].strip()) if marker else None
    data, errors = None, None
    try:
        data = y2j(m.group(1)) if m else None
    except subprocess.CalledProcessError:
        errors = ['yaml 블록을 읽지 못했다']
    if name and errors is None:
        errors = validate(schema(name), data) if m else ['```yaml 블록이 없다']
    return {'marker': marker, 'schema': name, 'data': data, 'errors': errors}


def convert_or_error(d, lines, params, on, f=convert):
    try:
        return f(d, lines, params, on)
    except Rejected as e:
        return {'error': str(e)}


def arg(s):
    return json.load(sys.stdin) if s == '-' else json.loads(s)


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


COMMANDS = {
    'def': lambda r: definition(r[0], r[1]),
    'hits': lambda r: hits(definition(r[0], r[1]), arg(r[2]), r[3] if len(r) > 3 else None),
    'visible': lambda r: visible(definition(r[0], r[1]), arg(r[2])),
    'diff': lambda r: diff(definition(r[0], r[2]), definition(r[1], r[2]), r[3] if len(r) > 3 else None),
    'history': lambda r: history(*r),
    'api': lambda r: api(r[0], r[1], arg(r[2])),
    'url': lambda r: parse_url(r[0]),
    'values': lambda r: values(arg(r[0])),
    'fields': lambda r: fields(read(r[0])),
    'edited': lambda r: edited(read(r[0])),
    'metric': lambda r: metric(read(r[0])),
    'cluster': lambda r: cluster(arg(r[0])),
    'block': lambda r: block(read(r[0])),
    'key': lambda r: key(to_current(r[1], r[2])(arg(r[0])) if len(r) > 1 else arg(r[0])),
    'same': lambda r: same(definition(r[0], r[1]), to_current(r[0], r[1])(arg(r[2]))),
    'uses': lambda r: uses(definition(r[0], r[1]), r[2], r[3]),
    'hit': lambda r: hit(definition(r[0], r[1]), arg(r[2]), arg(r[3])),
    'ends': lambda r: ends(definition(r[0], r[1]), r[2], r[3]),
    'target': lambda r: target(definition(r[0], r[1]), arg(r[2]), r[3]),
    'retired': lambda r: retired(r[0], r[1]),
    'convert': lambda r: convert_or_error(definition(r[0], r[1]), retired(r[0], r[1]), arg(r[2]),
                                          date.fromisoformat(r[3]) if len(r) > 3 else today(), reread),
    'uses-question': lambda r: uses_question(retired(r[0], r[1]), arg(r[2]), arg(r[3]), merged_options(r[0], r[1])),
    'at': lambda r: at(*r),
    'days': lambda r: days(r[0]),
}


if __name__ == '__main__':
    cmd, *rest = sys.argv[1:]
    if cmd not in COMMANDS:
        sys.exit(f'알 수 없는 명령: {cmd}')
    print(json.dumps(COMMANDS[cmd](rest), ensure_ascii=False, indent=1))
