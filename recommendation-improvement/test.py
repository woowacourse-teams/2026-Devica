#!/usr/bin/env python3
# common.py 와 구성 요소별 스크립트(signals.py, snapshot.py)의 테스트. 외부(API, PostHog)를 부르지 않는 함수만 본다.
# run.sh 는 임시 git 저장소와 가짜 claude 로 준비·잠금만 본다.
#
#   python3 test.py
import datetime
import importlib.util
import json
import os
import shutil
import subprocess
import tempfile
import time
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
os.sys.path.insert(0, HERE)

os.sys.path.insert(0, os.path.join(HERE, 'recommendation-improver'))
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
survey = load('recommendation-improver', 'survey.py')


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

    def test_권장안을_주면_다른_OS_전용_규칙은_빠진다(self):
        d = {**DEF, 'rules': [{**r, 'os': 'MAC'} if r['criterion'] == 'STORAGE' else r for r in DEF['rules']]}
        a = {'SLOWDOWN': ['OFTEN'], 'VIDEO': ['YES']}
        self.assertEqual([r['criterion'] for r in hits(d, a, 'WINDOWS')], ['MEMORY'])
        self.assertEqual([r['criterion'] for r in hits(d, a, 'MAC')], ['MEMORY', 'STORAGE'])
        self.assertEqual([r['criterion'] for r in hits(d, a)], ['MEMORY', 'STORAGE'])

    def test_규칙_구분은_조건_안의_순서를_무시하고_os_가_다르면_다르다(self):
        a = {'criterion': 'MEMORY', 'when': {'all': [{'question': 'A', 'anyOf': ['X', 'Y']}, {'question': 'B', 'anyOf': ['Z']}]}}
        b = {'criterion': 'MEMORY', 'when': {'all': [{'question': 'B', 'anyOf': ['Z']}, {'question': 'A', 'anyOf': ['Y', 'X']}]}}
        self.assertEqual(common.key(a), common.key(b))
        self.assertNotEqual(common.key(a), common.key({**a, 'os': 'WINDOWS'}))
        self.assertNotEqual(common.key(a), common.key({**a, 'criterion': 'STORAGE'}))
        nested = {'criterion': 'MEMORY', 'when': {'all': [{'all': [{'question': 'A', 'anyOf': ['X', 'Y']}]}, {'question': 'B', 'anyOf': ['Z']}]}}
        self.assertEqual(common.key(a), common.key(nested))
        single = {'criterion': 'MEMORY', 'when': {'all': [{'question': 'B', 'anyOf': ['Z']}]}}
        self.assertEqual(common.key(single), common.key({'criterion': 'MEMORY', 'when': {'question': 'B', 'anyOf': ['Z']}}))

    def test_기댄_답으로_더할지_넓힐지(self):
        mem = lambda gb: {'criterion': 'MEMORY', 'when': {'all': [{'question': 'CURRENT_MEMORY', 'anyOf': [gb]},
                                                                   {'question': 'SLOWDOWN', 'anyOf': ['OFTEN']}]}}
        self.assertEqual(common.target(DEF, mem('GB_32'), 'CURRENT_MEMORY'), {'kind': 'add', 'rule': None})
        self.assertEqual(common.target(DEF, mem('GB_32'), 'SLOWDOWN')['kind'], 'seen')
        d = {**DEF, 'rules': DEF['rules'] + [{**mem('GB_32'), 'weight': 1}]}
        self.assertEqual(common.target(d, mem('GB_32'), 'CURRENT_MEMORY')['kind'], 'exists')
        widen = common.target(d, mem('GB_24'), 'CURRENT_MEMORY')
        self.assertEqual((widen['kind'], widen['rule']['weight']), ('widen', 1))
        both = {**DEF, 'rules': DEF['rules'] + [{**mem('GB_16'), 'when': {'all': [
            {'question': 'CURRENT_MEMORY', 'anyOf': ['GB_16', 'GB_32']}, {'question': 'SLOWDOWN', 'anyOf': ['OFTEN']}]}, 'weight': 1}]}
        self.assertEqual(common.target(both, mem('GB_32'), 'CURRENT_MEMORY')['kind'], 'exists')
        sometimes = {'criterion': 'MEMORY', 'when': {'all': [{'question': 'CURRENT_MEMORY', 'anyOf': ['GB_32']},
                                                             {'question': 'SLOWDOWN', 'anyOf': ['SOMETIMES']}]}}
        self.assertEqual(common.target(d, sometimes, 'CURRENT_MEMORY')['kind'], 'widen')
        other = {**DEF, 'rules': d['rules'] + [{'criterion': 'MEMORY', 'when': {'question': 'CURRENT_MEMORY', 'anyOf': ['GB_8']}, 'weight': 2}]}
        self.assertEqual(common.target(other, mem('GB_24'), 'CURRENT_MEMORY')['kind'], 'seen')
        mapped = {**DEF, 'mappings': [{'criterion': 'CURRENT_CPU_TIER', 'value': 'X', 'when': {'question': 'CURRENT_MEMORY', 'anyOf': ['GB_8']}}]}
        self.assertEqual(common.target(mapped, mem('GB_32'), 'CURRENT_MEMORY')['kind'], 'seen')

    def test_같은_규칙과_같은_질문들로_된_규칙(self):
        d = {**DEF, 'rules': DEF['rules'] + [
            {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['SOMETIMES']}, 'weight': 1},
            {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['NEVER']}, 'weight': -1, 'os': 'MAC'}]}
        out = common.same(d, {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['OFTEN']}})
        self.assertEqual([r['weight'] for r in out['same']], [2])
        self.assertEqual([r['weight'] for r in out['same_questions']], [1])
        new = common.same(d, {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['FINE']}, 'os': 'MAC'})
        self.assertEqual((new['same'], [r['weight'] for r in new['same_questions']]), ([], [-1]))

    def test_답을_보는_규칙은_점수_0_과_매핑도_포함한다(self):
        d = {**DEF, 'rules': DEF['rules'] + [{'criterion': 'MEMORY', 'when': {'question': 'VIDEO', 'anyOf': ['YES']}, 'weight': 0}],
             'mappings': [{'criterion': 'CURRENT_CPU_TIER', 'value': 'BASIC', 'when': {'question': 'CPU', 'anyOf': ['BASIC']}}]}
        self.assertEqual([r['weight'] for r in common.uses(d, 'VIDEO', 'MEMORY')['rules']], [0])
        self.assertEqual(common.uses(d, 'VIDEO', 'REQUIRED_CPU'), {'rules': [], 'mappings': []})
        self.assertEqual(len(common.uses(d, 'CPU', 'REQUIRED_CPU')['mappings']), 1)

    def test_끝_구간은_점수_구간과_Mac_CPU_상한으로_정한다(self):
        d = {'interpretation': {'cpu': {'MAC': [{'upTo': 2, 'value': 'BASIC'}, {'upTo': 4, 'value': 'PRO'}, {'value': 'MAX'}],
                                        'WINDOWS': [{'upTo': 2, 'value': 'P_HS'}, {'value': 'HX'}]},
                                'memory': [{'upTo': 1, 'value': 16}, {'value': 48}], 'storage': [{'value': 512}],
                                'macCpuLimit': 'PRO'}}
        self.assertEqual(common.ends(d, 'MAC', 'REQUIRED_CPU')['max'], {'value': 'PRO', 'rank': 1})
        self.assertEqual(common.ends(d, 'WINDOWS', 'REQUIRED_CPU')['max'], {'value': 'HX', 'rank': 3})
        self.assertEqual((common.ends(d, 'MAC', 'MEMORY')['min']['rank'], common.ends(d, 'MAC', 'MEMORY')['max']['rank']), (16, 48))

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

    def test_고친_값_칸은_주소_번호와_항목과_rank_로_읽는다(self):
        text = '### 고친 값\n\n주소 1: 메모리: 24GB → 16GB (2건)\n주소 2: 저장 공간: 512GB → 1TB (1건)\nCPU: PRO → BASIC (1건)\n이상한 줄\n'
        self.assertEqual(common.edited(text), [
            {'address': 1, 'item': 'MEMORY', 'recommended': {'value': '24GB', 'rank': 24}, 'edited': {'value': '16GB', 'rank': 16}, 'count': 2},
            {'address': 2, 'item': 'STORAGE', 'recommended': {'value': '512GB', 'rank': 512}, 'edited': {'value': '1TB', 'rank': 1024}, 'count': 1},
            {'address': 1, 'item': 'REQUIRED_CPU', 'recommended': {'value': 'PRO', 'rank': 1}, 'edited': {'value': 'BASIC', 'rank': 0}, 'count': 1}])
        self.assertEqual(common.edited('### 바꾼 답\n\nA: x → y\n'), [])
        self.assertEqual(common.edited('### 고친 값\n\nCPU: PRO → ULTRA (1건)\n메모리: 큰 → 16GB (1건)\n'), [])

    def test_지표_피드백의_주소별_정보(self):
        text = ('### 결과 화면 주소\n\nhttps://devica.co.kr/result?purpose=DEV&answer=A.x\nhttps://devica.co.kr/result?purpose=DEV&answer=A.y\n\n'
                '### 바꾼 답\n\n주소 1: A: x → z\n주소 2: A: y → z\n주소 2: B: - → q\n\n### 근거 지표\n\n- 건수: 3\n- 주소별 건수: 2, 1\n')
        m = common.metric(text)
        self.assertEqual([(u['address'], u['answers']) for u in m['urls']], [(1, {'A': ['x']}), (2, {'A': ['y']})])
        self.assertEqual(m['counts'], [2, 1])
        self.assertEqual(m['changed'], [{'address': 1, 'lines': ['A: x → z']}, {'address': 2, 'lines': ['A: y → z', 'B: - → q']}])
        # 주소별 건수가 없는 옛 본문은 주소가 하나일 때만 건수를 쓴다
        old = '### 결과 화면 주소\n\nhttps://devica.co.kr/result?purpose=DEV&answer=A.x\n\n### 바꾼 답\n\nA: x → z\n\n### 근거 지표\n\n- 건수: 2\n'
        m = common.metric(old)
        self.assertEqual((m['counts'], m['changed']), ([2], [{'address': 1, 'lines': ['A: x → z']}]))

    def test_주소_묶음은_교집합이_비면_가장_많은_건수가_함께_가리키는_규칙으로_묶는다(self):
        c = common.cluster
        # 전체 교집합이 있으면 그것
        self.assertEqual(c([{'address': 1, 'count': 2, 'rules': ['r1', 'r2']}, {'address': 2, 'count': 2, 'rules': ['r1']}]),
                         {'addresses': [1, 2], 'rules': ['r1'], 'count': 4, 'tie': False, 'rest': []})
        # 비면 가장 큰 묶음(A·B·C 의 r1, 3건)이고 D·E(r3, 2건)는 밖
        items = [{'address': 1, 'count': 1, 'rules': ['r1', 'r2']}, {'address': 2, 'count': 1, 'rules': ['r1', 'r4']},
                 {'address': 3, 'count': 1, 'rules': ['r1']}, {'address': 4, 'count': 1, 'rules': ['r3']},
                 {'address': 5, 'count': 1, 'rules': ['r3', 'r2']}, {'address': 6, 'count': 1, 'rules': []}]
        self.assertEqual(c(items), {'addresses': [1, 2, 3], 'rules': ['r1'], 'count': 3, 'tie': False, 'rest': [4, 5, 6]})
        # 묶음의 교집합이 둘이면 둘 다 돌려 준다(판단은 보류)
        self.assertEqual(c([{'address': 1, 'count': 1, 'rules': ['r1', 'r2']}, {'address': 2, 'count': 1, 'rules': ['r1', 'r2']},
                            {'address': 3, 'count': 1, 'rules': ['r3']}])['rules'], ['r1', 'r2'])
        # 가장 큰 건수가 서로 다른 주소 묶음에서 나오면 tie
        out = c([{'address': 1, 'count': 2, 'rules': ['r1']}, {'address': 2, 'count': 2, 'rules': ['r2']}])
        self.assertEqual((out['tie'], out['rules'], out['rest']), (True, [], [1, 2]))
        # 규칙 객체도 받는다
        rule = {'criterion': 'MEMORY', 'when': {'question': 'Q', 'anyOf': ['a']}, 'weight': 1}
        self.assertEqual(c([{'address': 1, 'count': 2, 'rules': [rule]}])['rules'], [common.key(rule)])

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
        self.assertEqual(block('<!-- product-recommendation:survey-result -->\n```yaml\noutcome: [\n```\n')['errors'], ['yaml 블록을 읽지 못했다'])
        self.assertEqual(block('<!-- product-recommendation:survey-decision -->\n```yaml\na: 1\n```\n')['schema'], 'survey-instruction')


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
        for name in ('improvement-instruction', 'analysis-result', 'survey-instruction', 'survey-analysis', 'handover'):
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

    def test_규칙_추가_지시는_새_근거_문장만_있고_다른_변경과_함께_오지_않는다(self):
        s = schema('improvement-instruction')
        add = next(e for e in s['examples'] if 'add' in e['change'])
        self.assertEqual(validate(s, add), [])
        bad = [
            {'add': {'direction': 'UP'}},
            {'add': {'direction': 'UP'}, 'reason': {'from': '옛 문장', 'to': '새 문장'}},
            {'add': {'direction': 'UP'}, 'weight': {'from': 1, 'to': 2}, 'reason': {'to': '새 문장'}},
            {'weight': {'from': 1, 'to': 2}, 'reason': {'to': '새 문장'}},
        ]
        for change in bad:
            with self.subTest(change=change):
                self.assertTrue(validate(s, {**add, 'change': change}))
        no_expected = json.loads(json.dumps(add))
        no_expected['cases'][0].pop('expected')
        self.assertTrue(validate(s, no_expected))
        self.assertTrue(validate(s, {**add, 'rule': {**add['rule'], 'os': 'LINUX'}}))
        self.assertEqual(validate(s, {**add, 'rule': {**add['rule'], 'os': 'WINDOWS'}}), [])

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
        k = lambda item, d: collector.signal_key('DEV', 'Mac', collector.problem(item, d))
        self.assertEqual(k('MEMORY', 'DOWN'), k('MEMORY', 'DOWN'))
        self.assertNotEqual(k('MEMORY', 'DOWN'), k('MEMORY', 'UP'))
        self.assertNotEqual(k('MEMORY', 'DOWN'), collector.edit_key('DEV', 'Mac', collector.problem('MEMORY', 'DOWN')))
        self.assertEqual(collector.problem('REQUIRED_CPU', 'UP'), 'CPU 가 부족해요')


