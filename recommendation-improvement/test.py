#!/usr/bin/env python3
# common.py 와 구성 요소별 스크립트(signals.py, snapshot.py)의 테스트. 외부(git, API, PostHog)를 부르지 않는 함수만 본다.
#
#   python3 test.py
import importlib.util
import json
import os
import tempfile
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
os.sys.path.insert(0, HERE)

import common  # noqa: E402
from common import ITEMS, block, diff, ev, fields, hits, parse_url, result_url, schema, validate, values, visible  # noqa: E402


def load(component, script):
    # metric-collector/ 처럼 하이픈이 든 디렉터리는 패키지가 못 되므로 경로로 읽는다
    spec = importlib.util.spec_from_file_location(component, os.path.join(HERE, component, script))
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


collector = load('metric-collector', 'signals.py')
improver = load('recommendation-improver', 'snapshot.py')


def body(preferred='MAC', mac=('PRO', 16, 512), win=('P_HS', 16, 512), reasons=()):
    # 제품 추천 API 응답에서 values() 가 보는 부분만
    def spec(os_, cpu, mem, sto):
        return {'preferred': os_ == preferred, 'items': [
            {'code': 'OS', 'value': os_},
            {'code': 'REQUIRED_CPU', 'value': cpu, 'displayValue': cpu, 'reasons': list(reasons)},
            {'code': 'MEMORY', 'value': str(mem), 'displayValue': f'{mem}GB', 'reasons': list(reasons)},
            {'code': 'STORAGE', 'value': str(sto), 'displayValue': f'{sto}GB', 'reasons': []}]}
    return {'specs': [spec('MAC', *mac), spec('WINDOWS', *win)]}


def tmp(text):
    f = tempfile.NamedTemporaryFile('w', suffix='.json', delete=False, encoding='utf-8')
    f.write(text)
    f.close()
    return f.name


DEF = {
    'survey': {'steps': [{'questions': [
        {'code': 'SLOWDOWN'},
        {'code': 'VIDEO', 'showWhen': {'question': 'SLOWDOWN', 'anyOf': ['OFTEN']}}]}]},
    'rules': [
        {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['OFTEN']}, 'weight': 2},
        {'criterion': 'STORAGE', 'when': {'all': [{'question': 'VIDEO', 'anyOf': ['YES']},
                                                   {'not': {'question': 'SLOWDOWN', 'anyOf': ['NEVER']}}]}, 'weight': 1}],
}


