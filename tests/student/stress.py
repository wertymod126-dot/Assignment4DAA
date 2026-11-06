"""Own tests for A4: brute-force oracles on random small inputs plus full-limit scale runs.

Usage (from the repository root):
    python tests/student/stress.py            # random + scale
    python tests/student/stress.py --seed 7   # different reproducible seed

The oracles below are written independently of the Java code (brute force / simple
definitions) so a shared bug cannot hide. Every random batch is one process with mixed
tasks, which also checks that state is reset between requests.
"""
import argparse
import itertools
import json
import pathlib
import random
import subprocess
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from public_validate import check  # noqa: E402
import run  # noqa: E402


# ------------------------------------------------------------------ oracles

def lcs_len(a, b):
    # Brute force over subsequences of the shorter string (small inputs only).
    s, t = (a, b) if len(a) <= len(b) else (b, a)
    for k in range(len(s), -1, -1):
        for idx in itertools.combinations(range(len(s)), k):
            cand = ''.join(s[i] for i in idx)
            it = iter(t)
            if all(c in it for c in cand):
                return k
    return 0


def merge_cost(sizes):
    # Try every adjacent-merge order recursively (small n only).
    best = {}

    def go(blocks):
        if len(blocks) <= 1:
            return 0
        key = tuple(blocks)
        if key in best:
            return best[key]
        r = min(blocks[i] + blocks[i + 1] + go(blocks[:i] + [blocks[i] + blocks[i + 1]] + blocks[i + 2:])
                for i in range(len(blocks) - 1))
        best[key] = r
        return r
    return go(list(sizes))


def tree_weight(n, w, edges):
    best = 0
    for mask in range(1 << n):
        if any(mask >> u & 1 and mask >> v & 1 for u, v in edges):
            continue
        best = max(best, sum(w[i] for i in range(n) if mask >> i & 1))
    return best


def find_all(text, pat):
    return [i for i in range(len(text) - len(pat) + 1) if text[i:i + len(pat)] == pat]


def suffix_oracle(text):
    n = len(text)
    sa = sorted(range(n), key=lambda i: text[i:])
    lcp = [0] * n
    for r in range(1, n):
        a, b = text[sa[r - 1]:], text[sa[r]:]
        k = 0
        while k < min(len(a), len(b)) and a[k] == b[k]:
            k += 1
        lcp[r] = k
    best = ''
    for i in range(n):
        for j in range(i + 1, n + 1):
            s = text[i:j]
            if len(s) > len(best) and len(find_all(text, s)) >= 2:
                best = s
    return {'sa': sa, 'lcp': lcp, 'repeated': best}


def aho_oracle(patterns, texts):
    return [[len(find_all(t, p)) for p in patterns] for t in texts]


# ------------------------------------------------------------------ generators

def rand_str(rng, lo, hi, alpha):
    return ''.join(rng.choice(alpha) for _ in range(rng.randint(lo, hi)))


def random_tree(rng, n):
    edges = [[rng.randrange(v), v] for v in range(1, n)]
    perm = list(range(n))
    rng.shuffle(perm)
    edges = [[perm[u], perm[v]] for u, v in edges]
    for e in edges:
        if rng.random() < 0.5:
            e.reverse()
    rng.shuffle(edges)
    return edges