class SignalsTest(unittest.TestCase):
    # signals·issue 가 부르는 git·운영 API 를 가짜로 바꿔 끼운다
    def setUp(self):
        self.saved = {k: getattr(collector, k) for k in ('api', 'definition', 'history', 'interpretation_history')}
        self.main = DEF
        self.develop = DEF
        self.deployed = []
        self.interpretation_deployed = []
        collector.definition = lambda ref, purpose: self.main if ref == collector.MAIN else self.develop
        collector.history = lambda ref, purpose, since=None: self.deployed
        collector.interpretation_history = lambda ref, purpose, since=None: self.interpretation_deployed
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
        self.assertEqual((g['count'], g['plan'], g['problem']), (2, 'Mac', '메모리가 과해요'))
        self.assertEqual(g['changed'], [{'address': 1, 'lines': ['SLOWDOWN: NEVER → SOMETIMES']}])
        self.assertEqual(g['urls'], ['https://devica.co.kr/result?category=LAPTOP&purpose=DEV&answer=SLOWDOWN.NEVER'])
        self.assertEqual(g['title'], '[제품 추천 지표] Mac 권장안에서 메모리가 과해요')
        self.assertIn('- 건수: 2\n- 주소별 건수: 2', g['body'])
        self.assertEqual(fields(g['body'])['바꾼 답'], '주소 1: SLOWDOWN: NEVER → SOMETIMES')

    def test_답_조합이_달라도_같은_항목_방향이면_한_신호로_묶이고_주소는_따로_적는다(self):
        out = self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'),
                               self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'),
                               self.session('SLOWDOWN.NEVER', 'SLOWDOWN.OFTEN'),
                               self.session('SLOWDOWN.NEVER&VIDEO.YES', 'SLOWDOWN.SOMETIMES'))
        g, = out['groups']
        self.assertEqual((g['count'], g['counts']), (4, [2, 1, 1]))
        self.assertEqual(len(g['urls']), 3)  # 같은 답 조합이라도 바꾼 답이 다르면 다른 주소
        self.assertEqual(g['changed'][0], {'address': 1, 'lines': ['SLOWDOWN: NEVER → SOMETIMES']})
        self.assertIn('- 주소별 건수: 2, 1, 1', g['body'])
        m = common.metric(g['body'])
        self.assertEqual((m['counts'], [c['address'] for c in m['changed']]), ([2, 1, 1], [1, 2, 3]))
        self.assertEqual(collector.issue(tmp(g['body']))['key'], g['key'])

    def test_버리는_세션(self):
        old = int(time.time()) - 40 * 86400
        out = self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES', start=old),
                               self.session('SLOWDOWN.NEVER', 'SLOWDOWN.NEVER'),
                               self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES', purpose=''),
                               self.session('SLOWDOWN.SOMETIMES', 'SLOWDOWN.OFTEN'))
        self.assertEqual(out['groups'], [])
        self.assertEqual(out['dropped'], {'조회 기간 전에 시작한 세션': 1, '다른 답 조합의 결과를 두 번 이상 보지 않음': 1,
                                          '처음과 마지막의 사용 목적이 다르거나 없음': 1, '바뀐 항목 없음': 1})

    def test_기본_권장_사양은_답_조합으로_보지_않는다(self):
        out = self.run_signals(self.session('', 'SLOWDOWN.NEVER'))
        self.assertEqual((out['groups'], out['dropped']), ([], {'다른 답 조합의 결과를 두 번 이상 보지 않음': 1}))
        g, = self.run_signals(self.session('', 'SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))['groups']
        self.assertEqual(g['changed'], [{'address': 1, 'lines': ['SLOWDOWN: NEVER → SOMETIMES']}])

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

    def with_interpretation(self, memory_up_to):
        d = json.loads(json.dumps(DEF))
        d['interpretation'] = {'memory': [{'upTo': memory_up_to, 'value': 16}, {'value': 24}]}
        return d

    def test_배포를_기다리는_점수_해석_규칙이_있으면_그_사용_목적의_세션을_세지_않는다(self):
        self.main, self.develop = self.with_interpretation(1), self.with_interpretation(2)
        out = self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))
        self.assertEqual(out['dropped'], {'배포를 기다리는 점수 해석 규칙이 있음': 1})

    def test_점수_해석_규칙이_한쪽에만_있으면_바뀐_것으로_보지_않는다(self):
        self.develop = self.with_interpretation(1)
        self.assertEqual(len(self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))['groups']), 1)
        self.assertFalse(common.interpretation_changed(None, self.develop))
        self.assertTrue(common.interpretation_changed(self.with_interpretation(1), self.with_interpretation(2)))

    def test_배포_전에_본_점수_해석_규칙이면_세지_않는다(self):
        now = int(time.time())
        self.interpretation_deployed = [collector.iso(now - 60)]
        self.assertEqual(self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))['dropped'], {'배포 전에 본 점수 해석 규칙': 1})
        self.interpretation_deployed = [collector.iso(now - 7200)]
        self.assertEqual(len(self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))['groups']), 1)

    def test_점수_해석_규칙이_배포를_기다리면_열린_지표_피드백도_배포를_기다린다(self):
        g = self.run_signals(self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES'))['groups'][0]
        self.main, self.develop = self.with_interpretation(1), self.with_interpretation(2)
        self.assertEqual(collector.issue(tmp(g['body']))['pending'], ['interpretation'])

    def edit(self, answers, plan='MAC', cpu=('PRO', None), memory=(32, None), storage=(512, None), at=None):
        t = at or int(time.time()) - 3600
        return [str(t), 'DEV', 'LAPTOP', answers, plan, cpu[0], cpu[1], memory[0], memory[1], storage[0], storage[1]]

    def run_edits(self, *edits, sessions=()):
        return collector.signals(tmp(json.dumps(list(sessions))), 'https://api', 28, tmp(json.dumps(list(edits))))

    def test_직접_고친_기록은_항목별로_나눠_항목_방향마다_묶고_답_조합은_주소로_적는다(self):
        out = self.run_edits(self.edit('SLOWDOWN.OFTEN', memory=(32, 16), storage=(512, 1024)),
                             self.edit('SLOWDOWN.OFTEN', memory=(32, 24)),
                             self.edit('SLOWDOWN.NEVER', memory=(32, 16)))
        memory = next(g for g in out['groups'] if g['item'] == 'MEMORY')
        self.assertEqual((memory['count'], memory['counts'], memory['problem'], memory['title']),
                         (3, [2, 1], '메모리가 과해요', '[제품 추천 지표] Mac 권장안에서 메모리가 과해요 (직접 수정)'))
        self.assertIn('OFTEN', memory['urls'][0])  # 건수가 많은 주소가 앞
        self.assertIn('주소 1: 메모리: 32GB → 16GB (1건)\n주소 1: 메모리: 32GB → 24GB (1건)\n주소 2: 메모리: 32GB → 16GB (1건)', memory['body'])
        self.assertIn('- 신호: 추천 결과를 직접 고친 세션\n- 건수: 3\n- 주소별 건수: 2, 1', memory['body'])
        self.assertEqual([e['address'] for e in common.edited(memory['body'])], [1, 1, 2])
        storage = next(g for g in out['groups'] if g['item'] == 'STORAGE')
        self.assertEqual((storage['direction'], storage['count']), ('UP', 1))
        self.assertIn('주소 1: 저장 공간: 512GB → 1TB (1건)', storage['body'])
        self.assertEqual(len(out['groups']), 2)  # 메모리 과해요, 저장 공간 부족해요

    def test_직접_고친_신호와_답을_바꾼_신호는_키가_섞이지_않는다(self):
        out = self.run_edits(self.edit('SLOWDOWN.NEVER', memory=(32, 16)),
                             sessions=[self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES')])
        self.assertEqual(len({g['key'] for g in out['groups']}), 2)

    def test_직접_고친_기록_중_버리는_것(self):
        now = int(time.time())
        self.develop = json.loads(json.dumps(DEF))
        self.develop['rules'][0]['weight'] = 5  # SLOWDOWN.OFTEN 규칙
        change = {'key': 'k', 'before': None, 'after': {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['SOMETIMES']}}}
        self.deployed = [{'time': collector.iso(now - 60), 'changes': [change]}]
        out = self.run_edits(self.edit('SLOWDOWN.NEVER', memory=(32, 16), at=now - 40 * 86400),
                             self.edit('SLOWDOWN.NEVER', memory=(32, 32)),
                             self.edit('SLOWDOWN.OFTEN', memory=(16, 8)),
                             self.edit('SLOWDOWN.SOMETIMES', memory=(16, 8)))
        self.assertEqual(out['groups'], [])
        self.assertEqual(out['dropped'], {'직접 수정: 조회 기간 전에 고침': 1, '직접 수정: 사용 목적·OS·고친 항목이 없음': 1,
                                          '직접 수정: 배포를 기다리는 규칙에 걸림': 1, '직접 수정: 배포 전에 본 바뀐 규칙에 걸림': 1})

    def test_올바르지_않은_값의_기록만_버리고_나머지는_센다(self):
        out = self.run_edits(self.edit('SLOWDOWN.NEVER', memory=('32', 'abc')),
                             self.edit('SLOWDOWN.NEVER', cpu=('PRO', 'P_HS')),  # Mac 에 없는 Windows 등급
                             self.edit('SLOWDOWN.NEVER', cpu=('PRO', 'ULTRA')),
                             self.edit('SLOWDOWN.NEVER', memory=('32', '16')))
        self.assertEqual(out['dropped'], {'직접 수정: 값이 올바르지 않음(OS 에 없는 CPU 등급, 숫자가 아닌 용량)': 3})
        self.assertEqual([g['count'] for g in out['groups']], [1])

    def test_직접_고친_지표_피드백은_만든_신호와_같은_키를_낸다(self):
        g = self.run_edits(self.edit('SLOWDOWN.NEVER', memory=(32, 16)))['groups'][0]
        self.assertEqual([(e['address'], e['item'], e['recommended']['rank'], e['edited']['rank']) for e in common.edited(g['body'])], [(1, 'MEMORY', 32, 16)])
        self.assertEqual(collector.issue(tmp(g['body'])), {'key': g['key'], 'purpose': 'DEV', 'pending': []})

    def test_열린_지표_피드백의_신호_키와_배포를_기다리는_규칙(self):
        self.develop = json.loads(json.dumps(DEF))
        self.develop['rules'][0]['weight'] = 5  # SLOWDOWN.OFTEN 규칙
        body_text = ('### 결과 화면 주소\n\nhttps://devica.co.kr/result?category=LAPTOP&purpose=DEV&answer=SLOWDOWN.NEVER\n\n'
                     '### 어느 권장안인가요?\n\nMac\n\n### 어떤 점이 맞지 않았나요?\n\n메모리가 과해요\n\n### 바꾼 답\n\nSLOWDOWN: NEVER → OFTEN\n')
        out = collector.issue(tmp(body_text))
        self.assertEqual(out['key'], collector.signal_key('DEV', 'Mac', '메모리가 과해요'))
        self.assertEqual(out['pending'], [c['key'] for c in diff(DEF, self.develop)])
        # 주소마다 자기 바꾼 답을 적용한다. 주소 2 만 OFTEN 으로 바꿨어도 걸린다
        two = body_text.replace('answer=SLOWDOWN.NEVER\n', 'answer=SLOWDOWN.NEVER\nhttps://devica.co.kr/result?category=LAPTOP&purpose=DEV&answer=SLOWDOWN.SOMETIMES\n') \
                       .replace('SLOWDOWN: NEVER → OFTEN', '주소 1: SLOWDOWN: NEVER → SOMETIMES\n주소 2: SLOWDOWN: SOMETIMES → OFTEN')
        self.assertEqual(collector.issue(tmp(two))['pending'], [c['key'] for c in diff(DEF, self.develop)])
        self.assertEqual(collector.issue(tmp('### 결과 화면 주소\n\n_No response_\n'))['error'], '결과 화면 주소가 없다')

    def test_열린_지표_피드백은_조회_기간_말고_근거가_바뀌어야_갱신한다(self):
        s = self.session('SLOWDOWN.NEVER', 'SLOWDOWN.SOMETIMES')
        one = self.run_signals(s)['groups'][0]['body']
        old = one.replace('- 기간: ', '- 기간: 2000-01-01 ~ ').replace('\n', '\r\n')
        self.assertEqual(collector.stale(tmp(old), tmp(one)), {'stale': False})
        # 새 주소가 없어도 같은 주소의 건수가 늘면 갱신한다
        two = self.run_signals(s, s)['groups'][0]['body']
        self.assertEqual(collector.stale(tmp(one), tmp(two)), {'stale': True})


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


    def test_지시를_지금_정의서에_실행할_수_있는지_본다(self):
        d = {**DEF, 'survey': {'steps': [{'questions': [
            {'code': 'SLOWDOWN', 'options': [{'code': 'OFTEN'}, {'code': 'NEVER'}]},
            {'code': 'CURRENT_MEMORY', 'options': [{'code': 'GB_32'}]},
            {'code': 'VIDEO', 'options': [{'code': 'YES'}]}]}]},
            'rules': [{**r, 'reason': f"{r['criterion']} 근거"} for r in DEF['rules']]}
        path = tmp(json.dumps(d))
        run = lambda ins: improver.check(path, 'DEV', tmp(json.dumps(ins)))
        new = {'criterion': 'MEMORY', 'when': {'all': [{'question': 'SLOWDOWN', 'anyOf': ['OFTEN']},
                                                        {'question': 'CURRENT_MEMORY', 'anyOf': ['GB_32']}]}}
        add = {'rule': new, 'change': {'add': {'direction': 'UP'}, 'reason': {'to': '새 근거'}}}
        self.assertEqual(run(add), {'ok': True, 'errors': [], 'rule': None})
        self.assertIn('새 근거 문장이 같은 항목의 다른 규칙과 같다',
                      run({**add, 'change': {**add['change'], 'reason': {'to': 'MEMORY 근거'}}})['errors'])
        self.assertIn('설문에 없는 질문·선택지: CURRENT_MEMORY.GB_64',
                      run({**add, 'rule': {**new, 'when': {'question': 'CURRENT_MEMORY', 'anyOf': ['GB_64']}}})['errors'])
        existing = {**add, 'rule': {'criterion': 'MEMORY', 'when': {'question': 'SLOWDOWN', 'anyOf': ['OFTEN']}}}
        self.assertIn('추가할 규칙과 같은 규칙이 이미 있다', run(existing)['errors'])
        self.assertTrue(run({**existing, 'rule': {**existing['rule'], 'os': 'MAC'}})['ok'])
        modify = {'rule': existing['rule'], 'change': {'weight': {'from': 2, 'to': 1}}}
        self.assertEqual(run(modify)['rule']['weight'], 2)
        self.assertIn('지시 뒤에 develop 의 규칙이 바뀌었다(weight: 지시 3, 지금 2)',
                      run({**modify, 'change': {'weight': {'from': 3, 'to': 1}}})['errors'])
        self.assertIn('지시한 규칙이 정확히 하나가 아니다(0개)', run({**modify, 'rule': new})['errors'])
        multi = {**DEF, 'survey': d['survey'], 'rules': [{'criterion': 'MEMORY', 'weight': 1, 'reason': 'r',
                                                         'when': {'question': 'SLOWDOWN', 'anyOf': ['OFTEN', 'NEVER']}}]}
        reorder = {'question': 'SLOWDOWN', 'anyOf': ['NEVER', 'OFTEN']}
        self.assertTrue(improver.check(tmp(json.dumps(multi)), 'DEV', tmp(json.dumps(
            {'rule': {'criterion': 'MEMORY', 'when': reorder}, 'change': {'when': {'from': reorder, 'to': reorder}}})))['ok'])
        self.assertIn("지시한 규칙이 정확히 하나가 아니다(0개, os 만 다른 규칙이 있다: [None])",
                      run({**modify, 'rule': {**existing['rule'], 'os': 'MAC'}})['errors'])
        self.assertIn('새 근거 문장이 같은 항목의 다른 규칙과 같다',
                      run({**add, 'change': {**add['change'], 'reason': {'to': ' MEMORY  근거 '}}})['errors'])
        self.assertIn('더할 수 있는 항목이 아니다: GPU', run({**add, 'rule': {**new, 'criterion': 'GPU'}})['errors'])

    def test_규칙_추가는_새_근거_문장이_생기면_통과하고_OS_전용이면_다른_권장안은_그대로여야_한다(self):
        ins = {'rule': {'criterion': 'MEMORY', 'when': {'question': 'CURRENT_MEMORY', 'anyOf': ['GB_32']}, 'os': 'WINDOWS'},
               'change': {'add': {'direction': 'UP'}, 'reason': {'to': '새 근거'}},
               'cases': [{'issue': 300, 'answers': {}, 'expected': [{'plan': 'WINDOWS', 'item': 'MEMORY', 'direction': 'UP'}]}]}
        before = snapshot([{'name': '피드백 #300', 'answers': {}, 'response': body(win=('P_HS', 32, 512))}])
        good = snapshot([{'name': '피드백 #300', 'answers': {}, 'response': {'specs': [
            body()['specs'][0], body(win=('P_HS', 48, 512), reasons=['새 근거'])['specs'][1]]}}])
        out = improver.compare(tmp(before), tmp(good), tmp(json.dumps(ins)))
        self.assertTrue(out['ok'], out['checks'])
        both = snapshot([{'name': '피드백 #300', 'answers': {}, 'response': body(mac=('PRO', 48, 512), win=('P_HS', 48, 512), reasons=['새 근거'])}])
        self.assertIn('WINDOWS 전용 규칙인데 MAC 결과 값이 바뀌었다',
                      improver.compare(tmp(before), tmp(both), tmp(json.dumps(ins)))['checks'][0]['why'])

    def test_지금_사양이_있으면_그보다_높아야_한다(self):
        case = lambda cur: {'rule': {'criterion': 'MEMORY', 'when': {'question': 'CURRENT_MEMORY', 'anyOf': ['GB_32']}},
                            'change': {'add': {'direction': 'UP'}, 'reason': {'to': '새 근거'}},
                            'cases': [{'issue': 300, 'answers': {}, 'expected': [
                                {'plan': 'WINDOWS', 'item': 'MEMORY', 'direction': 'UP', 'current': cur}]}]}
        snap = lambda gb: tmp(snapshot([{'name': '피드백 #300', 'answers': {}, 'response': body(win=('P_HS', gb, 512), reasons=['새 근거'])}]))
        before = snap(24)
        self.assertIn('지금 사양(32)을 넘지 않았다', improver.compare(before, snap(32), tmp(json.dumps(case(32))))['checks'][0]['why'])
        self.assertTrue(improver.compare(before, snap(48), tmp(json.dumps(case(32))))['ok'])
        down = case(16)
        down['cases'][0]['expected'][0]['direction'] = 'DOWN'
        self.assertIn('지금 사양(16)을 넘지 않게 내려가지 않았다', improver.compare(snap(32), snap(24), tmp(json.dumps(down)))['checks'][0]['why'])
        self.assertTrue(improver.compare(snap(32), snap(16), tmp(json.dumps(down)))['ok'])


# ---- 설문 개선 지시 ----

RETIRED_CASES = os.path.join(HERE, '..', 'src', 'test', 'resources', 'recommendation', 'retired-cases.json')


class ConvertTest(unittest.TestCase):
    def test_서버와_같은_변환_사례표대로_읽는다(self):
        with open(RETIRED_CASES, encoding='utf-8') as f:
            table = json.load(f)
        for c in table['cases']:
            with self.subTest(c['name']):
                got = common.convert_or_error({'survey': table['survey']}, table['retired']['codes'], c['params'],
                                              common.date.fromisoformat(c['today']))
                self.assertEqual(got, {'error': c['error']} if 'error' in c else {'answers': c['answers'], 'outdated': c['outdated']})

    def test_보관_기한은_6개월_뒤이고_그_달에_같은_날이_없으면_말일이다(self):
        self.assertEqual(str(common.expiry('2026-03-01')), '2026-09-01')
        self.assertEqual(str(common.expiry('2026-08-31')), '2027-02-28')
        self.assertEqual(str(common.expiry('2026-03-31')), '2026-09-30')
        self.assertFalse(common.expired({'retiredAt': '2026-03-01'}, common.date(2026, 9, 1)))
        self.assertTrue(common.expired({'retiredAt': '2026-03-01'}, common.date(2026, 9, 2)))


def opt(*codes):
    return [{'code': c, 'content': c.lower()} for c in codes]


SHORT = {'code': 'SHORT', 'title': '부족한가요?', 'options': opt('OFTEN', 'ONCE', 'NEVER')}
TOOLS = {'code': 'TOOLS', 'title': '도구', 'input': 'MULTI', 'exclusive': 'NONE', 'options': opt('A', 'B', 'NONE')}
VIDEO = {'code': 'VIDEO', 'title': '영상', 'showWhen': {'question': 'SHORT', 'anyOf': ['OFTEN', 'ONCE']}, 'options': opt('YES', 'NO')}
SDEF = {
    'survey': {'steps': [
        {'layout': 'CURRENT_SPEC', 'questions': [{'code': 'CURRENT_OS', 'title': 'OS', 'options': opt('MACOS', 'WINDOWS')}]},
        {'questions': [SHORT]},
        {'questions': [TOOLS, VIDEO]}]},
    'rules': [
        {'criterion': 'MEMORY', 'weight': 2, 'when': {'question': 'SHORT', 'anyOf': ['OFTEN']}, 'reason': 'm2'},
        {'criterion': 'MEMORY', 'weight': 1, 'when': {'question': 'SHORT', 'anyOf': ['ONCE']}, 'reason': 'm1'},
        {'criterion': 'STORAGE', 'weight': 1, 'when': {'all': [{'question': 'SHORT', 'anyOf': ['ONCE', 'NEVER']},
                                                                 {'not': {'question': 'TOOLS', 'anyOf': ['A']}}]}, 'reason': 's1'},
        {'criterion': 'STORAGE', 'weight': 1, 'when': {'any': [{'question': 'SHORT', 'anyOf': ['ONCE']},
                                                                 {'question': 'TOOLS', 'anyOf': ['B']}]}, 'reason': 's2'}],
    'mappings': [{'criterion': 'CURRENT_OS', 'value': 'MACOS', 'when': {'question': 'CURRENT_OS', 'anyOf': ['MACOS']}}],
    'notes': [],
}
DAY = common.date(2026, 11, 5)


def question(base, **changes):
    q = json.loads(json.dumps(base))
    q.update(changes)
    return {k: v for k, v in q.items() if v is not None}


def sins(kind, q, frm, to, option=None, **kw):
    target = {'question': q, **({'option': option} if option else {})}
    return {'issues': [1], 'basis': 'USER', 'purpose': 'P', 'kind': kind, 'target': target, 'from': frm, 'to': to,
            'reason': 'r', **kw}


def delete_once(**kw):
    return sins('DELETE_OPTION', 'SHORT', SHORT, question(SHORT, options=opt('OFTEN', 'NEVER')), 'ONCE',
                retire=[{'question': 'SHORT', 'option': 'ONCE', 'drop': True, 'was': 'once'}], **kw)


def merge_once(**kw):
    return sins('MERGE_OPTION', 'SHORT', SHORT, question(SHORT, options=opt('OFTEN', 'NEVER')), 'ONCE',
                retire=[{'question': 'SHORT', 'option': 'ONCE', 'to': 'OFTEN', 'was': 'once'}], **kw)


class SurveyPlanTest(unittest.TestCase):
    def plan(self, ins, lines=(), history=()):
        return survey.plan(SDEF, list(lines), list(history) + list(lines), ins, DAY)

    def test_from_이_지금_정의서와_다르면_고치지_않는다(self):
        ins = delete_once()
        ins['from'] = question(SHORT, title='옛 제목')
        self.assertIn('from 이 지금 정의서의 SHORT 와 다르다', self.plan(ins)['errors'])

    def test_from_과_to_의_차이가_종류와_맞아야_한다(self):
        reword = sins('REWORD', 'SHORT', SHORT, question(SHORT, title='새 제목'))
        self.assertTrue(self.plan(reword)['ok'])
        code_changed = sins('REWORD', 'SHORT', SHORT, question(SHORT, options=opt('OFTEN', 'ONCE', 'NO')))
        self.assertIn('from 과 to 의 차이가 REWORD 와 맞지 않는다', self.plan(code_changed)['errors'])
        moved_with_change = sins('MOVE_QUESTION', 'VIDEO', VIDEO, question(VIDEO, title='x'), after='SHORT')
        self.assertIn('from 과 to 의 차이가 MOVE_QUESTION 와 맞지 않는다', self.plan(moved_with_change)['errors'])
        input_and_title = sins('CHANGE_INPUT', 'TOOLS', TOOLS, question(TOOLS, input='SINGLE', exclusive=None, title='x'),
                               retire=[{'question': 'TOOLS', 'dropInvalid': True}])
        self.assertIn('from 과 to 의 차이가 CHANGE_INPUT 와 맞지 않는다', self.plan(input_and_title)['errors'])

    def test_배타_선택지를_지우면_비우고_대체하면_새_코드로(self):
        to = question(TOOLS, options=opt('A', 'B'))
        line = [{'question': 'TOOLS', 'option': 'NONE', 'drop': True, 'was': 'none'}]
        self.assertIn('from 과 to 의 차이가 DELETE_OPTION 와 맞지 않는다',
                      self.plan(sins('DELETE_OPTION', 'TOOLS', TOOLS, to, 'NONE', retire=line))['errors'])
        self.assertTrue(self.plan(sins('DELETE_OPTION', 'TOOLS', TOOLS, question(to, exclusive=None), 'NONE', retire=line))['ok'])
        replaced = question(TOOLS, exclusive='NOTHING', options=opt('A', 'B', 'NOTHING'))
        line = [{'question': 'TOOLS', 'option': 'NONE', 'to': 'NOTHING', 'was': 'none'}]
        self.assertTrue(self.plan(sins('REPLACE_OPTION', 'TOOLS', TOOLS, replaced, 'NONE', retire=line))['ok'])

    def test_retire_는_종류별_표와_맞아야_한다(self):
        wrong_was = delete_once()
        wrong_was['retire'][0]['was'] = '한두 번'
        self.assertEqual(self.plan(wrong_was)['errors'], ["retire 의 ('SHORT', 'ONCE') 줄이 DELETE_OPTION 와 맞지 않는다"])
        merge_to_nowhere = merge_once()
        merge_to_nowhere['retire'][0]['to'] = 'ONCE'
        self.assertFalse(self.plan(merge_to_nowhere)['ok'])
        new_q = question(SHORT, code='LACK', options=opt('OFTEN', 'NEVER'))
        q_line = {'question': 'SHORT', 'to': 'LACK', 'was': '부족한가요?'}
        missing_option_line = sins('REPLACE_QUESTION', 'SHORT', SHORT, new_q, retire=[q_line])
        self.assertFalse(self.plan(missing_option_line)['ok'])
        full = sins('REPLACE_QUESTION', 'SHORT', SHORT, new_q,
                    retire=[q_line, {'question': 'LACK', 'option': 'ONCE', 'drop': True, 'was': 'once'}])
        self.assertTrue(self.plan(full)['ok'])
        self.assertEqual(self.plan(full)['definition']['rules'][0]['when'], {'question': 'LACK', 'anyOf': ['OFTEN']})
        full['retire'][1] = {'question': 'LACK', 'option': 'ONCE', 'to': 'OFTEN', 'was': 'once'}
        self.assertEqual(self.plan(full)['errors'], ['참조 정리를 할 수 없다: 같은 규칙이 둘 생긴다'])
        single = sins('CHANGE_INPUT', 'TOOLS', TOOLS, question(TOOLS, input='SINGLE'))
        self.assertFalse(self.plan(single)['ok'])
        self.assertTrue(self.plan(dict(single, retire=[{'question': 'TOOLS', 'dropInvalid': True}]))['ok'])

    def test_하지_않는_변경(self):
        os_q = SDEF['survey']['steps'][0]['questions'][0]
        add = sins('ADD_OPTION', 'CURRENT_OS', os_q, question(os_q, options=opt('MACOS', 'WINDOWS', 'LINUX')), 'LINUX')
        self.assertIn('매핑이 쓰는 질문 CURRENT_OS 에 새 코드를 만든다', self.plan(add)['errors'])
        delete = sins('DELETE_OPTION', 'CURRENT_OS', os_q, question(os_q, options=opt('MACOS')), 'WINDOWS',
                      retire=[{'question': 'CURRENT_OS', 'option': 'WINDOWS', 'drop': True, 'was': 'windows'}])
        self.assertIn('현재 사양 질문 CURRENT_OS 를 옮기거나 코드를 지우거나 바꾼다', self.plan(delete)['errors'])
        move = sins('MOVE_QUESTION', 'CURRENT_OS', os_q, os_q, after='SHORT')
        self.assertIn('현재 사양 질문 CURRENT_OS 를 옮기거나 코드를 지우거나 바꾼다', self.plan(move)['errors'])
        self.assertTrue(self.plan(sins('MOVE_QUESTION', 'VIDEO', VIDEO, VIDEO, after='SHORT'))['ok'])
        drop_shown = sins('DELETE_OPTION', 'SHORT', SHORT, question(SHORT, options=opt('ONCE', 'NEVER')), 'OFTEN',
                          retire=[{'question': 'SHORT', 'option': 'OFTEN', 'drop': True, 'was': 'often'}])
        self.assertTrue(self.plan(drop_shown)['ok'])
        steps = [{'questions': [question(SHORT, code=f'Q{i}')]} for i in range(9)]
        many = dict(SDEF, survey={'steps': SDEF['survey']['steps'] + steps})
        add_q = sins('ADD_QUESTION', 'NEW', None, question(SHORT, code='NEW'), after='SHORT')
        self.assertIn('참조 정리를 할 수 없다: 단계가 13개로 12개를 넘는다', survey.plan(many, [], [], add_q, DAY)['errors'])

    def test_지우거나_합친_코드는_조건에서_빼고_빈_조건은_정리한다(self):
        p = self.plan(merge_once())
        rules = {r['reason']: r['when'] for r in p['definition']['rules']}
        self.assertNotIn('m1', rules)  # 조건 전체가 비었다
        self.assertNotIn('s1', {r['reason'] for r in p['definition']['rules'] if 'ONCE' in json.dumps(r)})
        self.assertEqual(rules['s1'], {'all': [{'question': 'SHORT', 'anyOf': ['NEVER']}, {'not': {'question': 'TOOLS', 'anyOf': ['A']}}]})
        self.assertEqual(rules['s2'], {'any': [{'question': 'TOOLS', 'anyOf': ['B']}]})
        video = survey.questions_of(p['definition'])['VIDEO']
        self.assertEqual(video['showWhen'], {'question': 'SHORT', 'anyOf': ['OFTEN']})  # 합친 OFTEN 으로 바꿔 쓰지 않는다
        self.assertEqual({e['part'] for e in p['effects']}, {'rules', 'showWhen'})

    def test_새_코드로_옮길_때만_조건을_바꿔_쓴다(self):
        to = question(SHORT, options=opt('OFTEN', 'SOMETIMES', 'NEVER'))
        ins = sins('REPLACE_OPTION', 'SHORT', SHORT, to, 'ONCE', retire=[{'question': 'SHORT', 'option': 'ONCE', 'drop': True, 'was': 'once'}])
        rules = {r['reason']: r['when'] for r in self.plan(ins)['definition']['rules']}
        self.assertEqual(rules['m1'], {'question': 'SHORT', 'anyOf': ['SOMETIMES']})

    def test_참조_정리를_할_수_없는_변경(self):
        drop_a = sins('DELETE_OPTION', 'TOOLS', TOOLS, question(TOOLS, options=opt('B', 'NONE')), 'A',
                      retire=[{'question': 'TOOLS', 'option': 'A', 'drop': True, 'was': 'a'}])
        self.assertTrue(self.plan(drop_a)['ok'])  # all 안의 not 은 뺀다
        whole_not = dict(SDEF, rules=[{'criterion': 'MEMORY', 'weight': 1, 'when': {'not': {'question': 'TOOLS', 'anyOf': ['A']}}, 'reason': 'n'}])
        self.assertIn('참조 정리를 할 수 없다', survey.plan(whole_not, [], [], drop_a, DAY)['errors'][0])
        drop_shown_all = sins('DELETE_QUESTION', 'SHORT', SHORT, None, retire=[{'question': 'SHORT', 'drop': True, 'was': '부족한가요?'}])
        self.assertEqual(self.plan(drop_shown_all)['errors'], ['참조 정리를 할 수 없다: VIDEO 의 노출 조건이 빈다'])
        twins = dict(SDEF, rules=SDEF['rules'] + [{'criterion': 'MEMORY', 'weight': 3, 'when': {'question': 'SHORT', 'anyOf': ['OFTEN', 'ONCE']}, 'reason': 'x'}])
        self.assertEqual(survey.plan(twins, [], [], merge_once(), DAY)['errors'], ['참조 정리를 할 수 없다: 같은 규칙이 둘 생긴다'])

    def test_지운_코드는_기한이_지나_줄이_없어도_다시_쓰지_않는다(self):
        history = [{'question': 'SHORT', 'option': 'SOMETIMES', 'drop': True, 'was': '가끔', 'retiredAt': '2025-01-01'}]
        to = question(SHORT, options=opt('OFTEN', 'ONCE', 'NEVER', 'SOMETIMES'))
        add = sins('ADD_OPTION', 'SHORT', SHORT, to, 'SOMETIMES')
        self.assertEqual(self.plan(add, history=history)['errors'], ['지운 코드를 다시 쓴다: SHORT.SOMETIMES'])
        self.assertTrue(self.plan(add)['ok'])
        restore = dict(add, kind='RESTORE')
        self.assertFalse(self.plan(restore, history=history)['ok'])  # 문구가 was 와 다르다
        restore['to'] = question(SHORT, options=opt('OFTEN', 'ONCE', 'NEVER') + [{'code': 'SOMETIMES', 'content': '가끔'}])
        self.assertTrue(self.plan(restore, history=history)['ok'])
        self.assertEqual(self.plan(restore)['errors'], ['되살릴 코드가 지운 코드 목록 이력에 없다'])

    def test_지운_코드_목록(self):
        old = [{'question': 'SHORT', 'option': 'RARE', 'to': 'ONCE', 'was': '드물게', 'retiredAt': '2026-10-01'},
               {'question': 'SHORT', 'option': 'GONE', 'drop': True, 'was': '옛것', 'retiredAt': '2026-01-01'},
               {'question': 'TOOLS', 'dropInvalid': True, 'retiredAt': '2026-01-01'}]
        p = self.plan(merge_once(), lines=old)
        self.assertEqual(p['retired'], [
            {'question': 'SHORT', 'option': 'RARE', 'to': 'OFTEN', 'was': '드물게', 'retiredAt': '2026-10-01'},
            {'question': 'SHORT', 'option': 'ONCE', 'to': 'OFTEN', 'was': 'once', 'retiredAt': '2026-11-05'}])
        self.assertEqual(len(p['retired_changes']['expired']), 2)
        self.assertEqual(self.plan(delete_once(), lines=old)['retired'][0]['drop'], True)
        reword = sins('REWORD', 'SHORT', SHORT, question(SHORT, title='새 제목'))
        self.assertIsNone(self.plan(reword, lines=old)['retired'])  # 파일을 고치지 않으면 기한 지난 줄도 두다
        single = sins('CHANGE_INPUT', 'TOOLS', TOOLS, question(TOOLS, input='SINGLE'), retire=[{'question': 'TOOLS', 'dropInvalid': True}])
        fresh = old[:2] + [dict(old[2], retiredAt='2026-09-01')]
        self.assertEqual(self.plan(single, lines=fresh)['retired'][-1], {'question': 'TOOLS', 'dropInvalid': True, 'retiredAt': '2026-11-05'})
        self.assertEqual(len(self.plan(single, lines=fresh)['retired']), 2)
        deleted = sins('DELETE_QUESTION', 'TOOLS', TOOLS, None, retire=[{'question': 'TOOLS', 'drop': True, 'was': '도구'}])
        chained = [{'question': 'TOOLKIT', 'to': 'TOOLS', 'was': '도구들', 'retiredAt': '2026-10-01'}, dict(old[2], retiredAt='2026-10-01')]
        self.assertEqual(self.plan(deleted, lines=chained)['retired'], [
            {'question': 'TOOLKIT', 'was': '도구들', 'retiredAt': '2026-10-01', 'drop': True},
            {'question': 'TOOLS', 'drop': True, 'was': '도구', 'retiredAt': '2026-11-05'}])
        history = [{'question': 'SHORT', 'option': 'SOMETIMES', 'drop': True, 'was': '가끔', 'retiredAt': '2026-10-01'}]
        restore = sins('RESTORE', 'SHORT', SHORT, question(SHORT, options=opt('OFTEN', 'ONCE', 'NEVER') + [{'code': 'SOMETIMES', 'content': '가끔'}]), 'SOMETIMES')
        self.assertEqual(self.plan(restore, lines=history)['retired'], [])

    def test_지운_코드_목록_파일은_서버가_읽는_모양이다(self):
        text = survey.retired_yaml([{'question': 'Q', 'option': 'O', 'drop': True, 'was': '있나요?', 'retiredAt': '2026-11-05'}])
        self.assertEqual(text, 'codes:\n  - { question: Q, option: O, drop: true, was: "있나요?", retiredAt: 2026-11-05 }\n')
        self.assertEqual(common.y2j(text)['codes'][0]['retiredAt'], '2026-11-05')
        self.assertEqual(survey.retired_yaml([]), 'codes: []\n')

    def test_이력은_지운_줄도_모은다(self):
        repo = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, repo)
        g = ['git', '-C', repo, '-c', 'user.name=t', '-c', 'user.email=t@t']
        path = os.path.join(repo, common.RETIRED_PATH.format('P'))
        os.makedirs(os.path.dirname(path))
        subprocess.run(g + ['init', '-q'], check=True)
        for text in ['codes:\n  - { question: Q, drop: true, was: "옛", retiredAt: 2026-01-01 }\n', 'codes: []\n']:
            with open(path, 'w', encoding='utf-8') as f:
                f.write(text)
            subprocess.run(g + ['add', '.'], check=True)
            subprocess.run(g + ['commit', '-qm', 'x'], check=True)
        os.environ['REPO'] = repo
        self.addCleanup(os.environ.pop, 'REPO')
        self.assertEqual([l['question'] for l in survey.retired_history('HEAD', 'P')], ['Q'])


class SurveyFlowTest(unittest.TestCase):
    def test_결과가_없는_가장_오래된_지시를_종류마다_짝지어_고른다(self):
        good = lambda m: f'<!-- product-recommendation:{m} -->\n```yaml\noutcome: NOT_OPENED\nreason: x\n```'
        ins = lambda m, data: f'<!-- product-recommendation:{m} -->\n```yaml\n{json.dumps(data, ensure_ascii=False)}\n```'
        rule = ins('decision', schema('improvement-instruction')['examples'][0])
        rows = [
            {'issue': 1, 'id': 1, 'created_at': '2026-11-01T00:00:00Z', 'html_url': 'u1', 'body': ins('survey-decision', delete_once())},
            {'issue': 1, 'id': 2, 'created_at': '2026-11-01T01:00:00Z', 'html_url': 'u2', 'body': good('survey-result')},
            {'issue': 2, 'id': 3, 'created_at': '2026-11-02T00:00:00Z', 'html_url': 'u3', 'body': ins('survey-decision', {'kind': 'X'})},
            {'issue': 3, 'id': 4, 'created_at': '2026-11-03T00:00:00Z', 'html_url': 'u4', 'body': rule},
            {'issue': 3, 'id': 5, 'created_at': '2026-11-03T01:00:00Z', 'html_url': 'u5', 'body': good('survey-result')},
            {'issue': 1, 'id': 6, 'created_at': '2026-11-04T00:00:00Z', 'html_url': 'u6', 'body': ins('survey-decision', delete_once())},
        ]
        path, out = tmp('\n'.join(json.dumps(r, ensure_ascii=False) for r in rows)), tmp('')
        got = survey.next_instruction(path, out)
        # 이슈 3 의 개선 지시에는 설문 개선 결과가 짝이 아니다
        self.assertEqual(got['target'], {'kind': 'decision', 'issue': 3, 'url': 'u4'})
        self.assertEqual([i['url'] for i in got['invalid']], ['u3'])
        with open(out, encoding='utf-8') as f:
            self.assertEqual(json.load(f), schema('improvement-instruction')['examples'][0])
        rows[3]['body'] = rows[3]['body'].replace('decision', 'survey-decision')
        self.assertEqual(survey.next_instruction(tmp('\n'.join(json.dumps(r, ensure_ascii=False) for r in rows)), out)['target']['url'], 'u6')

    def test_형식이_틀린_지시와_결과는_짝짓지_않는다(self):
        result = lambda body: f'<!-- product-recommendation:survey-result -->\n{body}'
        ins = lambda data: f'<!-- product-recommendation:survey-decision -->\n```yaml\n{json.dumps(data, ensure_ascii=False)}\n```'
        row = lambda i, body: {'issue': 1, 'id': i, 'created_at': f'2026-11-0{i}T00:00:00Z', 'html_url': f'u{i}', 'body': body}
        good = result('```yaml\noutcome: NOT_OPENED\nreason: x\n```')
        out = tmp('')
        cases = [
            ([row(1, ins(delete_once())), row(2, result('YAML 없음'))], 'u1', ['u2']),
            ([row(1, ins(delete_once())), row(2, ins({'kind': 'X'})), row(3, good)], None, ['u2']),
        ]
        for rows, target, invalid in cases:
            got = survey.next_instruction(tmp('\n'.join(json.dumps(r, ensure_ascii=False) for r in rows)), out)
            self.assertEqual(got['target'] and got['target']['url'], target)
            self.assertEqual([i['url'] for i in got['invalid']], invalid)

    def test_이름이_여러_번_바뀐_질문을_지우면_이어진_옛_질문_줄도_drop_으로_바꾼다(self):
        lines = [{'question': 'A', 'to': 'B', 'was': '가', 'retiredAt': '2026-10-01'},
                 {'question': 'B', 'to': 'C', 'was': '나', 'retiredAt': '2026-10-15'},
                 {'question': 'X', 'to': 'Y', 'was': '다', 'retiredAt': '2026-10-15'}]
        ins = {'kind': 'DELETE_QUESTION', 'target': {'question': 'C'}, 'to': None,
               'retire': [{'question': 'C', 'drop': True, 'was': '라'}]}
        rows, _ = survey.planned_retired(lines, ins, datetime.date(2026, 11, 1))
        by = {l['question']: l for l in rows}
        self.assertTrue(by['A'].get('drop') and 'to' not in by['A'])
        self.assertTrue(by['B'].get('drop') and 'to' not in by['B'])
        self.assertEqual(by['X']['to'], 'Y')

    def setUp(self):
        self.wt = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.wt)
        os.makedirs(os.path.dirname(os.path.join(self.wt, common.DEF_PATH.format('P'))))
        os.makedirs(os.path.join(self.wt, 'src/test/resources/recommendation'))
        self.snap = survey.snapshot_path(self.wt, 'P')
        with open(self.snap, 'w', encoding='utf-8') as f:
            f.write(snapshot([{'name': '대표', 'answers': {'SHORT': ['ONCE'], 'VIDEO': ['YES']}, 'response': body()},
                              {'name': '이름', 'answers': {'SHORT': ['OFTEN']}, 'response': body()},
                              {'answers': {'SHORT': ['ONCE']}, 'response': body()}]))

    def plan_file(self, ins):
        p = survey.plan(SDEF, [], [], ins, DAY)
        path = tmp(json.dumps(p, ensure_ascii=False))
        return p, path

    def test_이름_있는_사례의_답만_지금_코드로_고쳐_쓰고_버린_답을_표시한다(self):
        _, path = self.plan_file(merge_once())
        out = survey.survey_apply(path, self.wt)
        self.assertTrue(out['retired_written'])
        self.assertEqual(out['named'], [{'name': '대표', 'before': {'SHORT': ['ONCE'], 'VIDEO': ['YES']},
                                         'after': {'SHORT': ['OFTEN'], 'VIDEO': ['YES']}, 'dropped': False}])
        self.assertEqual([c['answers'] for c in improver.snapshot_lines(self.snap)],
                         [{'SHORT': ['OFTEN'], 'VIDEO': ['YES']}, {'SHORT': ['OFTEN']}, {'SHORT': ['ONCE']}])
        _, path = self.plan_file(delete_once())
        self.setUp()
        self.assertEqual(survey.survey_apply(path, self.wt)['named'][0],
                         {'name': '대표', 'before': {'SHORT': ['ONCE'], 'VIDEO': ['YES']}, 'after': {}, 'dropped': True})

    def test_고친_정의서가_계획과_같은지(self):
        rule = {'criterion': 'MEMORY', 'when': {'question': 'SHORT', 'anyOf': ['SOMETIMES']}, 'direction': 'UP', 'reason': '새'}
        to = question(SHORT, options=opt('OFTEN', 'ONCE', 'NEVER', 'SOMETIMES'))
        p, path = self.plan_file(sins('ADD_OPTION', 'SHORT', SHORT, to, 'SOMETIMES', rule=rule, cases=[]))
        edited = json.loads(json.dumps(p['definition']))
        edited['rules'] = [r for r in edited['rules'] if r['weight'] is not None]
        def_path = tmp(json.dumps(edited, ensure_ascii=False))
        self.assertEqual(survey.survey_verify(path, def_path), {'ok': True, 'errors': [], 'new_rule_weight': None})
        edited['rules'].append({**{k: rule[k] for k in ('criterion', 'when', 'reason')}, 'weight': -1})
        self.assertFalse(survey.survey_verify(path, tmp(json.dumps(edited, ensure_ascii=False)))['ok'])
        edited['rules'][-1]['weight'] = 2
        self.assertEqual(survey.survey_verify(path, tmp(json.dumps(edited, ensure_ascii=False)))['new_rule_weight'], 2)
        edited['rules'][0]['weight'] = 5
        self.assertTrue(survey.survey_verify(path, tmp(json.dumps(edited, ensure_ascii=False)))['errors'][0].startswith('계획에 없는 규칙: '))
        edited['survey']['steps'][1]['questions'][0]['title'] = 'x'
        self.assertIn('survey 가 계획과 다르다', survey.survey_verify(path, tmp(json.dumps(edited, ensure_ascii=False)))['errors'])

    def test_설문_변경_전후_결과는_옛_답_사례와_reasons_만_바뀐다(self):
        ins = merge_once(reasons=[{'criterion': 'MEMORY', 'when': {'question': 'SHORT', 'anyOf': ['OFTEN']}, 'from': 'm2', 'to': 'm2!'}])
        _, plan_path = self.plan_file(ins)
        before = [{'name': '피드백 #9', 'answers': {'SHORT': ['ONCE']}, 'response': body(mac=('PRO', 32, 512))},
                  {'answers': {'SHORT': ['ONCE']}, 'response': body(mac=('PRO', 32, 512))},
                  {'answers': {'SHORT': ['OFTEN']}, 'response': body(reasons=['m2'])}]
        after = [{'name': '피드백 #9', 'answers': {'SHORT': ['OFTEN']}, 'response': body(mac=('PRO', 16, 512))},
                 {'answers': {'SHORT': ['ONCE']}, 'response': body(mac=('PRO', 16, 512))},
                 {'answers': {'SHORT': ['OFTEN']}, 'response': body(reasons=['m2!'])}]
        cmp = lambda a, d=None: survey.survey_compare(tmp(snapshot(before)), tmp(snapshot(a)), tmp(json.dumps(ins)), plan_path, d)
        out = cmp(after)
        self.assertEqual((out['ok'], out['reason_only'], out['old_answers'], out['unchecked']), (True, 1, 2, ['피드백 #9']))
        self.assertEqual([r['case'] for r in out['named_rows']], ['피드백 #9'])
        decision = {'issue': 9, 'id': 1, 'created_at': 't', 'html_url': 'u', 'body': '<!-- product-recommendation:decision -->\n```yaml\n' + json.dumps(
            schema('improvement-instruction')['examples'][0] | {'issues': [9], 'cases': [
                {'issue': 9, 'answers': {}, 'expected': [{'plan': 'MAC', 'item': 'MEMORY', 'direction': 'UP'}]}]},
            ensure_ascii=False) + '\n```'}
        out = cmp(after, tmp(json.dumps(decision, ensure_ascii=False)))
        self.assertEqual(out['fails'], ['피드백 #9: MAC MEMORY 이 개선 지시의 UP 과 반대로 움직였다'])
        # 근거가 #4·#9 인 지시는 #4 에 달린다. 근거에 9 가 없는 지시의 같은 이름 사례는 쓰지 않는다
        on_smaller = json.loads(json.dumps(decision))
        on_smaller['body'] = on_smaller['body'].replace('"issues": [9]', '"issues": [4, 9]')
        on_smaller['issue'] = 4
        self.assertEqual(cmp(after, tmp(json.dumps(on_smaller, ensure_ascii=False)))['fails'], out['fails'])
        unrelated = json.loads(json.dumps(on_smaller))
        unrelated['body'] = unrelated['body'].replace('"issues": [4, 9]', '"issues": [4]')
        self.assertEqual(cmp(after, tmp(json.dumps(unrelated, ensure_ascii=False)))['unchecked'], ['피드백 #9'])
        later_down = json.loads(json.dumps(on_smaller))
        later_down.update(id=2, created_at='u', body=on_smaller['body'].replace('"direction": "UP"', '"direction": "DOWN"'))
        rows = '\n'.join(json.dumps(x, ensure_ascii=False) for x in (later_down, on_smaller))
        self.assertTrue(cmp(after, tmp(rows))['ok'])  # 가장 최근 지시(DOWN)를 쓴다
        changed = after[:2] + [{'answers': {'SHORT': ['OFTEN']}, 'response': body(mac=('MAX', 16, 512), reasons=['m2!'])}]
        self.assertEqual(cmp(changed)['fails'], ['{"SHORT": ["OFTEN"]}: 결과 값이 바뀌었다'])
        other_reason = after[:2] + [{'answers': {'SHORT': ['OFTEN']}, 'response': body(reasons=['m3'])}]
        self.assertEqual(cmp(other_reason)['fails'], ['{"SHORT": ["OFTEN"]}: reasons 밖의 근거 문장이 바뀌었다'])

    def test_함께_더할_규칙은_개선_지시_모양으로(self):
        rule = {'criterion': 'MEMORY', 'when': {'question': 'SHORT', 'anyOf': ['SOMETIMES']}, 'direction': 'UP', 'reason': '새'}
        cases = [{'issue': 1, 'answers': {'SHORT': ['SOMETIMES']}, 'expected': [{'plan': 'MAC', 'item': 'MEMORY', 'direction': 'UP'}]}]
        out = survey.survey_rule(tmp(json.dumps(sins('ADD_OPTION', 'SHORT', SHORT, SHORT, 'SOMETIMES', rule=rule, cases=cases))))
        self.assertEqual((out['rule'], out['change']), ({'criterion': 'MEMORY', 'when': rule['when']},
                                                        {'add': {'direction': 'UP'}, 'reason': {'to': '새'}}))

    def test_지우거나_바꾼_코드가_든_열린_피드백(self):
        _, path = self.plan_file(delete_once())
        rows = [{'number': 1, 'body': '### 결과 화면 주소\n\nhttps://devica.co.kr/result?purpose=P&answer=SHORT.ONCE\n'},
                {'number': 2, 'body': '### 결과 화면 주소\n\nhttps://devica.co.kr/result?purpose=Q&answer=SHORT.ONCE\n'},
                {'number': 3, 'body': '### 사용 목적\n\nP\n\n### 지금까지 고른 답\n\nCURRENT_OS.MACOS,SHORT.ONCE\n'},
                {'number': 4, 'body': '### 사용 목적\n\nP\n\n### 질문\n\nTOOLS\n'}]
        self.assertEqual(survey.survey_affected(path, tmp('\n'.join(json.dumps(r) for r in rows))), [1, 3])