class CommonTest(unittest.TestCase):
    def test_조건_평가(self):
        a = {'SLOWDOWN': ['OFTEN'], 'VIDEO': ['YES']}
        self.assertTrue(ev({'question': 'SLOWDOWN', 'anyOf': ['OFTEN', 'SOMETIMES']}, a))
        self.assertFalse(ev({'not': {'question': 'VIDEO', 'anyOf': ['YES']}}, a))
        self.assertTrue(ev({'any': [{'question': 'X', 'anyOf': ['Y']}, {'question': 'VIDEO', 'anyOf': ['YES']}]}, a))
        self.assertFalse(ev({'all': [{'question': 'X', 'anyOf': ['Y']}, {'question': 'VIDEO', 'anyOf': ['YES']}]}, a))
        self.assertRaises(ValueError, ev, {'bogus': 1}, a)

    def test_노출_조건을_만족하지_않는_답은_버린다(self):
        self.assertEqual(visible(DEF, {'SLOWDOWN': ['NEVER'], 'VIDEO': ['YES']}), {'SLOWDOWN': ['NEVER']})
        self.assertEqual(visible(DEF, {'VIDEO': ['YES']}), {})

    def test_걸린_규칙은_노출된_답으로_본다(self):
        self.assertEqual([r['criterion'] for r in hits(DEF, {'SLOWDOWN': ['OFTEN'], 'VIDEO': ['YES']})], ['MEMORY', 'STORAGE'])
        self.assertEqual(hits(DEF, {'SLOWDOWN': ['NEVER'], 'VIDEO': ['YES']}), [])

    def test_정의서_차이는_규칙_단위다(self):
        d2 = json.loads(json.dumps(DEF))
        d2['rules'][0]['weight'] = 3
        d2['rules'].pop(1)
        out = diff(DEF, d2)
        self.assertEqual([(c['key'][:6], c['before'] is None, c['after'] is None) for c in out], [('MEMORY', False, False), ('STORAG', False, True)])
        self.assertEqual(diff(DEF, DEF), [])
        self.assertEqual(diff(None, DEF)[0]['before'], None)

    def test_결과_주소_읽기와_만들기(self):
        u = 'https://devica.co.kr/result?category=LAPTOP&purpose=DEV&answer=SLOWDOWN.OFTEN&answer=VIDEO.YES&answer=VIDEO.YES'
        p = parse_url(u)
        self.assertEqual(p, {'category': 'LAPTOP', 'purpose': 'DEV', 'answers': {'SLOWDOWN': ['OFTEN'], 'VIDEO': ['YES']}})
        self.assertEqual(result_url(None, 'DEV', p['answers']),
                         'https://devica.co.kr/result?category=LAPTOP&purpose=DEV&answer=SLOWDOWN.OFTEN&answer=VIDEO.YES')

    def test_응답에서_권장안별_값과_순위(self):
        v = values(body(preferred='WINDOWS', mac=('MAX', 32, 1024), win=('U', 8, 256)))
        self.assertTrue(v['WINDOWS']['preferred'] and not v['MAC']['preferred'])
        self.assertEqual(v['MAC']['REQUIRED_CPU']['rank'], 2)
        self.assertEqual(v['WIN' 'DOWS']['MEMORY'], {'value': '8', 'display': '8GB', 'rank': 8, 'reasons': []})

    def test_이슈_본문_칸(self):
        f = fields('### 결과 화면 주소\n\nhttps://a\nhttps://b\n\n### 더 알려 주실 내용\n\n_No response_\n\n### 바꾼 답\n\nSLOWDOWN: NEVER → OFTEN\n')
        self.assertEqual(f, {'결과 화면 주소': 'https://a\nhttps://b', '더 알려 주실 내용': '', '바꾼 답': 'SLOWDOWN: NEVER → OFTEN'})

    def test_산출물_댓글은_표시와_블록을_읽고_실제_스키마로_검증한다(self):
        good = '<!-- product-recommendation:result -->\n```yaml\noutcome: NOT_OPENED\nreason: 기대와 다름\n```\n'
        out = block(good)
        self.assertEqual((out['schema'], out['data'], out['errors']), ('improvement-result', {'outcome': 'NOT_OPENED', 'reason': '기대와 다름'}, []))
        bad = block(good.replace('NOT_OPENED', 'PR_OPENED'))
        self.assertTrue(bad['errors'])
        self.assertEqual(block('<!-- product-recommendation:result -->\n표만 있음\n')['errors'], ['```yaml 블록이 없다'])
        self.assertEqual(block('그냥 댓글')['schema'], None)


# ---- 스키마 검증기 ----

SCHEMA = {
    'type': 'object', 'additionalProperties': False, 'required': ['kind', 'n'],
    'properties': {
        'kind': {'enum': ['A', 'B']}, 'n': {'type': 'integer'}, 'x': {}, 'y': {},
        'name': {'type': ['string', 'null'], 'minLength': 1},
        'tags': {'type': 'array', 'minItems': 1, 'items': {'type': 'string', 'pattern': '^t'}},
        'cond': {'$ref': '#/$defs/cond'},
        'w': {'type': 'object', 'required': ['from'], 'properties': {'from': {'type': 'integer'}},
              'oneOf': [{'required': ['to']}, {'required': ['direction']}]},
    },
    'allOf': [{'not': {'required': ['x', 'y']}}],
    '$defs': {'cond': {'oneOf': [
        {'type': 'object', 'additionalProperties': False, 'required': ['q'], 'properties': {'q': {'const': 'Q'}}},
        {'type': 'object', 'additionalProperties': False, 'required': ['all'],
         'properties': {'all': {'type': 'array', 'items': {'$ref': '#/$defs/cond'}}}},
    ]}},
}

VALID = [
    {'kind': 'A', 'n': 1},
    {'kind': 'B', 'n': 0, 'name': None, 'tags': ['t1'], 'cond': {'all': [{'q': 'Q'}]}, 'w': {'from': 1, 'to': 2}, 'x': 1},
]