def random_cases(rng, count):
    cases = []
    alphas = ['ab', 'abc', 'aA .', 'xyz!?']
    for _ in range(count):
        alpha = rng.choice(alphas)
        a, b = rand_str(rng, 0, 9, alpha), rand_str(rng, 0, 9, alpha)
        cases.append(({'task': 'lcs', 'a': a, 'b': b}, {'length': lcs_len(a, b)}))

        sizes = [rng.choice([0, 1, 2, 5, 10**9, rng.randint(0, 100)]) for _ in range(rng.randint(0, 7))]
        cases.append(({'task': 'merge', 'sizes': sizes}, {'cost': merge_cost(sizes)}))

        n = rng.randint(0, 12)
        w = [rng.randint(-5, 5) for _ in range(n)]
        e = random_tree(rng, n)
        cases.append(({'task': 'tree', 'n': n, 'weights': w, 'edges': e}, {'weight': tree_weight(n, w, e)}))

        text = rand_str(rng, 0, 30, alpha)
        pat = rand_str(rng, 0, 4, alpha)
        for t in ('naive', 'kmp', 'rk'):
            cases.append(({'task': t, 'text': text, 'pattern': pat}, {'positions': find_all(text, pat)}))

        st = rand_str(rng, 0, 25, alpha)
        cases.append(({'task': 'suffix', 'text': st}, suffix_oracle(st)))

        pats = [rand_str(rng, 1, 4, alpha) for _ in range(rng.randint(0, 6))]
        if pats and rng.random() < 0.5:
            pats.append(rng.choice(pats))  # duplicate dictionary entry
        texts = [rand_str(rng, 0, 30, alpha) for _ in range(rng.randint(0, 4))]
        cases.append(({'task': 'aho', 'patterns': pats, 'texts': texts}, {'counts': aho_oracle(pats, texts)}))
    return cases


def fixed_cases():
    """Hand-written boundary cases; each targets one defect named in ANALYSIS.md."""
    return [
        ({'task': 'lcs', 'a': '', 'b': ''}, {'length': 0}),
        ({'task': 'lcs', 'a': 'aaaa', 'b': 'aa'}, {'length': 2}),
        ({'task': 'merge', 'sizes': [0, 0, 0, 0]}, {'cost': 0}),
        ({'task': 'merge', 'sizes': [1, 100, 1]}, {'cost': 203}),
        ({'task': 'merge', 'sizes': [10**9] * 200}, None),
        ({'task': 'tree', 'n': 1, 'weights': [-7], 'edges': []}, {'weight': 0}),
        ({'task': 'tree', 'n': 4, 'weights': [5, 1, 1, 1], 'edges': [[0, 1], [0, 2], [0, 3]]}, {'weight': 5}),
        ({'task': 'rk', 'text': 'djab', 'pattern': 'ab'}, {'positions': [2]}),
        ({'task': 'rk', 'text': '~~~ ~', 'pattern': '~ ~'}, {'positions': [2]}),
        ({'task': 'kmp', 'text': 'aabaabaab', 'pattern': 'aabaab'}, {'positions': [0, 3]}),
        ({'task': 'naive', 'text': 'ab', 'pattern': ''}, {'positions': [0, 1, 2]}),
        ({'task': 'suffix', 'text': 'aaaa'}, suffix_oracle('aaaa')),
        ({'task': 'suffix', 'text': 'abcd'}, suffix_oracle('abcd')),
        ({'task': 'aho', 'patterns': ['a', 'aa', 'aaa', 'a'], 'texts': ['aaaa', '', 'b']},
         {'counts': [[4, 3, 2, 4], [0, 0, 0, 0], [0, 0, 0, 0]]}),
        ({'task': 'aho', 'patterns': [], 'texts': ['abc', '']}, {'counts': [[], []]}),
    ]


def complete_expected(x, e):
    """Fill fields the validator compares but the oracle above does not produce."""
    if x['task'] == 'merge' and e is None:
        # 200 equal blocks: balanced merge costs are unique per optimum; compute with O(n^3) DP.
        s = x['sizes']; n = len(s); pre = [0]
        for v in s:
            pre.append(pre[-1] + v)
        dp = [[0] * n for _ in range(n)]
        for length in range(2, n + 1):
            for l in range(n - length + 1):
                r = l + length - 1
                dp[l][r] = min(dp[l][k] + dp[k + 1][r] for k in range(l, r)) + pre[r + 1] - pre[l]
        return {'cost': dp[0][n - 1]}
    return e


# ------------------------------------------------------------------ scale