def git_repo(test):
    # 임시 레포. REPO 를 바꿔 common.git 이 이 레포를 읽게 한다
    repo = tempfile.mkdtemp()
    test.addCleanup(shutil.rmtree, repo)
    prev = os.environ.get('REPO')
    os.environ['REPO'] = repo
    test.addCleanup(lambda: os.environ.__setitem__('REPO', prev) if prev else os.environ.pop('REPO', None))
    subprocess.run(['git', '-C', repo, 'init', '-q'], check=True)
    return repo


def commit(repo, files, when, ref=None):
    for path, text in files.items():
        full = os.path.join(repo, path)
        os.makedirs(os.path.dirname(full), exist_ok=True)
        with open(full, 'w', encoding='utf-8') as f:
            f.write(text)
    env = {**os.environ, 'GIT_COMMITTER_DATE': when, 'GIT_AUTHOR_DATE': when}
    g = ['git', '-C', repo, '-c', 'user.name=t', '-c', 'user.email=t@t']
    subprocess.run(g + ['add', '-A'], check=True, env=env)
    subprocess.run(g + ['commit', '-qm', when], check=True, env=env)
    sha = subprocess.run(g + ['rev-parse', 'HEAD'], capture_output=True, text=True).stdout.strip()
    if ref:
        subprocess.run(g + ['update-ref', ref, sha], check=True)
    return sha