# (데이터, 반드시 나와야 하는 오류 메시지의 앞부분)
INVALID = [
    ({}, ['$.kind: 없다', '$.n: 없다']),
    ({'kind': 'C', 'n': 1}, ['$.kind:']),
    ({'kind': 'A', 'n': True}, ['$.n:']),  # bool 은 integer 가 아니다
    ({'kind': 'A', 'n': 1, 'z': 1}, ['$.z: 허용되지 않는 키']),
    ({'kind': 'A', 'n': 1, 'name': ''}, ['$.name: 비어 있다']),
    ({'kind': 'A', 'n': 1, 'tags': []}, ['$.tags:']),
    ({'kind': 'A', 'n': 1, 'tags': ['x']}, ['$.tags[0]:']),
    ({'kind': 'A', 'n': 1, 'cond': {'q': 'X'}}, ['$.cond: oneOf']),
    ({'kind': 'A', 'n': 1, 'cond': {'all': [{'all': [{'q': 'X'}]}]}}, ['$.cond: oneOf']),
    ({'kind': 'A', 'n': 1, 'w': {'from': 1}}, ['$.w: oneOf']),
    ({'kind': 'A', 'n': 1, 'w': {'from': 1, 'to': 2, 'direction': 'UP'}}, ['$.w: oneOf']),
    ({'kind': 'A', 'n': 1, 'x': 1, 'y': 1}, ['$: 함께 올 수 없는 키']),
]


class ApiTest(unittest.TestCase):
    # 연결을 가짜로 바꿔, 끊기면 다시 보내는지만 본다(#259)
    def setUp(self):
        self.saved = (common.http.client.HTTPConnection, common.sleep)
        common.sleep = lambda s: None
        self.fails, self.sent = 0, []
        test = self

        class Conn:
            def __init__(self, host, timeout):
                pass

            def request(self, method, path, headers):
                test.sent.append(path)

            def getresponse(self):
                if len(test.sent) <= test.fails:
                    raise ConnectionResetError
                return type('R', (), {'status': 400, 'read': lambda r: b'{"code": "QUESTION_NOT_FOUND"}'})()

            def close(self):
                pass
        common.http.client.HTTPConnection = Conn

    def tearDown(self):
        common.http.client.HTTPConnection, common.sleep = self.saved

    def test_끊기면_다시_보내고_응답은_상태와_본문이다(self):
        self.fails = 2
        r = common.api('http://dev/', 'DEV', {'B': ['y'], 'A': ['x']})
        self.assertEqual(r, {'status': 400, 'body': {'code': 'QUESTION_NOT_FOUND'}})
        self.assertEqual(self.sent, ['/api/usage-purposes/DEV/recommendation?A=x&B=y'] * 3)

    def test_계속_끊기면_오류를_낸다(self):
        self.fails = common.API_TRIES
        with self.assertRaises(ConnectionResetError):
            common.api('http://dev', 'DEV', {})
        self.assertEqual(len(self.sent), common.API_TRIES)


class ValidateTest(unittest.TestCase):
    def test_실제_스키마의_예시는_통과한다(self):
        for name in ('improvement-instruction', 'analysis-result'):
            for data in schema(name)['examples']:
                with self.subTest(name=name):
                    self.assertEqual(validate(schema(name), data), [])

    def test_결과_값을_바꾸는_지시는_모든_사례에_기대_결과가_있어야_한다(self):
        s = schema('improvement-instruction')
        ins = json.loads(json.dumps(s['examples'][0]))
        del ins['cases'][1]['expected']
        self.assertTrue(validate(s, ins))
        ins['cases'][1]['expected'] = []
        self.assertTrue(validate(s, ins))
        ins['change'] = {'reason': {'from': '옛 문장', 'to': '새 문장'}}
        for c in ins['cases']:
            c.pop('expected')
        self.assertEqual(validate(s, ins), [])

    def test_맞는_데이터는_통과한다(self):
        for data in VALID:
            with self.subTest(data=data):
                self.assertEqual(validate(SCHEMA, data), [])

    def test_틀린_데이터는_어디가_틀렸는지_돌려준다(self):
        for data, expected in INVALID:
            with self.subTest(data=data):
                got = validate(SCHEMA, data)
                for e in expected:
                    self.assertTrue(any(g.startswith(e) for g in got), f'{e!r} 가 없다: {got}')


