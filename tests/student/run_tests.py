"""Deterministic independent-oracle, witness and full-domain Java checks."""
import argparse
from collections import Counter
from functools import lru_cache
import json
from pathlib import Path
import platform
import random
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from run import build, execute
from public_validate import check

SEED = 20261108


def starts(text, pattern):
    # A deliberately simple oracle, independent of Java's search methods.
    return [i for i in range(len(text) - len(pattern) + 1)
            if text[i:i + len(pattern)] == pattern]


def is_subsequence(s, text):
    cursor = 0
    for char in text:
        if cursor < len(s) and s[cursor] == char:
            cursor += 1
    return cursor == len(s)


def reference(request):
    task = request['task']
    if task == 'lcs':
        a, b = request['a'], request['b']
        best = 0
        for bits in range(1 << len(a)):
            candidate = ''.join(a[i] for i in range(len(a)) if bits >> i & 1)
            if len(candidate) > best and is_subsequence(candidate, b):
                best = len(candidate)
        return {'length': best}
    if task == 'merge':
        # Enumerate actual adjacent merge choices, not interval split states.
        @lru_cache(None)
        def cheapest(blocks):
            if len(blocks) < 2:
                return 0
            return min(blocks[i] + blocks[i + 1] +
                       cheapest(blocks[:i] + (blocks[i] + blocks[i + 1],) + blocks[i + 2:])
                       for i in range(len(blocks) - 1))
        return {'cost': cheapest(tuple(request['sizes']))}
    if task == 'tree':
        n, weights, edges = request['n'], request['weights'], request['edges']
        best = 0
        for bits in range(1 << n):
            if all(not (bits >> u & 1 and bits >> v & 1) for u, v in edges):
                best = max(best, sum(weights[v] for v in range(n) if bits >> v & 1))
        return {'weight': best}
    if task in ('naive', 'kmp', 'rk'):
        return {'positions': starts(request['text'], request['pattern'])}
    if task == 'suffix':
        text = request['text']
        sa = sorted(range(len(text)), key=lambda i: text[i:])
        lcp = [0] * len(text)
        for rank in range(1, len(sa)):
            left, right = sa[rank - 1], sa[rank]
            while max(left, right) + lcp[rank] < len(text) and text[left + lcp[rank]] == text[right + lcp[rank]]:
                lcp[rank] += 1
        longest = max(lcp, default=0)
        index = lcp.index(longest) if lcp else 0
        repeated = text[sa[index]:sa[index] + longest] if sa else ''
        return {'sa': sa, 'lcp': lcp, 'repeated': repeated}
    if task == 'aho':
        return {'counts': [[len(starts(text, pattern)) for pattern in request['patterns']]
                           for text in request['texts']]}
    raise AssertionError(task)


def generated_cases():
    rng = random.Random(SEED)
    result = []
    alphabet = 'aAb !~$\\"'

    def add(name, request):
        result.append({'name': name, 'input': request, 'output': reference(request)})

    for i in range(80):
        add(f'lcs-random-{i}', {'task': 'lcs',
            'a': ''.join(rng.choices('abc ', k=rng.randrange(10))),
            'b': ''.join(rng.choices('abc ', k=rng.randrange(10)))})
    for i in range(70):
        add(f'merge-random-{i}', {'task': 'merge',
            'sizes': [rng.randrange(8) for _ in range(rng.randrange(8))]})
    for i in range(80):
        n = rng.randrange(12)
        permutation = list(range(n))
        rng.shuffle(permutation)
        edges = [[permutation[v], permutation[rng.randrange(v)]] for v in range(1, n)]
        rng.shuffle(edges)
        add(f'tree-random-{i}', {'task': 'tree', 'n': n,
            'weights': [rng.randrange(-8, 12) for _ in range(n)], 'edges': edges})
    for i in range(150):
        text = ''.join(rng.choices(alphabet, k=rng.randrange(70)))
        if i % 3 == 0 and text:
            left = rng.randrange(len(text))
            pattern = text[left:left + rng.randrange(12)]
        else:
            pattern = ''.join(rng.choices(alphabet, k=rng.randrange(12)))
        for task in ('naive', 'kmp', 'rk'):
            add(f'{task}-random-{i}', {'task': task, 'text': text, 'pattern': pattern})
    for i in range(100):
        add(f'suffix-random-{i}', {'task': 'suffix',
            'text': ''.join(rng.choices(alphabet, k=rng.randrange(70)))})
    for i in range(80):
        patterns = [''.join(rng.choices('ab !~', k=rng.randrange(1, 8)))
                    for _ in range(rng.randrange(12))]
        if patterns and i % 2 == 0:
            patterns.append(patterns[0])
        texts = [''.join(rng.choices('ab !~', k=rng.randrange(60)))
                 for _ in range(rng.randrange(6))]
        add(f'aho-random-{i}', {'task': 'aho', 'patterns': patterns, 'texts': texts})
    rng.shuffle(result)
    return result


