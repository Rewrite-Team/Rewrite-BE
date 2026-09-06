#!/usr/bin/env python3
"""Validate source-grounded flow descriptions and export an offline viewer (stdlib only)."""
import argparse
import copy
import hashlib
import html
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path, PurePosixPath


class FlowError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise FlowError(message)


def string(value, where):
    require(isinstance(value, str) and bool(value.strip()), f'{where}: non-empty string required')
    require(len(value) <= 12000, f'{where}: text too long')
    return value


def keys(value, required, optional, where):
    require(isinstance(value, dict), f'{where}: object required')
    require(set(required) <= value.keys(), f'{where}: missing {set(required) - value.keys()}')
    require(value.keys() <= set(required) | set(optional), f'{where}: unknown fields {value.keys() - set(required) - set(optional)}')


def safe_path(root, value):
    string(value, 'source.path')
    path = PurePosixPath(value)
    require(not path.is_absolute() and '..' not in path.parts and '\\' not in value,
            f'source path must be repository-relative: {value}')
    require(path.parts and path.parts[0] not in {'.git', '.env'}, f'unsupported source path: {value}')
    resolved = (root / value).resolve()
    require(resolved.is_relative_to(root.resolve()), f'source leaves repository: {value}')
    return resolved


def validate(model):
    keys(model, ['version', 'title', 'entry', 'sources', 'calls', 'omitted', 'uncertain'], ['description'], 'flow')
    require(type(model['version']) is int and model['version'] == 1, 'version must be 1')
    string(model['title'], 'title')
    string(model['entry'], 'entry')
    if 'description' in model:
        string(model['description'], 'description')
    for field in ('omitted', 'uncertain'):
        require(isinstance(model[field], list), f'{field}: list required')
        for text in model[field]:
            string(text, field)
    for field in ('sources', 'calls'):
        require(isinstance(model[field], list) and 0 < len(model[field]) <= 300, f'{field}: 1..300 entries required')
    sources = {}
    for source in model['sources']:
        keys(source, ['id', 'path', 'start', 'end'], [], 'source')
        for field in ('id', 'path'):
            string(source[field], f'source.{field}')
        require(source['id'] not in sources, f'duplicate source: {source["id"]}')
        require(type(source['start']) is int and type(source['end']) is int
                and 1 <= source['start'] <= source['end'], 'invalid source line range')
        sources[source['id']] = source
    calls = {}
    for call in model['calls']:
        keys(call, ['id', 'class', 'method', 'source', 'steps', 'returns'], ['note', 'tx'], 'call')
        for field in ('id', 'class', 'method', 'source', 'returns'):
            string(call[field], f'call.{field}')
        require(call['id'] not in calls, f'duplicate call: {call["id"]}')
        require(call['source'] in sources, f'unknown source: {call["source"]}')
        if 'note' in call:
            string(call['note'], 'call.note')
        if 'tx' in call:
            keys(call['tx'], ['id', 'label'], [], 'tx')
            string(call['tx']['id'], 'tx.id')
            string(call['tx']['label'], 'tx.label')
        calls[call['id']] = call
    require(model['entry'] in calls, 'entry does not identify a call')
    edges = {key: [] for key in calls}
    step_count = 0

    def steps(items, owner, depth=0):
        nonlocal step_count
        require(isinstance(items, list), 'steps must be a list')
        require(depth <= 16, 'branch nesting exceeds 16')
        for index, item in enumerate(items):
            step_count += 1
            require(step_count <= 1500, 'too many steps')
            require(isinstance(item, dict), 'step must be an object')
            kind = item.get('type')
            if kind in ('call', 'async'):
                required = ['type', 'target', 'source']
                if kind == 'async':
                    required += ['label', 'trigger']
                keys(item, required, [], kind)
                require(isinstance(item['target'], str) and item['target'] in calls, 'unknown call target')
                require(isinstance(item['source'], str) and item['source'] in sources, 'unknown call-site source')
                if kind == 'async':
                    string(item['label'], 'async.label')
                    string(item['trigger'], 'async.trigger')
                edges[owner].append(item['target'])
            elif kind in ('note', 'return', 'throw'):
                keys(item, ['type', 'text', 'source'], [], kind)
                string(item['text'], f'{kind}.text')
                require(isinstance(item['source'], str) and item['source'] in sources, 'unknown step source')
                if kind != 'note':
                    require(index == len(items) - 1, f'{kind} must be the last step of its path')
            elif kind == 'branch':
                keys(item, ['type', 'label', 'cases', 'source'], [], 'branch')
                string(item['label'], 'branch.label')
                require(isinstance(item['source'], str) and item['source'] in sources, 'unknown branch source')
                require(isinstance(item['cases'], list) and 2 <= len(item['cases']) <= 20, 'branch needs 2..20 alternatives')
                for case in item['cases']:
                    keys(case, ['when', 'steps'], [], 'case')
                    string(case['when'], 'case.when')
                    steps(case['steps'], owner, depth + 1)
            else:
                raise FlowError(f'unknown step type: {kind}')

    for call in calls.values():
        steps(call['steps'], call['id'])
    visited, active = set(), set()

    def visit(key, depth=0):
        require(key not in active, f'call cycle at {key}; summarize recursion instead')
        require(key not in visited, f'call {key} reused; assign each invocation a unique id')
        require(depth <= 24, 'call depth exceeds 24; summarize deeper calls')
        active.add(key)
        for child in edges[key]:
            visit(child, depth + 1)
        active.remove(key)
        visited.add(key)

    visit(model['entry'])
    require(visited == set(calls), f'unreachable calls: {set(calls) - visited}')
    return model