# ---- Metric Collector ----

class CollectorTest(unittest.TestCase):
    def test_세션_행_읽기(self):
        v = collector.parse_view(['1700000000.5', 'recommendation_result_viewed', '/result/', 'DEV', '', 'SLOWDOWN.OFTEN&VIDEO.YES&VIDEO.YES&bad'])
        self.assertEqual(v, {'ts': 1700000000, 'event': 'recommendation_result_viewed', 'path': '/result', 'purpose': 'DEV',
                             'category': None, 'answers': {'SLOWDOWN': ['OFTEN'], 'VIDEO': ['YES']}})

    def test_바꾼_답_줄과_되돌리기(self):
        first = {'SLOWDOWN': ['NEVER'], 'VIDEO': ['YES']}
        last = {'SLOWDOWN': ['OFTEN'], 'GPU': ['YES']}
        lines = collector.changed_lines(first, last)
        self.assertEqual(lines, ['GPU: - → YES', 'SLOWDOWN: NEVER → OFTEN', 'VIDEO: YES → -'])
        self.assertEqual(collector.apply_lines(first, lines), {'GPU': ['YES'], 'SLOWDOWN': ['OFTEN']})

    def test_권장안이_있으면_그_권장안의_항목_변화가_신호다(self):
        v1 = values(body('MAC', mac=('PRO', 32, 512)))
        v2 = values(body('MAC', mac=('PRO', 16, 256)))
        self.assertEqual(collector.compare(v1, v2), ([('MAC', 'MEMORY', 'DOWN'), ('MAC', 'STORAGE', 'DOWN')], None))

    def test_신호가_아닌_경우(self):
        v = lambda **k: values(body(**k))
        self.assertEqual(collector.compare(v(preferred='MAC'), v(preferred='WINDOWS'))[1], '권장안이 달라짐')
        self.assertEqual(collector.compare(v(), v())[1], '바뀐 항목 없음')
        self.assertEqual(collector.compare(v(mac=('PRO', 16, 512)), v(mac=('MAX', 8, 512)))[1], '항목이 반대로 바뀜')

    def test_권장안이_없으면_두_권장안을_함께_본다(self):
        v1 = values(body(preferred=None, mac=('PRO', 16, 512), win=('P_HS', 16, 512)))
        v2 = values(body(preferred=None, mac=('PRO', 32, 512), win=('P_HS', 32, 1024)))
        self.assertEqual(collector.compare(v1, v2), ([('BOTH', 'MEMORY', 'UP'), ('WINDOWS', 'STORAGE', 'UP')], None))

    def test_권장안이_없을_때_버리는_경우(self):
        v = lambda **k: values(body(preferred=None, **k))
        self.assertEqual(collector.compare(v(), v())[1], '바뀐 항목 없음')
        self.assertEqual(collector.compare(v(), v(mac=('MAX', 8, 512)))[1], '항목이 반대로 바뀜')
        self.assertEqual(collector.compare(v(), v(mac=('MAX', 16, 512), win=('U', 16, 512)))[1], '항목이 반대로 바뀜')

    def test_세션_파일은_두_모양을_받고_시각순으로_정렬한다(self):
        a = ['20', 'e', '/result', 'DEV', 'LAPTOP', 'Q.A']
        b = ['10', 'e', '/result', 'DEV', 'LAPTOP', 'Q.B']
        for data in [[[a, b]], {'results': [[[a, b]]]}]:
            with self.subTest(data=data):
                out = collector.load_sessions(tmp(json.dumps(data)))
                self.assertEqual([v['ts'] for v in out[0]], [10, 20])

    def test_같은_신호는_같은_키(self):
        k = lambda lines: collector.signal_key('DEV', 'Mac', collector.problem('MEMORY', 'DOWN'), lines)
        self.assertEqual(k(['A: x → y']), k(['A: x → y']))
        self.assertNotEqual(k(['A: x → y']), k(['A: x → z']))
        self.assertEqual(collector.problem('REQUIRED_CPU', 'UP'), 'CPU 가 부족해요')