analyzer = load('feedback-analyzer', 'survey_feedback.py')
OLD_LINES = [{'question': 'OLD', 'to': 'SHORT', 'was': '옛 질문', 'retiredAt': '2026-10-01'},
             {'question': 'SHORT', 'option': 'RARE', 'to': 'ONCE', 'was': '드물게', 'retiredAt': '2026-10-01'},
             {'question': 'SHORT', 'option': 'GONE', 'drop': True, 'was': '옛것', 'retiredAt': '2026-10-01'}]


class AnalyzerRuleSideTest(unittest.TestCase):
    def test_바꿔_읽은_답에_버려진_답이_있는지(self):
        rr = lambda a: common.reread(SDEF, OLD_LINES, a, DAY)
        self.assertEqual(rr({'OLD': ['RARE']}), {'answers': {'SHORT': ['ONCE']}, 'outdated': True, 'dropped': False})
        self.assertTrue(rr({'SHORT': ['GONE']})['dropped'])
        self.assertTrue(rr({'SHORT': ['GONE'], 'VIDEO': ['YES']})['dropped'])
        # 원래부터 노출 조건에 맞지 않던 답은 지운 코드 때문에 버려진 것이 아니다
        self.assertEqual(rr({'SHORT': ['NEVER'], 'VIDEO': ['YES']}), {'answers': {'SHORT': ['NEVER']}, 'outdated': True, 'dropped': False})
        self.assertEqual(rr({'SHORT': ['NEVER']}), {'answers': {'SHORT': ['NEVER']}, 'outdated': False, 'dropped': False})
        single = [{'question': 'TOOLS', 'dropInvalid': True, 'retiredAt': '2026-10-01'}]
        d = json.loads(json.dumps(SDEF))
        d['survey']['steps'][2]['questions'][0]['input'] = 'SINGLE'
        self.assertTrue(common.reread(d, single, {'TOOLS': ['A', 'B']}, DAY)['dropped'])

    def test_옛_코드의_규칙은_to_줄로_지금_코드로_바꿔_본다(self):
        old = {'criterion': 'MEMORY', 'when': {'all': [{'question': 'OLD', 'anyOf': ['RARE', 'ONCE']},
                                                        {'not': {'question': 'TOOLS', 'anyOf': ['A']}}]}}
        self.assertEqual(common.current_rule(old, OLD_LINES)['when'],
                         {'all': [{'question': 'SHORT', 'anyOf': ['ONCE']}, {'not': {'question': 'TOOLS', 'anyOf': ['A']}}]})
        self.assertEqual(common.key(common.current_rule({'criterion': 'MEMORY', 'when': {'question': 'OLD', 'anyOf': ['RARE']}}, OLD_LINES)),
                         common.key(SDEF['rules'][1]))
        d = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, d)
        ref = os.path.join(d, 'P.yml')
        for path, text in ((ref, json.dumps(SDEF)), (os.path.join(d, 'P.retired.yml'), json.dumps({'codes': OLD_LINES}))):
            with open(path, 'w', encoding='utf-8') as f:
                f.write(text)
        old_rule = json.dumps({'criterion': 'MEMORY', 'when': {'question': 'OLD', 'anyOf': ['RARE']}})
        self.assertEqual(common.COMMANDS['key']([old_rule, ref, 'P']), common.key(SDEF['rules'][1]))
        self.assertEqual(common.COMMANDS['same']([ref, 'P', old_rule])['same'], [SDEF['rules'][1]])
        chained = [{'question': 'OLDER', 'to': 'OLD', 'was': 'x', 'retiredAt': '2026-09-01'}] + OLD_LINES
        self.assertEqual(common.renamed(chained, 'OLDER'), ('SHORT', None))
        self.assertEqual(common.renamed(chained, 'OLDER', 'RARE'), ('SHORT', 'ONCE'))

    def test_옛_규칙은_참조_정리처럼_대체한_코드만_바꾸고_합치거나_지운_코드는_뺀다(self):
        lines = OLD_LINES + [{'question': 'TOOLS', 'option': 'C', 'to': 'B', 'was': 'c', 'retiredAt': '2026-10-01'},
                             {'question': 'GONEQ', 'drop': True, 'was': 'q', 'retiredAt': '2026-10-01'}]
        cur = lambda when: common.current_rule({'criterion': 'MEMORY', 'when': when}, lines, {('TOOLS', 'C')})
        self.assertEqual(cur({'question': 'TOOLS', 'anyOf': ['C', 'A']})['when'], {'question': 'TOOLS', 'anyOf': ['A']})
        self.assertIsNone(cur({'question': 'TOOLS', 'anyOf': ['C']}))  # 합친 코드만 있던 조건은 비어 규칙이 없다
        self.assertIsNone(cur({'all': [{'question': 'OLD', 'anyOf': ['RARE']}, {'question': 'GONEQ', 'anyOf': ['Y']}]}))
        self.assertEqual(cur({'any': [{'question': 'OLD', 'anyOf': ['RARE', 'GONE']}, {'question': 'TOOLS', 'anyOf': ['C']}]})['when'],
                         {'any': [{'question': 'SHORT', 'anyOf': ['ONCE']}]})
        self.assertIsNone(cur({'not': {'question': 'SHORT', 'anyOf': ['GONE']}}))  # 늘 참이 되는 조건도 규칙이 없는 것으로 본다
        self.assertEqual(common.uses_question(lines, ['TOOLS'], [{'criterion': 'M', 'when': {'question': 'TOOLS', 'anyOf': ['C']}}],
                                              {('TOOLS', 'C')}), [])

    def test_합치기와_대체는_줄을_더한_커밋_전_정의서로_가른다(self):
        repo = git_repo(self)
        path, rpath = common.DEF_PATH.format('P'), common.RETIRED_PATH.format('P')
        base = json.loads(json.dumps(SDEF))
        short = base['survey']['steps'][1]['questions'][0]
        line = lambda o, to: {'question': 'SHORT', 'option': o, 'to': to, 'was': o, 'retiredAt': '2026-10-01'}
        short['options'] = opt('OFTEN', 'ONCE', 'NEVER', 'RARE', 'X')
        commit(repo, {path: json.dumps(base), rpath: '{"codes": []}'}, '2026-10-01T00:00:00Z')
        # RARE 는 있던 ONCE 로 합치고, X 는 새 코드 NX 로 대체한다
        short['options'] = opt('OFTEN', 'ONCE', 'NEVER', 'NX')
        commit(repo, {path: json.dumps(base), rpath: json.dumps({'codes': [line('RARE', 'ONCE'), line('X', 'NX')]})}, '2026-10-02T00:00:00Z')
        self.assertEqual(common.merged_options('HEAD', 'P'), {('SHORT', 'RARE')})
        base['rules'].append({'criterion': 'CPU', 'weight': 1, 'when': {'question': 'SHORT', 'anyOf': ['NX']}, 'reason': 'c'})
        commit(repo, {path: json.dumps(base)}, '2026-10-03T00:00:00Z')
        rule = lambda c, *o: json.dumps({'criterion': c, 'when': {'question': 'SHORT', 'anyOf': list(o)}})
        self.assertIsNone(common.COMMANDS['key']([rule('MEMORY', 'RARE'), 'HEAD', 'P']))
        self.assertEqual(common.COMMANDS['key']([rule('MEMORY', 'RARE', 'ONCE'), 'HEAD', 'P']), common.key(SDEF['rules'][1]))
        self.assertEqual(common.COMMANDS['same'](['HEAD', 'P', rule('CPU', 'X')])['same'], [base['rules'][-1]])
        self.assertEqual(common.COMMANDS['same'](['HEAD', 'P', rule('MEMORY', 'RARE')]), {'same': [], 'same_questions': []})
        # 대체한 NX 를 다시 OFTEN 으로 합치면 X→NX 줄을 고쳐 쓴 X→OFTEN 도 합치기를 이어받는다
        short['options'] = opt('OFTEN', 'ONCE', 'NEVER')
        commit(repo, {path: json.dumps(base), rpath: json.dumps({'codes': [line('RARE', 'ONCE'), line('X', 'OFTEN'), line('NX', 'OFTEN')]})},
               '2026-10-04T00:00:00Z')
        self.assertEqual(common.merged_options('HEAD', 'P'), {('SHORT', 'RARE'), ('SHORT', 'X'), ('SHORT', 'NX')})

    def test_그_질문을_조건에_쓰는_규칙(self):
        got = common.uses_question(OLD_LINES, ['OLD', 'VIDEO'], SDEF['rules'])
        self.assertEqual([(u['question'], u['rule']['reason']) for u in got], [('OLD', 'm2'), ('OLD', 'm1'), ('OLD', 's1'), ('OLD', 's2')])
        self.assertEqual(common.uses_question([], ['TOOLS'], SDEF['rules'][:2]), [])

    def test_그_시각의_커밋과_지난_날_수(self):
        repo = git_repo(self)
        first = commit(repo, {'a': '1'}, '2026-10-01T00:00:00Z')
        commit(repo, {'a': '2'}, '2026-10-10T00:00:00Z')
        self.assertEqual(common.at('HEAD', '2026-10-05T00:00:00+09:00'), first)
        self.assertIsNone(common.at('HEAD', '2026-09-01T00:00:00Z'))
        self.assertAlmostEqual(common.days((common.datetime.now(common.timezone.utc) - common.timedelta(days=28)).isoformat()), 28, places=2)


