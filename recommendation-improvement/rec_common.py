#!/usr/bin/env python3
# 제품 추천 개선 예약 작업이 함께 쓰는 스크립트. 정의서 읽기·규칙 적중·이력·제품 추천 API·피드백 칸 읽기를 맡는다.
# 세 구성 요소의 예약 작업이 함께 쓴다. 구성 요소 전용 명령은 각 구성 요소 디렉터리의 rec.py 가 이 파일을 불러와 더한다.
#
# 레포는 환경 변수 REPO(없으면 현재 디렉터리). REF 는 git ref(origin/main 등)이거나 정의서 파일 경로다.
# ANSWERS 는 {"질문": ["선택지", ...]} JSON 이고, '-' 를 주면 표준 입력에서 읽는다.
#
#   rec_common.py def     REF PURPOSE                       정의서(JSON). 없으면 null
#   rec_common.py hits    REF PURPOSE ANSWERS               답 조합에 걸린 규칙(노출 조건을 적용한 답 기준)
#   rec_common.py visible REF PURPOSE ANSWERS               노출 조건을 적용한 답 조합
#   rec_common.py diff    REF_A REF_B PURPOSE [CRITERION]   두 정의서 사이에 다른 규칙(before=A, after=B)
#   rec_common.py history REF PURPOSE [SINCE_ISO]           first-parent 이력에서 규칙이 바뀐 커밋
#   rec_common.py api     BASE_URL PURPOSE ANSWERS          제품 추천 API 결과 {status, body}
#   rec_common.py url     RESULT_URL                        결과 화면 주소 → {category, purpose, answers}
#   rec_common.py values  RESPONSE_JSON                     API 응답 → 권장안별 항목 값·순위·근거
#   rec_common.py fields  BODY_FILE                         피드백 이슈 본문 → 칸 제목별 값
#   rec_common.py block   COMMENT_FILE                      산출물 댓글 → 표시(첫 줄), 첫 ```yaml 블록(JSON), 스키마 검증 오류(errors)
#
# 테스트는 test.py 에 있다.
# 스키마(interface/artifacts/*.schema.yml)는 이 파일과 같은 디렉터리나 그 아래 interface/artifacts/ 에서 찾는다.
import json, os, re, subprocess, sys, urllib.error, urllib.parse, urllib.request
from datetime import datetime, timezone

DEF_PATH = 'src/main/resources/recommendation/purposes/{}.yml'
Y2J = ('f=lambda{|o| case o when Hash then o.map{|k,v|[k.to_s,f.(v)]}.to_h when Array then o.map{|v|f.(v)} '
       'when Time then o.utc.iso8601 when Date then o.iso8601 else o end}; '
       'puts JSON.generate(f.(YAML.safe_load($stdin.read, permitted_classes: [Date, Time], aliases: true)))')
CPU_RANK = {'BASIC': 0, 'PRO': 1, 'MAX': 2, 'U': 0, 'P_HS': 1, 'H': 2, 'HX': 3}
ITEMS = ('REQUIRED_CPU', 'MEMORY', 'STORAGE')
RESULT_PREFIXES = ('https://devica.co.kr/result', 'https://2026-devica.vercel.app/result')
MARKER_PREFIX = '<!-- product-recommendation:'
# 댓글 표시 → 그 블록이 따라야 하는 스키마 (interface/binding.md)
MARKER_SCHEMAS = {'analysis': 'analysis-result', 'decision': 'improvement-instruction', 'result': 'improvement-result'}
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


def key(rule):
    return rule['criterion'] + ' ' + json.dumps(rule['when'], sort_keys=True, ensure_ascii=False, separators=(',', ':'))


def hits(d, a):
    v = visible(d, a)
    return [r for r in d['rules'] if ev(r['when'], v)]


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


def history(ref, purpose, since=None):
    # 커밋 시각(%cI)을 그 변경이 ref 에 들어온 시각으로 본다. main 은 릴리스마다 squash 커밋 하나다
    path = DEF_PATH.format(purpose)
    lines = git('log', '--first-parent', '--format=%H%x1f%cI%x1f%s', ref, '--', path).stdout.splitlines()
    out = []
    for line in lines:
        sha, time, subject = line.split('\x1f', 2)
        time = utc(time)
        if since and time < utc(since):
            continue
        parent = git('rev-parse', '--verify', '-q', sha + '^').stdout.strip()
        changes = diff(definition(parent, purpose) if parent else None, definition(sha, purpose))
        if changes:
            out.append({'commit': sha, 'time': time, 'subject': subject, 'changes': changes})
    return out


def api(base, purpose, a):
    q = urllib.parse.urlencode([(k, o) for k in sorted(a) for o in a[k]])
    url = f'{base.rstrip("/")}/api/usage-purposes/{purpose}/recommendation?{q}'
    req = urllib.request.Request(url, headers={'Accept': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return {'status': r.status, 'body': json.load(r)}
    except urllib.error.HTTPError as e:
        try:
            body = json.load(e)
        except Exception:
            body = None
        return {'status': e.code, 'body': body}


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
    data = y2j(m.group(1)) if m else None
    errors = None
    if name:
        errors = validate(schema(name), data) if m else ['```yaml 블록이 없다']
    return {'marker': marker, 'schema': name, 'data': data, 'errors': errors}


def arg(s):
    return json.load(sys.stdin) if s == '-' else json.loads(s)


def read(path):
    with open(path, encoding='utf-8') as f:
        return f.read()


COMMANDS = {
    'def': lambda r: definition(r[0], r[1]),
    'hits': lambda r: hits(definition(r[0], r[1]), arg(r[2])),
    'visible': lambda r: visible(definition(r[0], r[1]), arg(r[2])),
    'diff': lambda r: diff(definition(r[0], r[2]), definition(r[1], r[2]), r[3] if len(r) > 3 else None),
    'history': lambda r: history(*r),
    'api': lambda r: api(r[0], r[1], arg(r[2])),
    'url': lambda r: parse_url(r[0]),
    'values': lambda r: values(arg(r[0])),
    'fields': lambda r: fields(read(r[0])),
    'block': lambda r: block(read(r[0])),
}


if __name__ == '__main__':
    cmd, *rest = sys.argv[1:]
    if cmd not in COMMANDS:
        sys.exit(f'알 수 없는 명령: {cmd}')
    print(json.dumps(COMMANDS[cmd](rest), ensure_ascii=False, indent=1))