class SignalsTest(unittest.TestCase):
    # signals·issue 가 부르는 git·운영 API 를 가짜로 바꿔 끼운다
    def setUp(self):
        self.saved = {k: getattr(collector, k) for k in ('api', 'definition', 'history')}
        self.develop = DEF
        self.deployed = []
        collector.definition = lambda ref, purpose: DEF if ref == collector.MAIN else self.develop
        collector.history = lambda ref, purpose, since=None: self.deployed
        # SLOWDOWN 이 NEVER 면 메모리 32GB, 아니면 16GB
        collector.api = lambda base, purpose, a: {'status': 200, 'body': body(
            mac=('PRO', 32 if 'NEVER' in a.get('SLOWDOWN', []) else 16, 512))}

    def tearDown(self):
        for k, v in self.saved.items():
            setattr(collector, k, v)

    def session(self, *answer_sets, start=None, purpose='DEV'):
        t = start or int(time.time()) - 3600
        return [[str(t + i), collector.RESULT_EVENT, '/result', purpose, 'LAPTOP', p] for i, p in enumerate(answer_sets)]

    def run_signals(self, *sessions):
        return collector.signals(tmp(json.dumps(list(sessions))), 'https://api', 28)

    def test_답을_바꿔_다시_본_세션이_같은_신호로_묶인다(self):
        out = self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'),
                               self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))
        self.assertEqual(out['dropped'], {})
        g, = out['groups']
        self.assertEqual((g['count'], g['plan'], g['problem'], g['changed']), (2, 'Mac', '메모리가 과해요', ['SLOWDOWN: NEVER → SOMETIMES']))
        self.assertEqual(g['urls'], ['https://devica.co.kr/result?category=LAPTOP&purpose=DEV&answer=SLOWDOWN.NEVER'])
        self.assertEqual(g['title'], '[제품 추천 지표] Mac 권장안에서 메모리가 과해요')
        self.assertIn('- 건수: 2', g['body'])
        self.assertEqual(fields(g['body'])['바꾼 답'], 'SLOWDOWN: NEVER → SOMETIMES')

    def test_버리는_세션(self):
        old = int(time.time()) - 40 * 86400
        out = self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES', start=old),
                               self.session('SLOWDOWN.NEVER', 'SLOWDOWN.NEVER'),
                               self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES', purpose=''),
                               self.session('SLOWDOWN.SOMETIMES', 'SLOWDOWN.OFTEN'))
        self.assertEqual(out['groups'], [])
        self.assertEqual(out['dropped'], {'조회 기간 전에 시작한 세션': 1, '다른 답 조합의 결과를 두 번 이상 보지 않음': 1,
                                          '처음과 마지막의 사용 목적이 다르거나 없음': 1, '바뀐 항목 없음': 1})

    def test_운영_서버_오류는_질문이_바뀐_것과_구분하고_불완전으로_표시한다(self):
        session = self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES')
        self.assertFalse(self.run_signals(session)['incomplete'])
        collector.api = lambda base, purpose, a: {'status': 400, 'body': None}
        out = self.run_signals(session)
        self.assertEqual((out['dropped'], out['incomplete']), ({'운영 결과를 받지 못함(질문이 바뀜)': 1}, False))
        for status in (500, 429):
            collector.api = lambda base, purpose, a: {'status': status, 'body': None}
            out = self.run_signals(session)
            self.assertEqual((out['dropped'], out['incomplete']), ({'운영 서버 오류(일시적일 수 있음)': 1}, True))

    def test_배포를_기다리는_규칙에_걸린_세션은_세지_않는다(self):
        self.develop = json.loads(json.dumps(DEF))
        self.develop['rules'].append({'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['SOMETIMES']}, 'weight': 1})
        out = self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))
        self.assertEqual(out['dropped'], {'배포를 기다리는 규칙에 걸림': 1})

    def test_배포_전에_본_바뀐_규칙에_걸린_세션은_세지_않는다(self):
        change = {'key': 'k', 'before': None, 'after': {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['SOMETIMES']}}}
        now = int(time.time())
        self.deployed = [{'time': collector.iso(now - 60), 'changes': [change]}]
        self.assertEqual(self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))['dropped'], {'배포 전에 본 바뀐 규칙에 걸림': 1})
        self.deployed = [{'time': collector.iso(now - 7200), 'changes': [change]}]
        self.assertEqual(len(self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))['groups']), 1)

    def test_열린_지표_피드백의_신호_키와_배포를_기다리는_규칙(self):
        self.develop = json.loads(json.dumps(DEF))
        self.develop['rules'][0]['weight'] = 5  # SLOWDOWN.OFTEN 규칙
        body_text = ('### 결과 화면 주소\n\nhttps://devica.co.kr/result?category=LAPTOP&purpose=DEV&answer=SLOWDOWN.NEVER\n\n'
                     '### 어느 권장안인가요?\n\nMac\n\n### 어떤 점이 맞지 않았나요?\n\n메모리가 과해요\n\n### 바꾼 답\n\nSLOWDOWN: NEVER → OFTEN\n')
        out = collector.issue(tmp(body_text))
        self.assertEqual(out['key'], collector.signal_key('DEV', 'Mac', '메모리가 과해요', ['SLOWDOWN: NEVER → OFTEN']))
        self.assertEqual(out['pending'], [c['key'] for c in diff(DEF, self.develop)])
        self.assertEqual(collector.issue(tmp('### 결과 화면 주소\n\n_No response_\n'))['error'], '결과 화면 주소가 없다')