def scale_cases(rng):
    big = []
    a = rand_str(rng, 1000, 1000, 'ACGT'); b = rand_str(rng, 1000, 1000, 'ACGT')
    big.append({'task': 'lcs', 'a': a, 'b': b})
    big.append({'task': 'merge', 'sizes': [rng.randint(0, 10**9) for _ in range(200)]})
    n = 100000
    big.append({'task': 'tree', 'n': n, 'weights': [rng.randint(-10**9, 10**9) for _ in range(n)],
                'edges': [[i, i + 1] for i in range(n - 1)]})  # deep chain
    big.append({'task': 'tree', 'n': n, 'weights': [10**9] * n, 'edges': [[0, i] for i in range(1, n)]})
    big.append({'task': 'kmp', 'text': 'a' * 200000, 'pattern': 'a' * 1000})
    big.append({'task': 'kmp', 'text': 'a' * 200000, 'pattern': ''})
    big.append({'task': 'rk', 'text': 'a' * 10000, 'pattern': 'a' * 2000})
    big.append({'task': 'suffix', 'text': 'a' * 50000})
    big.append({'task': 'suffix', 'text': rand_str(rng, 50000, 50000, 'ab')})
    big.append({'task': 'aho', 'patterns': ['a' * k for k in range(1, 441)] + ['a'] * 3420,
                'texts': ['a' * 2000] * 100})
    return big


def scale_oracle_checks(x, y):
    t = x['task']
    if t == 'kmp' and x['pattern']:
        return y['positions'] == list(range(len(x['text']) - len(x['pattern']) + 1))
    if t == 'kmp':
        return y['positions'] == list(range(len(x['text']) + 1))
    if t == 'rk':
        return y['positions'] == list(range(len(x['text']) - len(x['pattern']) + 1))
    if t == 'suffix' and set(x['text']) == {'a'}:
        n = len(x['text'])
        return (y['sa'] == list(range(n - 1, -1, -1)) and y['lcp'] == list(range(n))
                and y['repeated'] == 'a' * (n - 1))
    if t == 'suffix':
        sa, text = y['sa'], x['text']
        ok = sorted(sa) == list(range(len(text)))
        ok = ok and all(text[sa[i - 1]:] < text[sa[i]:] for i in range(1, len(sa)))
        return ok and y['lcp'][0] == 0
    if t == 'tree' and x['edges'][0] == [0, 1] and x['edges'][-1][0] == 0:
        return y['weight'] == (len(x['weights']) - 1) * 10**9
    if t == 'aho':
        L = 2000
        exp = [L - k + 1 for k in range(1, 441)] + [L] * 3420
        return all(row == exp for row in y['counts'])
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--seed', type=int, default=20261106)
    ap.add_argument('--rounds', type=int, default=150)
    args = ap.parse_args()
    rng = random.Random(args.seed)
    command = run.build('Java')

    cases = [(x, complete_expected(x, e)) for x, e in fixed_cases()] + random_cases(rng, args.rounds)
    answers = run.execute(command, [x for x, _ in cases])
    bad = 0
    for (x, e), y in zip(cases, answers):
        e = dict(e)
        if x['task'] == 'lcs':
            e.setdefault('sequence', '')
        if not check(x, y, e):
            bad += 1
            if bad <= 10:
                print('FAIL', json.dumps(x)[:200], '->', json.dumps(y)[:200], 'expected', json.dumps(e)[:200])
    print(f'random+fixed: {len(cases) - bad}/{len(cases)} passed (seed {args.seed})')

    for x in scale_cases(rng):
        t0 = time.perf_counter()
        y = run.execute(command, [x])[0]
        dt = time.perf_counter() - t0
        ok = scale_oracle_checks(x, y)
        bad += not ok
        print(f'scale {x["task"]:7s} {"OK " if ok else "BAD"} {dt:6.2f}s (process start to result)')
    return int(bool(bad))


if __name__ == '__main__':
    sys.exit(main())
