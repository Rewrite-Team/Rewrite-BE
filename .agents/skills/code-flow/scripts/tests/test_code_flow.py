import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import code_flow as flow


class FlowTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        (self.root / 'Example.java').write_text('class Example {\n  void run() {}\n}\n')
        self.model = dict(version=1, title='흐름', entry='a', omitted=[], uncertain=[],
                          sources=[dict(id='s', path='Example.java', start=1, end=3)],
                          calls=[dict(id='a', **{'class': 'Example'}, method='run()',
                                      source='s', steps=[], returns='void')])

    def child(self):
        c = copy.deepcopy(self.model['calls'][0]); c['id'] = 'b'
        self.model['calls'].append(c)
        self.model['calls'][0]['steps'] = [dict(type='call', target='b', source='s')]

    def test_render_offline_and_exact_excerpt(self):
        outputs = flow.export(self.model, self.root, self.root / 'out' / 'flow')
        document = (self.root / 'out/flow.html').read_text()
        captured = json.loads(document.split('<script id="flow-data" type="application/json">')[1].split('</script>')[0])
        self.assertEqual(captured['sources'][0]['excerpt'], 'class Example {\n  void run() {}\n}')
        self.assertEqual(len(outputs), 3)
        self.assertIn('connect-src \'none\'', document)
        self.assertEqual(flow.check_snapshot(captured['snapshot'], self.root), [])
        self.assertIn('class Example {\n  void run() {}\n}', (self.root / 'out/flow.md').read_text())

    def test_stale_on_change_or_delete_even_outside_excerpt(self):
        self.model['sources'][0]['end'] = 1
        snap = flow.capture(self.model, self.root)['snapshot']
        (self.root / 'Example.java').write_text('class Example {\n  void changed() {}\n}\n')
        self.assertEqual(flow.check_snapshot(snap, self.root), ['Example.java'])
        (self.root / 'Example.java').unlink()
        self.assertEqual(flow.check_snapshot(snap, self.root), ['Example.java'])

    def test_markdown_preserves_call_interpretation(self):
        self.model['calls'][0]['note'] = '비동기 스택은 요청 TX를 상속하지 않는다'
        data = flow.capture(self.model, self.root)
        self.assertIn(self.model['calls'][0]['note'], flow.markdown(data, 'flow.html'))

    def test_untrusted_text_cannot_close_script_or_replace_template(self):
        payload = '</script><script>alert(1)</script> @@SCRIPT@@ & <img src=x>'
        self.model['title'] = payload
        (self.root / 'Example.java').write_text(payload + '\nline2\nline3')
        flow.export(self.model, self.root, self.root / 'flow')
        document = (self.root / 'flow.html').read_text()
        self.assertNotIn('</script><script>alert(1)', document)
        encoded = document.split('<script id="flow-data" type="application/json">')[1].split('</script>')[0]
        self.assertEqual(json.loads(encoded)['title'], payload)
        self.assertEqual(document.count('<script'), 2)

    def test_source_paths_confined(self):
        for path in ('../Example.java', '/tmp/Example.java', '.git/config', '..\\Example.java'):
            with self.subTest(path=path), self.assertRaises(flow.FlowError):
                flow.safe_path(self.root, path)
        with tempfile.TemporaryDirectory() as outside:
            (self.root / 'link').symlink_to(outside, target_is_directory=True)
            with self.assertRaises(flow.FlowError):
                flow.safe_path(self.root, 'link/secret.java')

    def test_invalid_range_and_unknown_source(self):
        self.model['sources'][0]['end'] = 4
        with self.assertRaises(flow.FlowError): flow.capture(self.model, self.root)
        self.model['sources'][0]['end'] = 3
        self.model['calls'][0]['source'] = 'missing'
        with self.assertRaises(flow.FlowError): flow.validate(self.model)

    def test_reused_cycle_unreachable_and_unknown_target(self):
        self.child()
        valid = copy.deepcopy(self.model)
        mutations = [lambda m: m['calls'][0]['steps'].append(dict(type='call', target='b', source='s')),
                     lambda m: m['calls'][1]['steps'].append(dict(type='call', target='a', source='s')),
                     lambda m: m['calls'][0].update(steps=[]),
                     lambda m: m['calls'][0]['steps'][0].update(target='missing')]
        for mutate in mutations:
            m = copy.deepcopy(valid); mutate(m)
            with self.assertRaises(flow.FlowError): flow.validate(m)

    def test_async_branch_and_terminal_path(self):
        self.child()
        self.model['calls'][0]['steps'] = [dict(type='branch', label='조건', source='s', cases=[
            dict(when='진행', steps=[dict(type='async', target='b', source='s', label='job', trigger='AFTER_COMMIT')]),
            dict(when='종료', steps=[dict(type='return', text='종료', source='s')])])]
        flow.validate(self.model)
        self.model['calls'][0]['steps'][0]['cases'][1]['steps'].append(dict(type='note', text='도달 불가', source='s'))
        with self.assertRaises(flow.FlowError): flow.validate(self.model)

    def test_symlink_output_refused(self):
        (self.root / 'flow.html').symlink_to(self.root / 'Example.java')
        with self.assertRaises(flow.FlowError): flow.export(self.model, self.root, self.root / 'flow')
        self.assertTrue((self.root / 'Example.java').read_text().startswith('class Example'))

    def test_invalid_shape_is_validation_error(self):
        for bad in ([], {}, dict(self.model, version=True), dict(self.model, coordinates=[])):
            with self.assertRaises(flow.FlowError): flow.validate(bad)


if __name__ == '__main__':
    unittest.main()