# ---- Recommendation Improver ----

def snapshot(cases):
    return '[\n' + ',\n'.join('  ' + json.dumps(c, ensure_ascii=False, separators=(',', ':')) for c in cases) + '\n]\n'


INSTRUCTION = {
    'issues': [251], 'rule': {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['OFTEN']}},
    'change': {'weight': {'direction': 'DOWN'}},
    'cases': [{'issue': 251, 'answers': {'SLOWDOWN': ['OFTEN']},
               'expected': [{'plan': 'MAC', 'item': 'MEMORY', 'direction': 'DOWN'}]},
              {'issue': 251, 'answers': {'SLOWDOWN': ['OFTEN'], 'VIDEO': ['YES']},
               'expected': [{'plan': 'MAC', 'item': 'MEMORY', 'direction': 'DOWN'}]}],
}


class ImproverTest(unittest.TestCase):
    def test_사례_이름은_이슈_번호와_순번(self):
        self.assertEqual(improver.case_names(INSTRUCTION), ['피드백 #251', '피드백 #251 (2)'])

    def test_사례는_이름_있는_사례_뒤에_끼우고_있는_것은_건너뛴다(self):
        path = tmp(snapshot([{'name': '대표 사용자', 'answers': {}, 'response': None},
                             {'name': '피드백 #251', 'answers': {}, 'response': None},
                             {'answers': {'X': ['Y']}, 'response': None}]))
        self.assertEqual(improver.add_cases(path, tmp(json.dumps(INSTRUCTION))), ['피드백 #251 (2)'])
        names = [c.get('name') for c in improver.snapshot_lines(path)]
        self.assertEqual(names, ['대표 사용자', '피드백 #251', '피드백 #251 (2)', None])
        self.assertEqual(improver.add_cases(path, tmp(json.dumps(INSTRUCTION))), [])

    def test_모든_사례에_이름이_있으면_맨_뒤에_끼운다(self):
        path = tmp(snapshot([{'name': '대표 사용자', 'answers': {}, 'response': None}]))
        ins = dict(INSTRUCTION, cases=[{'issue': 300, 'answers': {'A': ['B']}}])
        self.assertEqual(improver.add_cases(path, tmp(json.dumps(ins))), ['피드백 #300'])
        self.assertEqual([c.get('name') for c in improver.snapshot_lines(path)], ['대표 사용자', '피드백 #300'])

    def test_조건의_답_쌍은_중첩을_편다(self):
        c = {'all': [{'question': 'A', 'anyOf': ['X', 'Y']}, {'not': {'question': 'B', 'anyOf': ['Z']}}]}
        self.assertEqual(improver.when_pairs(c), ['A.X', 'A.Y', 'B.Z'])

    def test_기대한_방향으로_바뀌면_통과(self):
        before = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body(mac=('PRO', 32, 512))},
                           {'name': '피드백 #251 (2)', 'answers': {}, 'response': body(mac=('PRO', 32, 512))},
                           {'name': '대표 사용자', 'answers': {'SLOWDOWN': ['OFTEN']}, 'response': body(mac=('PRO', 32, 512))},
                           {'answers': {'SLOWDOWN': ['NEVER']}, 'response': body()}])
        after = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body(mac=('PRO', 16, 512))},
                          {'name': '피드백 #251 (2)', 'answers': {}, 'response': body(mac=('PRO', 16, 512))},
                          {'name': '대표 사용자', 'answers': {'SLOWDOWN': ['OFTEN']}, 'response': body(mac=('PRO', 16, 512))},
                          {'answers': {'SLOWDOWN': ['NEVER']}, 'response': body()}])
        out = improver.compare(tmp(before), tmp(after), tmp(json.dumps(INSTRUCTION)))
        self.assertTrue(out['ok'], out['checks'])
        self.assertEqual(out['rows'][0]['plans']['MAC']['MEMORY'], '32GB → 16GB')
        self.assertEqual(out['impact'], {'total': 4, 'value_changed': 3, 'reason_only': 0,
                                         'value_changed_by_answer': {'조건 밖': 2, 'SLOWDOWN.OFTEN': 1},
                                         'personas_changed': ['대표 사용자']})

    def test_기대와_다르면_실패(self):
        before = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body()},
                           {'name': '피드백 #251 (2)', 'answers': {}, 'response': body()}])
        out = improver.compare(tmp(before), tmp(before), tmp(json.dumps(INSTRUCTION)))
        self.assertFalse(out['ok'])
        self.assertIn('MAC MEMORY 이 DOWN 쪽으로 바뀌지 않았다', out['checks'][0]['why'])
        other = snapshot([{'name': '대표 사용자', 'answers': {}, 'response': body()}])
        missing = improver.compare(tmp(other), tmp(other), tmp(json.dumps(INSTRUCTION)))
        self.assertEqual([c['why'] for c in missing['checks']], ['스냅숏에 사례가 없다'] * 2)

    def test_결과_값을_바꾸는_지시에_기대_결과가_없으면_실패(self):
        ins = dict(INSTRUCTION, cases=[{'issue': 251, 'answers': {'SLOWDOWN': ['OFTEN']}}])
        same = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body()}])
        out = improver.compare(tmp(same), tmp(same), tmp(json.dumps(ins)))
        self.assertFalse(out['ok'])
        self.assertIn('기대 결과(expected)가 없다', out['checks'][0]['why'])

    def test_근거_문장만_바꾸는_지시는_값이_바뀌면_실패(self):
        ins = dict(INSTRUCTION, change={'reason': {'from': '옛 문장', 'to': '새 문장'}},
                   cases=[{'issue': 251, 'answers': {'SLOWDOWN': ['OFTEN']}}])
        before = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body(reasons=['옛 문장'])}])
        good = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body(reasons=['새 문장'])}])
        bad = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body(mac=('MAX', 16, 512), reasons=['새 문장'])}])
        self.assertTrue(improver.compare(tmp(before), tmp(good), tmp(json.dumps(ins)))['ok'])
        out = improver.compare(tmp(before), tmp(bad), tmp(json.dumps(ins)))
        self.assertIn('근거 문장만 바꾸는 지시인데 결과 값이 바뀌었다', out['checks'][0]['why'])
        same = improver.compare(tmp(before), tmp(before), tmp(json.dumps(ins)))
        self.assertIn('MAC 의 MEMORY 근거 문장이 지시한 문장으로 바뀌지 않았다', same['checks'][0]['why'])

    def test_다른_피드백_사례의_변화는_따로_돌려준다(self):
        before = snapshot([{'name': '피드백 #251', 'answers': {}, 'response': body(mac=('PRO', 32, 512))},
                           {'name': '피드백 #251 (2)', 'answers': {}, 'response': body(mac=('PRO', 32, 512))},
                           {'name': '피드백 #300', 'answers': {}, 'response': body(mac=('PRO', 32, 512))}])
        after = before.replace('"32"', '"16"').replace('32GB', '16GB')
        out = improver.compare(tmp(before), tmp(after), tmp(json.dumps(INSTRUCTION)))
        self.assertTrue(out['ok'])
        self.assertEqual([r['case'] for r in out['other_feedback_rows']], ['피드백 #300'])


if __name__ == '__main__':
    unittest.main()