def issue_body(**f):
    names = {'purpose': '사용 목적', 'questions': '질문', 'title': '질문 제목', 'version': '설문 버전',
             'problems': '어떤 점이 맞지 않았나요?', 'details': '더 알려 주실 내용', 'basis': '근거 지표', 'url': '결과 화면 주소'}
    return '\n\n'.join(f'### {names[k]}\n\n{v}' for k, v in f.items()) + '\n'


def art(marker, data):
    return f'<!-- product-recommendation:{marker} -->\n사람이 읽는 문장\n\n```yaml\n{json.dumps(data, ensure_ascii=False)}\n```'


class AnalyzerSurveyStateTest(unittest.TestCase):
    def setUp(self):
        git_repo(self)
        user = lambda n, author='a', assoc='NONE', **f: {
            'number': n, 'title': '[제품 추천 설문 의견] x', 'state': 'open', 'created_at': f'2026-11-0{n % 10}T00:00:00Z',
            'author': author, 'author_association': assoc, 'labels': ['product-recommendation-survey-feedback'],
            'body': issue_body(purpose='P', questions='SHORT', problems='선택지끼리 구분이 안 돼요, 필요 없는 질문 같아요', **f)}
        handed = {**user(12), 'title': '[제품 추천 의견] y', 'body': issue_body(url='https://devica.co.kr/result?purpose=P&answer=SHORT.ONCE',
                                                                         problems='질문이 이해하기 어려웠어요, 메모리가 과해요', details='모르겠다')}
        rule_left = [{'rule': {'criterion': 'MEMORY', 'when': {'question': 'SHORT', 'anyOf': ['ONCE']}}, 'direction': 'DOWN'}]
        self.issues = [user(10), user(11, 'team', 'MEMBER'), handed, {**handed, 'number': 13},
                       {**user(14, 'bot', 'MEMBER', basis='- 기간: x\n- 신호: 쓰이지 않는 질문'), 'title': '[제품 추천 설문 지표] z',
                        'labels': ['product-recommendation-approved']},
                       user(15), {**user(16), 'state': 'closed'}]
        c = lambda n, i, when, body, assoc='MEMBER': {'issue': n, 'id': i, 'created_at': when, 'author_association': assoc, 'body': body}
        self.comments = [
            c(12, 1, '2026-11-02T01:00:00Z', art('handover', {'purpose': 'P', 'question': 'OLD', 'problems': ['무엇을 묻는지 모르겠어요'],
                                                              'rule_left': rule_left})),
            c(12, 2, '2026-11-02T02:00:00Z', art('handover', {'purpose': 'P', 'question': 'X', 'problems': ['기타'], 'rule_left': []})),
            c(10, 3, '2026-11-02T00:00:00Z', art('survey-analysis', {'judgment': 'ON_HOLD', 'question': 'SHORT'})),
            c(10, 40, '2026-11-02T00:00:00Z', art('survey-analysis', {'judgment': 'TEAM_HANDLES'}), 'NONE'),
            c(15, 4, '2026-11-03T00:00:00Z', art('survey-analysis', {'judgment': 'INSTRUCTED', 'question': 'SHORT'})),
            c(15, 5, '2026-11-03T00:00:00Z', art('survey-decision', delete_once(issues=[15]))),
            c(15, 6, '2026-11-03T01:00:00Z', art('survey-result', {'outcome': 'PR_OPENED', 'pr': 99})),
            c(15, 7, '2026-11-03T02:00:00Z', art('decision', schema('improvement-instruction')['examples'][0])),
            c(16, 8, '2026-11-04T00:00:00Z', art('survey-decision', merge_once(issues=[16]))),
            c(16, 9, '2026-11-04T01:00:00Z', art('survey-result', {'outcome': 'NOT_OPENED', 'reason': '점검 실패'})),
            c(11, 10, '2026-11-04T00:00:00Z', art('survey-analysis', {'judgment': 'NOPE'})),
        ]

    def state(self, prs=()):
        rows = lambda xs: tmp('\n'.join(json.dumps(x, ensure_ascii=False) for x in xs))
        return analyzer.survey_state('HEAD', rows(self.issues), rows(self.comments), rows(prs) if prs else None)

    def test_이슈마다_종류와_건수와_대상(self):
        s = self.state()
        by = {i['number']: i for i in s['issues']}
        self.assertEqual({n: (i['kind'], i['skip']) for n, i in by.items()}, {
            10: ('USER', None), 11: ('USER', None), 12: ('HANDED', None), 13: ('HANDED', '넘김 없음'),
            14: ('METRIC', None), 15: ('USER', None), 16: ('USER', None)})
        self.assertEqual(by[10]['problems'], ['선택지끼리 구분이 안 돼요', '필요 없는 질문 같아요'])
        self.assertEqual(by[10]['analysis']['data'], {'judgment': 'ON_HOLD', 'question': 'SHORT'})  # 팀원이 쓴 것만
        self.assertEqual((by[12]['questions'], by[12]['problems'], by[12]['result_problems'], by[12]['handover_unfinished']),
                         (['OLD'], ['무엇을 묻는지 모르겠어요'], ['질문이 이해하기 어려웠어요', '메모리가 과해요'], True))  # 넘김은 처음 것
        self.assertEqual((by[14]['signal'], by[14]['approved']), ('쓰이지 않는 질문', True))
        self.assertEqual(by[11]['invalid'][0]['marker'], 'survey-analysis')
        self.assertEqual({n: i['target'] for n, i in by.items()},
                         {10: 'analyze', 11: 'analyze', 12: 'analyze', 13: None, 14: 'analyze', 15: None, 16: None})

    def test_지시의_상태와_다시_판단할_지시(self):
        self.assertEqual(self.state()['prs'], [99])
        s = self.state([{'number': 99, 'state': 'CLOSED', 'mergedAt': None, 'closedAt': '2026-11-04T00:00:00Z'}])
        got = [(x['issue'], x['type'], x['status'], x.get('family'), x['rejudge']) for x in s['instructions']]
        self.assertEqual(got, [(15, 'survey', 'rejected', '삭제·합치기·대체·쪼개기·선택 방식', True), (15, 'rule', 'processing', None, False),
                               (16, 'survey', 'failed', '삭제·합치기·대체·쪼개기·선택 방식', True)])
        self.assertEqual({i['number']: i['target'] for i in s['issues']}[15], 'rejudge')
        self.assertEqual({i['number']: i['target'] for i in s['issues']}[16], None)  # 닫힌 근거는 다시 판단하지 않는다
        self.comments.append({'issue': 15, 'id': 11, 'created_at': '2026-11-05T00:00:00Z', 'author_association': 'MEMBER',
                              'body': art('survey-analysis', {'judgment': 'TEAM_HANDLES'})})
        s = self.state([{'number': 99, 'state': 'MERGED', 'mergedAt': 't', 'closedAt': 't'}])
        self.assertEqual(s['instructions'][0]['status'], 'merged')
        s = self.state([{'number': 99, 'state': 'CLOSED', 'mergedAt': None, 'closedAt': '2026-11-04T00:00:00Z'}])
        self.assertFalse(s['instructions'][0]['rejudge'])  # 반려 뒤에 판단이 있다