def scale_cases():
    def case(name, request, expected):
        return {'name': name, 'input': request, 'output': expected}

    yield case('lcs-million-cells-tie', {'task': 'lcs', 'a': 'a' * 500 + 'b' * 500,
        'b': 'b' * 500 + 'a' * 500}, {'length': 500})
    printable = ''.join(map(chr, range(32, 127)))
    text = (printable * 11)[:1000]
    yield case('lcs-max-identical-ascii', {'task': 'lcs', 'a': text, 'b': text}, {'length': 1000})
    # A minimum-depth binary tree has 56 leaves at depth 7 and 144 at depth 8.
    yield case('merge-200-billion-blocks', {'task': 'merge', 'sizes': [10**9] * 200}, {'cost': 1544 * 10**9})
    yield case('merge-200-zero-blocks', {'task': 'merge', 'sizes': [0] * 200}, {'cost': 0})
    n = 100000
    yield case('tree-100000-chain', {'task': 'tree', 'n': n, 'weights': [10**9] * n,
        'edges': [[i - 1, i] for i in range(1, n)]}, {'weight': n // 2 * 10**9})
    yield case('tree-100000-star', {'task': 'tree', 'n': n, 'weights': [10**9] * n,
        'edges': [[0, i] for i in range(1, n)]}, {'weight': (n - 1) * 10**9})
    yield case('tree-100000-negative-chain', {'task': 'tree', 'n': n, 'weights': [-10**9] * n,
        'edges': [[i - 1, i] for i in range(1, n)]}, {'weight': 0})
    yield case('naive-max-overlap', {'task': 'naive', 'text': 'a' * 3000,
        'pattern': 'a' * 1500}, {'positions': list(range(1501))})
    yield case('naive-max-late-mismatch', {'task': 'naive', 'text': 'a' * 3000,
        'pattern': 'a' * 1499 + 'b'}, {'positions': []})
    yield case('naive-max-empty-pattern', {'task': 'naive', 'text': '~' * 3000,
        'pattern': ''}, {'positions': list(range(3001))})
    yield case('kmp-max-overlap', {'task': 'kmp', 'text': 'a' * 200000,
        'pattern': 'a' * 100000}, {'positions': list(range(100001))})
    yield case('kmp-max-late-mismatch', {'task': 'kmp', 'text': 'a' * 200000,
        'pattern': 'a' * 99999 + 'b'}, {'positions': []})
    yield case('kmp-max-empty-pattern', {'task': 'kmp', 'text': '~' * 200000,
        'pattern': ''}, {'positions': list(range(200001))})
    yield case('rk-product-limit-overlap', {'task': 'rk', 'text': 'a' * 10000,
        'pattern': 'a' * 2000}, {'positions': list(range(8001))})
    yield case('rk-longer-pattern-limit', {'task': 'rk', 'text': 'a' * 2000,
        'pattern': 'a' * 10000}, {'positions': []})
    yield case('rk-max-empty-pattern', {'task': 'rk', 'text': '~' * 10000,
        'pattern': ''}, {'positions': list(range(10001))})
    n = 50000
    yield case('suffix-50000-identical', {'task': 'suffix', 'text': 'a' * n},
        {'sa': list(range(n - 1, -1, -1)), 'lcp': list(range(n)), 'repeated': 'a' * (n - 1)})
    yield case('suffix-50000-periodic', {'task': 'suffix', 'text': 'ab' * (n // 2)},
        {'sa': list(range(n - 2, -1, -2)) + list(range(n - 1, -1, -2)),
         'lcp': list(range(0, n, 2)) + [0] + list(range(1, n - 1, 2)),
         'repeated': 'ab' * (n // 2 - 1)})
    yield case('aho-100001-states', {'task': 'aho', 'patterns': ['a' * 100000],
        'texts': ['a' * 200000]}, {'counts': [[100001]]})
    yield case('aho-million-output-counts', {'task': 'aho', 'patterns': ['a' * 10] * 10000,
        'texts': ['a' * 2000] * 100}, {'counts': [[1991] * 10000 for _ in range(100)]})
    patterns = ['a' * i for i in range(1, 401)]
    yield case('aho-dense-suffix-chain', {'task': 'aho', 'patterns': patterns,
        'texts': ['a' * 100000, '', 'a' * 100000]},
        {'counts': [[100001 - i for i in range(1, 401)], [0] * 400,
                    [100001 - i for i in range(1, 401)]]})
    patterns = [c * 1000 for c in printable]
    yield case('aho-wide-alphabet-trie', {'task': 'aho', 'patterns': patterns,
        'texts': [''.join(c * 2000 for c in printable)]}, {'counts': [[1001] * 95]})
    yield case('aho-empty-dictionary-max-texts', {'task': 'aho', 'patterns': [],
        'texts': ['~' * 2000] * 100}, {'counts': [[] for _ in range(100)]})


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--skip-build', action='store_true')
    args = parser.parse_args()
    command = (['java', '-Xmx384m', '-cp', str(ROOT / 'Java' / 'build'), 'Main']
               if args.skip_build else build('Java'))
    cases = json.loads((Path(__file__).with_name('cases.json')).read_text(encoding='utf-8'))
    generated = generated_cases()
    report = {'seed': SEED, 'python': platform.python_version(), 'platform': platform.platform(),
              'java': subprocess.run(['java', '-version'], capture_output=True, text=True).stderr.strip(),
              'scope': 'Java process startup, JSON transport and algorithm; 20-second timeout per batch',
              'fixture_results': [], 'batches': []}
    total = 0
    by_task = Counter()

    def run_batch(label, batch, record_fixtures=False):
        nonlocal total
        start = time.perf_counter()
        answers = execute(command, [c['input'] for c in batch])
        elapsed = time.perf_counter() - start
        for case, answer in zip(batch, answers):
            if not check(case['input'], answer, case['output']):
                raise AssertionError(f"{case['name']}: expected {str(case['output'])[:1200]}, actual {str(answer)[:1200]}")
            total += 1
            by_task[case['input']['task']] += 1
            if record_fixtures:
                report['fixture_results'].append({'name': case['name'], 'input': case['input'],
                    'expected': case['output'], 'actual': answer, 'status': 'PASS'})
        report['batches'].append({'name': label, 'cases': len(batch), 'seconds': round(elapsed, 6), 'status': 'PASS'})
        print(f'PASS {label}: {len(batch)} cases ({elapsed:.3f} s)', flush=True)

    run_batch('named boundary fixtures', cases, True)
    run_batch('independent small oracles', generated)
    mixed = cases + generated[:80]
    run_batch('repeated mixed state reset', mixed + list(reversed(mixed)) + mixed)
    for case in scale_cases():
        run_batch(case['name'], [case])
    report.update(total_cases=total, by_task=dict(sorted(by_task.items())), status='PASS')
    Path(__file__).with_name('results.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'PASS all {total} checks; results written to tests/student/results.json')


if __name__ == '__main__':
    main()