def read_json(path):
    require(path.stat().st_size <= 2_000_000, 'JSON input exceeds 2 MB')
    return json.loads(path.read_text(encoding='utf-8'))


def digest(data):
    return hashlib.sha256(data).hexdigest()


def capture(model, root):
    validate(model)
    root = root.resolve()
    captured = copy.deepcopy(model)
    files = {}
    for source in captured['sources']:
        path = safe_path(root, source['path'])
        require(path.is_file(), f'source file missing: {source["path"]}')
        require(path.stat().st_size <= 2_000_000, 'source file exceeds 2 MB')
        if source['path'] not in files:
            raw = path.read_bytes()
            files[source['path']] = (digest(raw), raw.decode('utf-8').splitlines())
        sha, lines = files[source['path']]
        require(source['end'] <= len(lines), f'source range outside file: {source["id"]}')
        source['excerpt'] = '\n'.join(lines[source['start'] - 1:source['end']])
        source['sha256'] = sha
    result = subprocess.run(['git', '-C', str(root), 'rev-parse', 'HEAD'], capture_output=True, text=True)
    captured['snapshot'] = {
        'generatedAt': datetime.now(timezone.utc).isoformat(timespec='seconds'),
        'commit': result.stdout.strip() if result.returncode == 0 else None,
        'modelSha256': digest(json.dumps(model, ensure_ascii=False, sort_keys=True).encode()),
        'files': {path: sha for path, (sha, _) in sorted(files.items())},
    }
    return captured


def check_snapshot(snapshot, root):
    keys(snapshot, ['generatedAt', 'commit', 'modelSha256', 'files'], [], 'snapshot')
    require(isinstance(snapshot['files'], dict) and bool(snapshot['files']), 'snapshot.files is empty')
    changed = []
    for name, expected in snapshot['files'].items():
        require(isinstance(expected, str) and re.fullmatch(r'[a-f0-9]{64}', expected), 'invalid SHA-256')
        path = safe_path(root.resolve(), name)
        if not path.is_file() or digest(path.read_bytes()) != expected:
            changed.append(name)
    return changed


def safe_json(data):
    # A source excerpt may contain </script>, even in a Java comment or string.
    return json.dumps(data, ensure_ascii=False).replace('&', '\\u0026').replace('<', '\\u003c').replace('>', '\\u003e')


def md_text(text):
    return re.sub(r'([\\`*_{}\[\]()#+.!|>-])', r'\\\1', html.escape(text))