class AnalyzerSeenSurveyTest(unittest.TestCase):
    def setUp(self):
        self.repo = git_repo(self)
        path = common.DEF_PATH.format('P')
        old = json.loads(json.dumps(SDEF))
        old['survey']['steps'][1]['questions'][0] = question(SHORT, code='OLD')
        self.old_text = json.dumps(old, ensure_ascii=False)
        self.v1 = commit(self.repo, {path: self.old_text}, '2026-10-01T00:00:00Z', 'refs/remotes/origin/main')
        new = json.loads(json.dumps(SDEF))
        new['survey']['steps'][2]['questions'][1] = question(VIDEO, title='영상 편집')
        commit(self.repo, {path: json.dumps(new, ensure_ascii=False),
                           common.RETIRED_PATH.format('P'): 'codes:\n  - { question: OLD, to: SHORT, was: "x", retiredAt: 2026-10-02 }\n'},
               '2026-10-02T00:00:00Z', 'refs/remotes/origin/develop')

    def seen(self, codes, version='-', created='2026-10-01T12:00:00Z', title=None):
        return analyzer.seen_survey('origin/main', 'origin/develop', 'P', codes, version, created, *([title] if title else []))

    def test_설문_버전으로_찾고_지금_코드로_따라가_바뀌었는지_본다(self):
        version = analyzer.hashlib.sha256(self.old_text.encode()).hexdigest()[:12]
        out = self.seen('OLD,VIDEO', version)
        self.assertEqual(out['commit'], self.v1)
        self.assertEqual([(q['code'], q['current'], q['changed']) for q in out['questions']], [('OLD', 'SHORT', False), ('VIDEO', 'VIDEO', True)])
        self.assertIsNone(out['judgment'])
        self.assertEqual(self.seen('VIDEO', version)['judgment'], 'ALREADY_CHANGED')
        self.assertEqual(self.seen('OLD', 'ffffffffffff')['judgment'], 'NOT_REPRODUCIBLE')
        self.assertEqual(self.seen('SHORT', version)['judgment'], 'NOT_REPRODUCIBLE')

    def test_버전이_없으면_그_시각의_main_과_질문_제목으로_찾는다(self):
        self.assertEqual(self.seen('OLD', title='부족한가요?')['commit'], self.v1)
        self.assertEqual(self.seen('OLD', title='다른 제목')['judgment'], 'NOT_REPRODUCIBLE')
        self.assertEqual(self.seen('OLD', created='2026-09-01T00:00:00Z')['judgment'], 'NOT_REPRODUCIBLE')
        state = tmp(json.dumps({'issues': [{'number': 3, 'purpose': 'P', 'questions': ['OLD'], 'version': None,
                                            'created_at': '2026-10-01T12:00:00Z', 'title': '다른 제목'}]}, ensure_ascii=False))
        self.assertEqual(analyzer.COMMANDS['seen-survey'](['origin/main', 'origin/develop', state, '3'])['judgment'], 'NOT_REPRODUCIBLE')


class AnalyzerSurveyReadyTest(unittest.TestCase):
    def setUp(self):
        d = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, d)
        self.ref = os.path.join(d, 'P.yml')
        with open(self.ref, 'w', encoding='utf-8') as f:
            json.dump(SDEF, f, ensure_ascii=False)
        with open(os.path.join(d, 'P.retired.yml'), 'w', encoding='utf-8') as f:
            f.write('codes: []\n')
        issue = lambda n, author, kind='USER', **kw: {'number': n, 'state': 'open', 'skip': None, 'purpose': 'P', 'questions': ['SHORT'],
                                                       'kind': kind, 'author': author, 'team': False, 'approved': False,
                                                       'signal': None, 'handover': None, **kw}
        self.issues = {1: issue(1, 'a'), 2: issue(2, 'b'), 3: issue(3, 'a'), 4: issue(4, 'bot', 'METRIC', questions=['VIDEO'], signal='이탈이 많은 화면'),
                       5: issue(5, 'c', questions=['VIDEO']), 6: issue(6, 'bot', 'METRIC', questions=['VIDEO'], signal=analyzer.UNUSED),
                       7: issue(7, 'team', team=True), 8: issue(8, 'd', 'HANDED', handover={'rule_left': [{'rule': {}, 'direction': 'UP'}]})}
        self.instructions, self.rules = [], []

    def ready(self, ins):
        state = tmp(json.dumps({'issues': list(self.issues.values()), 'instructions': self.instructions}, ensure_ascii=False))
        return analyzer.survey_ready(self.ref, tmp(art('survey-decision', ins)), state, tmp(json.dumps(self.rules)), DAY)

    def test_종류별_근거_건수(self):
        reword = sins('REWORD', 'SHORT', SHORT, question(SHORT, title='새 제목'))
        self.assertEqual(self.ready(reword)['basis'], 'USER')
        self.assertEqual(self.ready(delete_once())['stage'], 'count')  # ONCE 는 규칙이 쓴다
        self.assertTrue(self.ready(delete_once(issues=[1, 2]))['ok'])
        self.assertEqual(self.ready(delete_once(issues=[1, 3]))['stage'], 'count')  # 같은 작성자
        self.assertTrue(self.ready(delete_once(issues=[7]))['ok'])  # 팀원 의견은 1건으로 충분하다
        unused = lambda **kw: sins('DELETE_OPTION', 'VIDEO', VIDEO, question(VIDEO, options=opt('YES')), 'NO',
                                   retire=[{'question': 'VIDEO', 'option': 'NO', 'drop': True, 'was': 'no'}], **kw)
        self.assertEqual(self.ready(unused(issues=[5]))['stage'], 'count')
        self.issues[1]['questions'] = ['VIDEO']
        self.assertTrue(self.ready(unused(issues=[1, 5]))['ok'])  # 지표 신호 없이 서로 다른 사용자 의견 2건
        self.issues[3]['questions'] = ['VIDEO']
        self.assertEqual(self.ready(unused(issues=[1, 3]))['stage'], 'count')  # 같은 작성자
        self.assertEqual(self.ready(unused(issues=[4, 5], basis='USER_AND_METRIC'))['metrics'], [4])
        self.assertTrue(self.ready(unused(issues=[4, 5], basis='USER_AND_METRIC'))['ok'])
        drop_q = lambda **kw: sins('DELETE_QUESTION', 'VIDEO', VIDEO, None, retire=[{'question': 'VIDEO', 'drop': True, 'was': '영상'}], **kw)
        self.assertEqual(self.ready(drop_q(issues=[6], basis='METRIC'))['stage'], 'count')
        self.issues[6]['approved'] = True
        self.assertTrue(self.ready(drop_q(issues=[6], basis='METRIC'))['ok'])
        description = sins('REWORD', 'VIDEO', VIDEO, question(VIDEO, description='설명'), basis='METRIC', issues=[4])
        self.assertEqual(self.ready(description)['stage'], 'count')
        self.assertTrue(self.ready(dict(description, issues=[4, 6]))['ok'])

    def test_근거_모양(self):
        self.assertEqual(self.ready(delete_once(issues=[1, 2], basis='METRIC'))['errors'], ['basis 는 USER 이어야 한다'])
        self.assertEqual(self.ready(delete_once(issues=[2, 1]))['errors'], ['issues 가 오름차순이 아니거나 겹친다'])
        self.assertEqual(self.ready(delete_once(issues=[1, 5]))['errors'], ['#5 의 질문에 SHORT 가 없다'])
        self.assertEqual(self.ready(delete_once(issues=[1, 8], related=[8]))['stage'], 'schema')  # 넘겨받은 이슈는 설문 PR 이 닫는다
        self.assertTrue(self.ready(delete_once(issues=[1, 8]))['ok'])
        self.issues[2]['state'] = 'closed'
        self.assertEqual(self.ready(delete_once(issues=[1, 2]))['errors'], ['#2 은 이 사용 목적의 열린 설문 피드백이 아니다'])

    def test_막는_조건과_실패_이력(self):
        processing = lambda **kw: {'issue': 20, 'type': 'survey', 'status': 'processing', 'question': 'SHORT', 'family': analyzer.REMOVING,
                                   'pr': None, 'reason': None, 'data': {'purpose': 'P'}, **kw}
        self.instructions = [processing()]
        self.assertEqual(self.ready(delete_once(issues=[1, 2]))['stage'], 'hold')
        self.instructions = [processing(data={'purpose': 'Q'})]
        self.assertTrue(self.ready(delete_once(issues=[1, 2]))['ok'])
        self.rules = [{'purpose': 'P', 'rule': SDEF['rules'][2]}]
        self.assertEqual(self.ready(delete_once(issues=[1, 2]))['stage'], 'hold')
        self.rules = [{'purpose': 'P', 'rule': {'criterion': 'MEMORY', 'when': {'question': 'TOOLS', 'anyOf': ['A']}}}]
        self.instructions = [processing(status='rejected', reason='문구')]
        out = self.ready(delete_once(issues=[1, 2]))
        self.assertEqual((out['ok'], len(out['previous'])), (True, 1))
        self.assertTrue(self.ready(sins('REWORD', 'SHORT', SHORT, question(SHORT, title='새 제목')))['ok'])  # 다른 묶음
        self.instructions.append(processing(status='failed'))
        self.assertEqual(self.ready(delete_once(issues=[1, 2]))['stage'], 'previous')

    def test_Improver_가_하지_않는_변경과_함께_더할_규칙(self):
        bad = delete_once(issues=[1, 2])
        bad['from'] = question(SHORT, title='옛 제목')
        self.assertEqual(self.ready(bad)['stage'], 'plan')
        rule = {'criterion': 'MEMORY', 'when': {'question': 'SHORT', 'anyOf': ['SOMETIMES']}, 'direction': 'UP', 'reason': '새'}
        to = question(SHORT, options=opt('OFTEN', 'ONCE', 'NEVER', 'SOMETIMES'))
        case = lambda a: [{'issue': 1, 'answers': a, 'expected': [{'plan': 'MAC', 'item': 'MEMORY', 'direction': 'UP'}]}]
        add = sins('ADD_OPTION', 'SHORT', SHORT, to, 'SOMETIMES', rule=rule, cases=case({'SHORT': ['ONCE']}))
        self.assertEqual(self.ready(add)['errors'], ['사례 #1 의 답이 함께 더할 규칙에 걸리지 않는다'])
        self.assertTrue(self.ready(dict(add, cases=case({'SHORT': ['SOMETIMES']})))['ok'])
        self.assertEqual(self.ready(dict(add, issues=[5]))['errors'], ['#5 의 질문에 SHORT 가 없다'])



class RunShTest(unittest.TestCase):
    """run.sh 의 준비와 잠금. 임시 HOME·임시 origin 과 가짜 claude 로 돌려 외부를 부르지 않는다."""

    RUN_SH = os.path.join(HERE, 'schedule', 'run.sh')
    FAKE_CLAUDE = """#!/bin/bash
# 불린 것을 남기고, 그때 잠금이 있었는지와 받은 프롬프트를 적는다
[ -d "$ST/lock" ] && echo locked > "$MARK" || echo unlocked > "$MARK"
printf '%s' "$2" > "$MARK.prompt"
ls -A "$ST/tmp" > "$MARK.tmp"
echo '{"result": "ok", "permission_denials": []}'
exit "${FAKE_RC:-0}"
"""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, self.tmp)
        git = ['git', '-c', 'user.name=t', '-c', 'user.email=t@t', '-c', 'init.defaultBranch=develop']
        src = os.path.join(self.tmp, 'src')
        for f in ['common.py', 'feedback-analyzer/survey_feedback.py', 'recommendation-improver/snapshot.py', 'recommendation-improver/survey.py'] + [f'interface/artifacts/{n}.schema.yml' for n in (
                'analysis-result', 'improvement-instruction', 'improvement-result', 'survey-instruction', 'survey-analysis', 'handover')]:
            os.makedirs(os.path.dirname(os.path.join(src, 'recommendation-improvement', f)), exist_ok=True)
            shutil.copy(os.path.join(HERE, f), os.path.join(src, 'recommendation-improvement', f))
        origin = os.path.join(self.tmp, 'origin.git')
        self.work = os.path.join(self.tmp, 'work')
        for cmd, cwd in [(git + ['init', '-q', src], None), (git + ['add', '.'], src), (git + ['commit', '-qm', 'x'], src),
                         (git + ['clone', '-q', '--bare', src, origin], None), (git + ['clone', '-q', origin, self.work], None)]:
            subprocess.run(cmd, cwd=cwd, check=True)
        bin_dir = os.path.join(self.tmp, 'bin')
        os.makedirs(bin_dir)
        with open(os.path.join(bin_dir, 'claude'), 'w') as f:
            f.write(self.FAKE_CLAUDE)
        os.chmod(os.path.join(bin_dir, 'claude'), 0o755)
        self.home = os.path.join(self.tmp, 'home')
        self.st = os.path.join(self.home, '.devica-product-recommendation', 'feedback-analyzer')
        self.mark = os.path.join(self.tmp, 'called')
        self.env = {**os.environ, 'HOME': self.home, 'PATH': bin_dir + os.pathsep + os.environ['PATH'], 'MARK': self.mark}
        self.env.pop('SELF_REF', None)

    def run_sh(self, **env):
        return subprocess.run(['bash', self.RUN_SH, 'feedback-analyzer'], cwd=self.work, env={**self.env, **env},
                              capture_output=True, text=True)

    def test_준비한_뒤_잠금을_잡고_claude_를_부르고_끝나면_푼다(self):
        r = self.run_sh()

        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        with open(self.mark) as f:
            self.assertEqual(f.read().strip(), 'locked')
        self.assertTrue(os.path.exists(os.path.join(self.st, 'common.py')))
        self.assertFalse(os.path.exists(os.path.join(self.st, 'lock')))
        self.assertEqual(len(os.listdir(os.path.join(self.st, 'runs'))), 1)

    def test_임시_디렉터리를_비워_넘기고_끝나면_지운다(self):
        os.makedirs(os.path.join(self.st, 'tmp'))
        open(os.path.join(self.st, 'tmp', 'old.md'), 'w').close()

        r = self.run_sh()

        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        with open(self.mark + '.tmp') as f:
            self.assertEqual(f.read(), '')
        self.assertFalse(os.path.exists(os.path.join(self.st, 'tmp')))

    def test_명령에_쓸_변수의_값을_프롬프트로_넘긴다(self):
        self.run_sh()

        with open(self.mark + '.prompt') as f:
            prompt = f.read()
        self.assertIn('GH_REPO=woowacourse-teams/2026-Devica', prompt)
        self.assertIn('ST=' + self.st, prompt)
        self.assertIn('SELF_REF=origin/develop', prompt)

    def test_다른_실행이_잠금을_잡고_있으면_claude_를_부르지_않는다(self):
        os.makedirs(os.path.join(self.st, 'lock'))
        os.makedirs(os.path.join(self.st, 'tmp'))

        r = self.run_sh()

        self.assertEqual(r.returncode, 0)
        self.assertFalse(os.path.exists(self.mark))
        self.assertTrue(os.path.exists(os.path.join(self.st, 'lock')))
        self.assertTrue(os.path.exists(os.path.join(self.st, 'tmp')))

    def test_6시간_넘은_잠금은_지우고_실행한다(self):
        lock = os.path.join(self.st, 'lock')
        os.makedirs(lock)
        old = time.time() - 7 * 3600
        os.utime(lock, (old, old))

        r = self.run_sh()

        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertTrue(os.path.exists(self.mark))
        self.assertFalse(os.path.exists(lock))

    def test_준비에_실패하면_claude_를_부르지_않고_잠금을_푼다(self):
        r = self.run_sh(SELF_REF='origin/없는-브랜치')

        self.assertEqual(r.returncode, 1)
        self.assertFalse(os.path.exists(self.mark))
        self.assertFalse(os.path.exists(os.path.join(self.st, 'lock')))

    def test_claude_가_실패하면_실패로_끝내고_잠금을_푼다(self):
        r = self.run_sh(FAKE_RC='3')

        self.assertEqual(r.returncode, 3)
        self.assertFalse(os.path.exists(os.path.join(self.st, 'lock')))


def clone(x):
    return json.loads(json.dumps(x))


def sq(code, show_when=None, title=None):
    q = {'code': code, 'title': title or f'{code} 질문', 'options': [{'code': 'X', 'content': '엑스'}, {'code': 'Y', 'content': '와이'}]}
    return {**q, 'showWhen': show_when} if show_when else q