def markdown(data, html_name):
    calls = {c['id']: c for c in data['calls']}
    lines = [f'# {md_text(data["title"])}', '', f'[인터랙티브 흐름 열기](./{html_name})', '',
             '소스에서 해석한 구조도입니다. 실행 시간·스레드 간 선후관계를 측정한 trace가 아닙니다.', '',
             f'생성: {data["snapshot"]["generatedAt"]} · Git 기준: {data["snapshot"]["commit"] or "없음"}', '', '## 흐름', '']

    def walk_call(key, depth):
        call = calls[key]
        lines.append('  ' * depth + '- ' + md_text(call['class'] + '.' + call['method']))
        if call.get('note'):
            lines.append('  ' * (depth + 1) + '- 설명: ' + md_text(call['note']))
        if call.get('tx'):
            lines.append('  ' * (depth + 1) + '- TX: ' + md_text(call['tx']['id'] + ' · ' + call['tx']['label']))
        walk_steps(call['steps'], depth + 1)
        lines.append('  ' * (depth + 1) + '- 반환/종료: ' + md_text(call['returns']))

    def walk_steps(steps, depth):
        for step in steps:
            if step['type'] == 'call':
                walk_call(step['target'], depth)
            elif step['type'] == 'async':
                lines.append('  ' * depth + '- 비동기 원인 연결: ' + md_text(step['label'] + ' · ' + step['trigger']))
                walk_call(step['target'], depth + 1)
            elif step['type'] == 'branch':
                lines.append('  ' * depth + '- 대안 분기: ' + md_text(step['label']))
                for case in step['cases']:
                    lines.append('  ' * (depth + 1) + '- 조건: ' + md_text(case['when']))
                    walk_steps(case['steps'], depth + 2)
            else:
                lines.append('  ' * depth + '- ' + md_text(step['text']))

    walk_call(data['entry'], 0)
    for field, title in [('omitted', '생략 범위'), ('uncertain', '미확정 사항')]:
        lines += ['', f'## {title}', ''] + ['- ' + md_text(t) for t in data[field]]
        if not data[field]:
            lines.append('모델 작성자가 별도로 기록한 항목 없음. 분석의 완전성을 보장하지 않습니다.')
    lines += ['', '## 코드 근거', '']
    for source in data['sources']:
        fence = '`' * max(3, 1 + max([len(x) for x in re.findall(r'`+', source['excerpt'])] or [0]))
        lines += [f'### {md_text(source["id"])}', '',
                  md_text(f'{source["path"]}:{source["start"]}-{source["end"]}'), '',
                  f'SHA-256: {source["sha256"]}', '', fence + 'java', source['excerpt'], fence, '']
    return '\n'.join(lines)


def export(model, root, output):
    data = capture(model, root)
    assets = Path(__file__).resolve().parent.parent / 'assets'
    template = (assets / 'viewer.html').read_text(encoding='utf-8')
    substitutions = {'TITLE': html.escape(data['title']), 'STYLE': (assets / 'viewer.css').read_text(encoding='utf-8'),
                     'SCRIPT': (assets / 'viewer.js').read_text(encoding='utf-8'), 'DATA': safe_json(data)}
    # One substitution pass: data containing template token text stays literal.
    document = re.sub(r'@@(TITLE|STYLE|SCRIPT|DATA)@@', lambda m: substitutions[m[1]], template)
    outputs = {Path(str(output) + '.html'): document,
               Path(str(output) + '.md'): markdown(data, Path(str(output) + '.html').name),
               Path(str(output) + '.snapshot.json'): json.dumps(data['snapshot'], ensure_ascii=False, indent=2) + '\n'}
    for path in outputs:
        require(not path.is_symlink(), f'refusing symlink output: {path}')
        require(path.resolve() not in {safe_path(root, s['path']) for s in model['sources']}, 'output would overwrite source')
    output.parent.mkdir(parents=True, exist_ok=True)
    for path, content in outputs.items():
        path.write_text(content, encoding='utf-8')
    return outputs


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    for command in ('validate', 'render'):
        item = sub.add_parser(command)
        item.add_argument('--repo', type=Path, required=True)
        item.add_argument('--input', type=Path, required=True)
        if command == 'render':
            item.add_argument('--out', type=Path, required=True, help='output filename prefix')
    check = sub.add_parser('check')
    check.add_argument('--repo', type=Path, required=True)
    check.add_argument('--snapshot', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.command == 'check':
            changed = check_snapshot(read_json(args.snapshot), args.repo)
            print('\n'.join(['STALE: regenerate after rereading sources', *changed]) if changed else 'CURRENT: recorded source hashes match')
            return 1 if changed else 0
        model = read_json(args.input)
        if args.command == 'validate':
            capture(model, args.repo)
            print('VALID: structure and source ranges checked; semantics require code review')
        else:
            for path in export(model, args.repo.resolve(), args.out):
                print(path.resolve())
        return 0
    except (FlowError, OSError, UnicodeError, json.JSONDecodeError) as error:
        print(f'ERROR: {error}', file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