SURVEY_DEF = {
    'survey': {'steps': [
        {'title': '지금 사양', 'layout': 'CURRENT_SPEC', 'questions': [sq('CUR')]},
        {'questions': [sq('A')]},
        {'questions': [sq('B')]},
        {'title': '도구', 'questions': [sq('C'), sq('D')]},
        {'questions': [sq('E', {'question': 'A', 'anyOf': ['X']})]},
        {'questions': [sq('F')]}]},
    'rules': [{'criterion': 'MEMORY', 'when': {'question': q, 'anyOf': ['X']}, 'weight': 1} for q in ('CUR', 'A', 'B', 'C', 'D', 'E')],
}
SPEC = os.path.join(HERE, 'interface', 'components', 'metric-collector.md')


class SurveySignalsTest(unittest.TestCase):
    # survey·survey-issue 가 부르는 git 을 가짜로 바꿔 끼운다. 정의서는 ref(MAIN, DEVELOP, 커밋 sha)마다 둔다
    def setUp(self):
        names = ('definition', 'main_commits', 'main_purposes', 'survey_version', 'api', 'history',
                 'interpretation_history', 'survey_history')
        self.saved = {k: getattr(collector, k) for k in names}
        self.now = int(time.time())
        self.defs = {collector.MAIN: SURVEY_DEF, collector.DEVELOP: SURVEY_DEF, 'c1': SURVEY_DEF}
        self.commits = [(self.now - 10 * 86400, 'c1', '릴리스')]
        collector.definition = lambda ref, purpose: clone(self.defs.get(ref))
        collector.main_commits = lambda purpose: self.commits
        collector.main_purposes = lambda: ['P']
        collector.survey_version = lambda ref, purpose: 'abcdef012345'

    def tearDown(self):
        for k, v in self.saved.items():
            setattr(collector, k, v)

    def session(self, screens, dwell=5, result='A.X', start=None, slow=None, pause=30):
        # 화면 번호들을 dwell 초 간격으로 보고, result 가 있으면 그 답으로 결과를 본다. slow 화면은 30초 머문다
        t, out = start or self.now - 86400, []
        for n in screens:
            out.append([str(t), '$pageview', f'/questions/{n}', 'P', 'LAPTOP', ''])
            t += pause if n == slow else dwell
        if result is not None:
            out.append([str(t), '$pageview', '/result', 'P', 'LAPTOP', result])
        return out

    def standard(self, **kw):
        # 결과까지 간 40세션(화면 3 에서 30초)과 화면 3 에서 그만둔 10세션
        return [self.session([1, 2, 3, 4, 5, 6], slow=3, **kw) for _ in range(40)] + \
               [self.session([1, 2, 3], result=None, **kw) for _ in range(10)]

    def run_survey(self, sessions):
        return collector.survey(tmp(json.dumps(sessions)), SPEC)

    def test_기준값은_명세의_표에서_읽는다(self):
        self.assertEqual(collector.thresholds(SPEC), {'days': 56, 'sample': [30.0], 'dwell': [2.0, 15.0],
                                                      'exit': [2.0, 10.0, 5.0], 'unused_days': 28})
        path = tmp(read_spec().replace('| 30세션 |', '| (정하지 않음) |'))
        self.assertIsNone(collector.thresholds(path))
        self.assertEqual(collector.survey(tmp('[]'), path)['groups'], [])

    def test_체류가_긴_화면과_이탈이_많은_화면(self):
        out = self.run_survey(self.standard())
        self.assertEqual(sorted((g['signal'], g['questions'][0]) for g in out['groups']),
                         sorted([(collector.DWELL, 'B'), (collector.EXIT, 'B')]))
        rows = {r['code']: r for r in out['screens']}
        self.assertEqual((rows['B']['sessions'], rows['B']['exits'], rows['B']['dwell_median']), (50, 10, 30))
        self.assertEqual((rows['A']['exits'], rows['C']['dwell_median']), (0, 5))
        self.assertIsNone(rows['F']['exits'])  # 노출 조건 뒤 화면은 어디서 그만뒀는지 몰라 이탈률을 내지 않는다
        self.assertNotIn('CUR', rows)
        exit_group = next(g for g in out['groups'] if g['signal'] == collector.EXIT)
        f = fields(exit_group['body'])
        self.assertEqual((f['사용 목적'], f['질문'], f['질문 제목'], f['설문 버전'], f['어떤 점이 맞지 않았나요?']),
                         ('P', 'B', 'B 질문', 'abcdef012345', '기타'))
        self.assertIn('- 이 화면: 이탈 20% (10 / 50세션)', f['근거 지표'])
        self.assertTrue(exit_group['title'].startswith('[제품 추천 설문 지표] P "B 질문"'))
        self.assertFalse(exit_group['pending'])
        self.assertEqual(collector.survey_issue(tmp(exit_group['body'])), {'key': exit_group['key'], 'pending': False})

    def test_표본이_모자라거나_기준에_닿지_않으면_신호가_아니다(self):
        self.assertEqual(self.run_survey(self.standard()[:20] + self.standard()[40:49])['groups'], [])
        few = [self.session([1, 2, 3, 4, 5, 6], slow=3) for _ in range(40)] + \
              [self.session([1, 2, 3], result=None) for _ in range(4)]
        self.assertEqual([g['signal'] for g in self.run_survey(few)['groups']], [collector.DWELL])
        fast = [self.session([1, 2, 3, 4, 5, 6]) for _ in range(40)]
        self.assertEqual(self.run_survey(fast)['groups'], [])

    def test_기준값의_경계(self):
        complete = lambda **kw: [self.session([1, 2, 3, 4, 5, 6], slow=3, **kw) for _ in range(40)]
        exits = lambda n, k: [self.session(list(range(1, n + 1)), result=None) for _ in range(k)]
        # 체류: 2배는 넘지만 15초가 안 되거나, 15초는 넘지만 2배가 안 된다
        self.assertEqual(self.run_survey(complete(pause=12))['groups'], [])
        self.assertEqual(self.run_survey(complete(dwell=10, pause=18))['groups'], [])
        # 이탈: 10% 가 안 되거나(5 / 60), 5세션이 안 되거나(4 / 34), 중앙값의 2배가 안 된다
        self.assertEqual(self.run_survey(complete(pause=5)[:35] + complete(pause=5)[:20] + exits(3, 5))['groups'], [])
        self.assertEqual(self.run_survey(complete(pause=5)[:30] + exits(3, 4))['groups'], [])
        self.assertEqual(self.run_survey(complete(pause=5) + exits(2, 6) + exits(3, 6))['groups'], [])

    def test_체류는_다른_단일_질문_화면들의_중앙값과_견준다(self):
        # A 가 Y 라 화면은 CUR, A, B, C·D, F. 머문 시간 A 5초, B 36초, C·D cd 초, F 20초. 단일 질문 화면 중앙값은 20초
        def session(cd):
            t, out = self.now - 86400, []
            for n, pause in zip(range(1, 6), (5, 5, 36, cd, 20)):
                out.append([str(t), '$pageview', f'/questions/{n}', 'P', 'LAPTOP', ''])
                t += pause
            return out + [[str(t), '$pageview', '/result', 'P', 'LAPTOP', 'A.Y']]
        out = self.run_survey([session(100) for _ in range(30)])
        self.assertEqual(sorted((g['signal'], g['questions'][0]) for g in out['groups']),
                         [(collector.DWELL, 'B'), (collector.DWELL, 'C')])
        c = next(g for g in out['groups'] if g['questions'][0] == 'C')
        self.assertEqual((c['questions'], fields(c['body'])['질문 제목']), (['C', 'D'], '도구'))
        self.assertIn('- 이 화면: 체류 중앙값 100초 (30세션, 질문 2개라 질문당 50초)', fields(c['body'])['근거 지표'])
        # 질문이 둘인 화면은 질문 수로 나눠 견준다. 70초면 질문당 35초로 20초의 2배가 안 된다
        out = self.run_survey([session(70) for _ in range(30)])
        self.assertEqual([g['questions'][0] for g in out['groups']], ['B'])

    def test_첫_결과_화면_뒤의_이동은_쓰지_않고_잇단_같은_화면은_하나로_본다(self):
        back = self.session([1, 2, 3, 4, 5, 6])
        back.insert(2, [str(int(back[1][0]) + 2), '$pageview', '/questions/2', 'P', 'LAPTOP', ''])
        back.append([str(int(back[-1][0]) + 5), '$pageview', '/questions/3', 'P', 'LAPTOP', ''])
        rows = {r['code']: r for r in self.run_survey([back])['screens']}
        self.assertEqual((rows['B']['exits'], rows['F']['dwell_median'], rows['A']['dwell_median']), (0, 5, 5))

    def test_커넥터의_표_모양_결과도_그대로_읽는다(self):
        rows = self.standard()
        text = 'views\n' + '\n'.join(repr([[int(v[0]), *v[1:]] for v in r]) for r in rows)
        self.assertEqual(collector.survey(tmp(json.dumps({'results': text})), SPEC)['screens'], self.run_survey(rows)['screens'])

    def test_결과_주소의_답으로_노출_조건을_풀어_화면_번호를_맞춘다(self):
        # A 가 Y 면 E 화면이 없어 F 가 5번이다
        out = self.run_survey([self.session([1, 2, 3, 4, 5], result='A.Y') for _ in range(30)])
        rows = {r['code']: r for r in out['screens']}
        self.assertEqual((rows['F']['sessions'], 'E' in rows), (30, False))
        out = self.run_survey([self.session([1, 2, 3, 4, 5, 6, 7], result='A.X')])
        self.assertEqual(out['dropped'], {'화면 번호가 화면 수를 넘음': 1})

    def test_배포_경계와_지금과_다른_화면은_세지_않는다(self):
        before = self.session([1, 2, 3], start=self.now - 20 * 86400)
        during = self.session([1, 2, 3], start=self.commits[0][0] + 600)
        self.assertEqual(self.run_survey([before, during])['dropped'],
                         {'main 에 정의서가 없던 때': 1, '배포 중이거나 도중에 배포된 세션': 1})
        old = clone(SURVEY_DEF)
        old['survey']['steps'][2]['questions'][0]['title'] = '옛 문구'
        self.defs['c0'] = old
        self.commits = [(self.now - 20 * 86400, 'c0', '옛 릴리스')] + self.commits
        out = self.run_survey([self.session([1, 2, 3, 4, 5, 6], start=self.now - 15 * 86400)])
        self.assertEqual(out['dropped'], {'지금 main 과 다른 화면(세션·화면)': 1})
        self.assertNotIn('B', {r['code'] for r in out['screens']})

    def test_develop_과_다른_화면은_배포_대기다(self):
        dev = clone(SURVEY_DEF)
        dev['survey']['steps'][2]['questions'][0]['options'].append({'code': 'Z', 'content': '제트'})
        self.defs[collector.DEVELOP] = dev
        groups = self.run_survey(self.standard())['groups']
        self.assertTrue(all(g['pending'] for g in groups) and groups)
        self.assertTrue(collector.survey_issue(tmp(groups[0]['body']))['pending'])

    def test_쓰이지_않는_질문은_쓰이지_않게_된_뒤_기간이_지나야_신호다(self):
        out = self.run_survey([])
        self.assertEqual(out['groups'], [])
        self.commits = [(self.now - 40 * 86400, 'c0', '옛 릴리스')] + self.commits
        self.defs['c0'] = SURVEY_DEF
        g, = self.run_survey([])['groups']
        self.assertEqual((g['signal'], g['questions'], g['pending']), (collector.UNUSED, ['F'], False))
        self.assertIn('- 쓰이지 않게 된 커밋: c0 옛 릴리스', g['body'])
        self.assertEqual(collector.survey_issue(tmp(g['body']))['key'], g['key'])
        used = clone(SURVEY_DEF)
        used['rules'].append({'criterion': 'MEMORY', 'when': {'question': 'F', 'anyOf': ['X']}, 'weight': 1})
        self.defs['c0'] = used
        self.assertEqual(self.run_survey([])['groups'], [])
        self.defs['c0'], self.defs[collector.DEVELOP] = SURVEY_DEF, used
        self.assertTrue(self.run_survey([])['groups'][0]['pending'])

    def test_설문의_뜻이_바뀌었는지(self):
        def changed(**edit):
            d = clone(SURVEY_DEF)
            q = d['survey']['steps'][2]['questions'][0]
            for k, v in edit.items():
                q[k] = v
            return collector.survey_changed(SURVEY_DEF, d)
        self.assertFalse(changed(title='새 문구'))
        self.assertFalse(changed(options=sq('B')['options'] + [{'code': 'Z', 'content': '제트'}]))
        self.assertTrue(changed(options=sq('B')['options'][:1]))
        self.assertTrue(changed(input='MULTI'))
        self.assertTrue(changed(exclusive='Y'))
        self.assertTrue(changed(showWhen={'question': 'A', 'anyOf': ['X']}))
        d = clone(SURVEY_DEF)
        d['survey']['steps'].pop()
        self.assertTrue(collector.survey_changed(SURVEY_DEF, d))
        self.assertFalse(collector.survey_changed(d, SURVEY_DEF))
        self.assertFalse(collector.survey_changed(None, SURVEY_DEF))

    def test_설문_뜻이_바뀌면_규칙_신호도_배포_뒤_세션만_센다(self):
        collector.history = lambda ref, purpose, since=None: []
        collector.interpretation_history = lambda ref, purpose, since=None: []
        deployed = []
        collector.survey_history = lambda ref, purpose, since=None: deployed
        collector.api = lambda base, purpose, a: {'status': 200, 'body': body(
            mac=('PRO', 32 if 'X' in a.get('A', []) else 16, 512))}
        rows = [[[str(self.now - 3600 + i), collector.RESULT_EVENT, '/result', 'P', 'LAPTOP', p]
                 for i, p in enumerate(('A.X', 'A.Y'))]]
        run = lambda: collector.signals(tmp(json.dumps(rows)), 'https://api', 28)
        self.assertEqual(len(run()['groups']), 1)
        deployed.append(collector.iso(self.now - 60))
        self.assertEqual(run()['dropped'], {'설문 뜻이 바뀌기 전에 본 결과': 1})
        deployed.clear()
        dev = clone(SURVEY_DEF)
        dev['survey']['steps'][1]['questions'][0]['options'].pop()
        self.defs[collector.DEVELOP] = dev
        g = None
        self.assertEqual(run()['dropped'], {'배포를 기다리는 설문 뜻 변경이 있음': 1})
        self.defs[collector.DEVELOP] = SURVEY_DEF
        g = run()['groups'][0]
        self.defs[collector.DEVELOP] = dev
        self.assertEqual(collector.issue(tmp(g['body']))['pending'], ['survey'])


def read_spec():
    with open(SPEC, encoding='utf-8') as f:
        return f.read()


if __name__ == '__main__':
    unittest.main()
