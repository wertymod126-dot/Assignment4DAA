# Assignment 4 — analysis

## Environment and commands

- Language: Java (compiled with `--release 17`), runtime OpenJDK 21.0.12.1 (Temurin), launcher Python 3.14.8.
- Machine: Windows 11 Pro, Intel Core i7-13620H, 15.6 GB RAM.
- Source: `Java/source/Algorithms.java` (all eight commands). `Main.java` and `Json.java` are the supplied transport and are unchanged.

```sh
python tools/run.py --language Java --setup
python tools/run.py --language Java --check          # 44 public cases + repeated batch
python tests/student/stress.py                       # own tests (seed 20261106)
python tests/student/stress.py --seed 1 --rounds 300 # own tests, second seed
javac --release 17 -d Java/build-bench Java/source/*.java Java/bench/SearchBench.java
java -Xmx384m -cp Java/build-bench SearchBench       # search comparison
```

Results on this machine: setup PASS; all 44 public cases PASS; state-reset batch PASS; own tests 1215/1215 (seed 20261106) and 2415/2415 (seed 1); all 10 scale runs correct in under 0.5 s each.

## Algorithm explanations

### lcs — prefix DP

- **State:** `dp[i][j]` = length of an LCS of the prefixes `a[0..i)` and `b[0..j)`. Stored as one flat `int` array of size (|a|+1)(|b|+1).
- **Base cases:** `dp[0][j] = dp[i][0] = 0` (an empty prefix has only the empty common subsequence). If either string is empty the function returns `length=0, sequence=""` immediately.
- **Transition:** if `a[i-1] == b[j-1]` then `dp[i][j] = dp[i-1][j-1] + 1`; otherwise `dp[i][j] = max(dp[i-1][j], dp[i][j-1])`.
- **Order:** rows `i = 1..n`, columns `j = 1..m`; the three cells used (up, left, up-left) are already filled.
- **Correctness:** when the last characters match, some optimal LCS ends by pairing them (exchange argument), so we add 1 to the LCS of the shorter prefixes. When they differ, at least one of the two last characters is not used in an optimal LCS, so dropping it from `a` or from `b` loses nothing; taking the max of both options covers both cases.
- **Reconstruction:** start at `(n, m)`. On a match, emit the character and move diagonally. Otherwise move to the neighbour that holds the max (ties go up). Each step keeps `dp` equal to the remaining length, so the result has the optimal length and is a subsequence of both strings. Characters are written from the back.
- **Complexity:** O((|a|+1)(|b|+1)) time and memory; output O(length).
- **Test:** `a="aaaa", b="aa"` → length 2. This catches code that counts every matching pair instead of taking one character per position.

### merge — interval DP

- **State:** `cost[l][r]` = minimum total cost to merge original blocks `l..r` into one block. `split[l][r]` stores the best last split `k`.
- **Base cases:** `cost[i][i] = 0`. For n ≤ 1 return cost 0 and an empty plan.
- **Transition:** the last merge of `[l, r]` joins `[l, k]` and `[k+1, r]` for some `k`, and costs `sum(l..r)` whatever `k` is. So `cost[l][r] = min_k (cost[l][k] + cost[k+1][r]) + sum(l..r)`. Range sums come from a `long` prefix-sum array.
- **Order:** by increasing interval length, so both sub-intervals are already computed.
- **Correctness:** every valid plan for `[l, r]` ends with exactly one merge of two adjacent blocks that cover `[l, k]` and `[k+1, r]`. The two halves are merged independently, so each half should be optimal (optimal substructure). Trying every `k` therefore finds the optimum. Greedily merging the two smallest blocks anywhere is invalid because they may not be adjacent.
- **Reconstruction:** iterative postorder over the split tree (explicit stack). The merge `[l, k, r]` is emitted only after every merge inside `[l, k]` and `[k+1, r]`, because those two blocks must exist before they can be joined. The plan has exactly n−1 entries.
- **Overflow:** the worst total is about 200 × 200 × 10⁹ = 4·10¹³, which fits in `long`.
- **Complexity:** O(n³) time, O(n²) memory (`cost` + `split`); O(1) for n = 0.
- **Test:** `[1, 100, 1]` → 203 (both orders cost 101+102). Also 200 blocks of 10⁹ checks 64-bit sums.

### tree — maximum-weight independent set (take/skip)

- **State:** for vertex `v` with the tree rooted at 0: `take[v]` = best weight in `v`'s subtree with `v` selected; `skip[v]` = best weight with `v` not selected.
- **Transition:** `take[v] = w[v] + Σ skip[c]`, `skip[v] = Σ max(take[c], skip[c])` over children `c`.
- **Base case:** a leaf has `take = w[v]`, `skip = 0`. n = 0 returns weight 0, selected [].
- **Order:** adjacency is stored in compact arrays (CSR). A BFS from root 0 gives an order where each parent comes before its children; processing that order **in reverse** is a valid postorder (all children done before the parent). No recursion is used, so a 100 000-vertex chain cannot overflow the stack.
- **Correctness:** if `v` is taken, no child may be taken; if `v` is skipped, each child subtree is independent and picks its own best. The answer is `max(take[0], skip[0])`. `skip ≥ 0` always (the empty set), so all-negative inputs give 0. Any root gives the same optimum because the optimum is a property of the tree, not of the rooting.
- **Reconstruction:** walk in BFS order. A vertex is selected only if its parent was not selected **and** `take[v] > skip[v]`. The parent's decision restricts the child: a selected parent forces skip, which matches how `take[parent]` was computed (it used `skip[c]`). An unselected parent used `max(take[c], skip[c])`, so the child picks the bigger one. Each vertex is visited once, so no duplicates.
- **Complexity:** O(n + 1) time and memory; weights summed in `long` (up to 10¹⁴).
- **Test:** a 100 000-vertex chain (deep recursion would crash) and a star with weight 10⁹ on every vertex (expected 99 999·10⁹, checks `long` sums and the parent restriction).

### naive

- **Invariant:** each start `s` with `0 ≤ s ≤ n − m` is checked by comparing characters until a mismatch or `m` matches.
- **Edge cases:** empty pattern → all `n + 1` boundaries `0..n` (the final boundary `n` is included). `m > n` → the loop does not run, giving `[]`. Overlaps are found because every start is tried, not only starts after the previous match.
- **Complexity:** O(n·m + n + 1) time, O(1) auxiliary memory, O(z + 1) output.
- **Test:** `text="ab", pattern=""` → `[0, 1, 2]` (catches a missing final boundary).

### kmp

- **Definition:** `pi[i]` = length of the longest proper prefix of `p[0..i]` that is also a suffix of it.
- **Prefix function:** for `i ≥ 1`, start with `k = pi[i-1]`; while `k > 0` and `p[i] ≠ p[k]`, fall back `k = pi[k-1]`; if `p[i] = p[k]` then `k++`.
- **Search:** `j` = number of pattern characters matched so far. On a mismatch, fall back `j = pi[j-1]`, which keeps the longest pattern prefix that is still a suffix of the text read so far, so no possible start is skipped. On a full match (`j == m`), report `i − m + 1` and set `j = pi[m-1]` instead of 0, so overlapping matches are kept.
- **Edge cases:** empty pattern returns `0..n` before any indexing into the pattern; `m > n` returns `[]`.
- **Complexity:** O(n + m + z + 1) time (each fallback undoes an earlier increment, so total fallbacks ≤ total increments); O(m + 1) auxiliary memory; O(z + 1) output.
- **Test:** `text="aabaabaab", pattern="aabaab"` → `[0, 3]`; resetting `j` to 0 after a match would miss 3.

### rk — Rabin–Karp, base 31, modulus 101

- **Hash:** `H(s) = Σ s[i]·31^(m−1−i) mod 101` using ASCII codes; the leftmost character has the highest power. Example: `"ab"` → (97·31 + 98) mod 101 = 75, the same as `"dj"`.
- **Rolling update:** with `high = 31^(m−1) mod 101`, the next window is `((h − t[s]·high) mod 101 + 101) mod 101`, then `(h·31 + t[s+m]) mod 101`. The `+101` normalizes negative remainders, which Java's `%` can produce.
- **Verification:** an equal hash is only a candidate. Characters are compared before a start is reported, so collisions such as `ab`/`dj` are rejected.
- **Edge cases:** `m = 0` and `m > n` are handled before forming a window.
- **Complexity:** O(n + m + c·m), where c is the number of equal-hash windows. With modulus 101 roughly 1 in 101 random windows collide, and in the worst case every window is a candidate, so the bound over all inputs is O(n·m + n + m + 1). It is not linear in the worst case. Auxiliary memory O(1) (rolling window) plus O(z + 1) output.
- **Tests:** `text="abdj", pattern="ab"` → `[0]` and `text="djab"` → `[2]` (collision must be verified); `text="~~~ ~", pattern="~ ~"` → `[2]` (high codes make the subtraction negative, catching missing normalization).

### suffix — prefix doubling + Kasai

**Prefix doubling.**
- **Invariant:** before the round with step `k`, `rank[i]` orders suffixes by their first `k` characters (equal ranks mean equal first `k` characters).
- **Round:** suffix `i` gets the pair `(rank[i], rank[i+k])`, with `−1` (smallest) when `i + k ≥ n`; a shorter suffix that is a prefix of a longer one comes first. Sorting by that pair orders by the first `2k` characters.
- **Sorting:** two-pass radix sort per round. First, order by the second key, which is free: suffixes with `i + k ≥ n` first, then `sa[j] − k` for each `sa[j] ≥ k` in current order. Then a stable counting sort by the first key. New ranks are assigned by comparing neighbouring pairs.
- **Start/stop:** the initial sort is a counting sort on ASCII codes. Stop when all ranks are different (at most ⌈log₂ n⌉ rounds).
- **No sentinel** is added and no suffix strings are created; only integer arrays.
- **Complexity:** O(n log(n + 1)) time, O(n + 1) memory.

**Kasai LCP.**
- `lcp[r]` = LCP of `sa[r−1]` and `sa[r]`; `lcp[0] = 0`.
- Process suffixes in text order `i = 0, 1, …`. If suffix `i` shares `h` characters with its predecessor `j` in `sa`, then suffix `i+1` shares at least `h − 1` with the suffix `j+1`, which comes before it. So `h` is reused and only decreases by 1 per step. `h` grows by at most 2n in total: O(n).

**Longest repeat.** Any substring that occurs at two starts is a common prefix of two suffixes. The suffixes that share the longest prefix with a given suffix are next to it in sorted order, so the longest repeat has length `max(lcp)`. It is read as `text[sa[r] .. sa[r] + lcp[r])` for the `r` with the largest `lcp`. If `max(lcp) = 0`, `repeated = ""`. Overlapping occurrences are allowed (e.g. `"ana"` in `banana`).

- **Tests:** `"aaaa"` (all equal: sa = [3,2,1,0], lcp = [0,1,2,3], repeat "aaa"); `"abcd"` (all distinct: repeat ""); 50 000 × `'a'` and a random 50 000-character `a/b` text (scale; sa checked by direct suffix comparison).

### aho — Aho–Corasick with reverse-failure counting

- **Trie:** one state per distinct pattern prefix; transitions stored in a flat `int` table of `95·S` entries (printable ASCII 32..126). Duplicate patterns end at the **same** terminal state; `terminal[i]` records each dictionary position's state.
- **Failure links:** BFS from the root. `fail[u]` = the state for the longest proper suffix of `u`'s string that is also a trie prefix. Missing transitions are filled with `go[fail[v]][a]`, making a full automaton, so the text scan never backtracks.
- **Counting:** for each text, reset `visits` to 0, walk the automaton and add 1 to `visits[state]` at each character. Then, in **reverse BFS order** (deeper states first), add `visits[u]` into `visits[fail[u]]`. After that, `visits[s]` = number of text positions whose current suffix has the string of `s` as a suffix = number of occurrences of that string. This counts overlaps and suffix patterns (e.g. `he` inside `she`) without listing each match.
- **Output:** row per text in input order; column `i` = `visits[terminal[i]]`, so duplicates get equal counts. Empty dictionary → one empty row per text. Empty `texts` → `[]`. Empty text → a row of zeros.
- **Complexity:** construction O(L + 95·S + p) time and O(95·S + p) memory; each text O(|text| + S + p). Output O(q·(p + 1) + 1).
- **Tests:** `["a","aa","aaa","a"]` on `"aaaa"` → `[4,3,2,4]` (overlaps, suffix patterns, duplicates); a scale case with 3 860 patterns (`a`…`a`×440 plus 3 420 duplicates of `a`, total length 100 000) on 100 texts of 2 000 `a`s, which has about 10⁸ individual matches and only passes quickly because counts are aggregated.

## Own tests

All tests are in [tests/student/stress.py](tests/student/stress.py). Random cases use independent brute-force oracles: trying all subsequences (lcs), trying all merge orders (merge), trying all vertex subsets (tree), slicing comparison (search), sorting real suffix strings (suffix) and per-pattern slicing (aho). Results are checked with the supplied `public_validate.check`, so witnesses (subsequence, plan, selected set, repeat) are validated too. Every random batch is one process with mixed tasks, which checks state reset.

|Command|Input or fixture|Expected|Actual|What it checks|
|---|---|---|---|---|
|lcs|`a="", b=""`|0, ""|0, ""|both-empty base case|
|lcs|`a="aaaa", b="aa"`|2|2, "aa"|repeated characters counted once per position|
|merge|`[0,0,0,0]`|0|0, 3 merges|zero sizes, plan length n−1|
|merge|`[1,100,1]`|203|203|adjacency (cannot merge the two 1s first)|
|merge|200 × 10⁹|DP value|equal|64-bit costs, n = 200|
|tree|`n=1, w=[-7]`|0, []|0, []|empty set allowed|
|tree|star, w=[5,1,1,1]|5|5, [0]|parent decision restricts children|
|tree|chain of 100 000|completes|OK, 0.27 s|no recursion overflow|
|tree|star of 100 000, all 10⁹|99 999·10⁹|equal|large sum, `long`|
|naive|`"ab"`, `""`|[0,1,2]|[0,1,2]|final empty boundary|
|kmp|`"aabaabaab"`, `"aabaab"`|[0,3]|[0,3]|fallback after a full match|
|kmp|200 000 × `a`, pattern 1 000 × `a`|all starts|equal, 0.17 s|scale, overlaps|
|rk|`"djab"`, `"ab"`|[2]|[2]|collision ab/dj verified|
|rk|`"~~~ ~"`, `"~ ~"`|[2]|[2]|negative remainder normalization|
|suffix|`"aaaa"`|sa [3,2,1,0], lcp [0,1,2,3], "aaa"|equal|all-equal, no sentinel|
|suffix|`"abcd"`|sa [0,1,2,3], lcp all 0, ""|equal|all distinct, no repeat|
|suffix|50 000 × `a`|sa descending, lcp 0..n−1|equal, 0.44 s|scale, worst-case doubling|
|aho|`["a","aa","aaa","a"]` on `["aaaa","","b"]`|[[4,3,2,4],[0,0,0,0],[0,0,0,0]]|equal|overlaps, duplicates, empty text|
|aho|`[]` on `["abc",""]`|[[],[]]|[[],[]]|empty dictionary keeps one row per text|
|aho|dense 3 860 patterns × 100 texts|closed-form counts|equal, 0.23 s|aggregation instead of per-match listing|
|all|150 random rounds, seed 20261106|oracle|1215/1215 pass|general correctness + state reset|
|all|300 random rounds, seed 1|oracle|2415/2415 pass|same, second seed|

The scale times above are measured from process start to result (including JVM start-up and JSON).

## Search comparison

- **Generator:** `Java/bench/SearchBench.java`, `java.util.Random` seed 20261106.
- **Random case:** text of uniform letters `a–z`; pattern is a substring copied from a random position (so at least one match).
- **Repetitive case:** text `a`×n, pattern `a`×(m−1) + `b`. This is the naive worst case: every window matches m−1 characters, then fails. For RK, every text window differs from the pattern only in the last character (by 1 in ASCII), so its hash differs by exactly 1 mod 101: c = 0 candidates.
- **Sizes:** (n, m) = (1000, 100), (2000, 200), (3000, 300), all inside the common domain (n, m ≤ 3000 and n·m ≤ 2·10⁷).
- **Correctness first:** naive, KMP and RK outputs were compared (`List.equals`) on every input before timing; all identical.
- **Timing scope:** only the `Algorithms.naive/kmp/rk` call on a ready request map (no JSON parsing, no printing, no compilation), measured with `System.nanoTime`. The same boundary is used for all three methods.
- **Warm-up/JIT:** 300 untimed calls per method and input before 5 timed calls; one JVM run (OpenJDK 21.0.12.1, `-Xmx384m`, i7-13620H, Windows 11). Times are in microseconds (µs). Raw output: [tests/student/search_bench_output.txt](tests/student/search_bench_output.txt).

|Case|n|m|Seed/generator|Algorithm|Run 1|Run 2|Run 3|Run 4|Run 5|Median (µs)|Correct outputs checked|
|---|---:|---:|---|---|---:|---:|---:|---:|---:|---:|---|
|random|1000|100|20261106 / SearchBench|naive|10.8|10.9|10.3|10.3|10.3|10.3|yes (1 match)|
|random|1000|100|20261106 / SearchBench|kmp|12.4|11.7|11.9|11.6|11.8|11.8|yes|
|random|1000|100|20261106 / SearchBench|rk|39.8|26.7|27.5|29.1|35.1|29.1|yes|
|random|2000|200|20261106 / SearchBench|naive|62.6|20.8|20.6|20.7|59.9|20.8|yes (1 match)|
|random|2000|200|20261106 / SearchBench|kmp|26.4|26.0|76.0|26.3|25.8|26.3|yes|
|random|2000|200|20261106 / SearchBench|rk|38.6|37.6|115.6|39.0|37.5|38.6|yes|
|random|3000|300|20261106 / SearchBench|naive|4.6|4.6|4.3|4.1|4.1|4.3|yes (1 match)|
|random|3000|300|20261106 / SearchBench|kmp|38.4|37.0|72.0|37.3|36.8|37.3|yes|
|random|3000|300|20261106 / SearchBench|rk|34.9|46.9|32.3|32.1|32.1|32.3|yes|
|repetitive|1000|100|a^n / a^(m−1)b|naive|87.9|87.3|87.2|87.1|87.9|87.3|yes (0 matches)|
|repetitive|1000|100|a^n / a^(m−1)b|kmp|2.9|2.8|2.7|3.0|2.7|2.8|yes|
|repetitive|1000|100|a^n / a^(m−1)b|rk|9.1|8.6|8.6|8.6|8.7|8.6|yes|
|repetitive|2000|200|a^n / a^(m−1)b|naive|295.6|333.5|293.9|299.6|290.7|295.6|yes (0 matches)|
|repetitive|2000|200|a^n / a^(m−1)b|kmp|6.5|6.1|6.0|6.2|5.9|6.1|yes|
|repetitive|2000|200|a^n / a^(m−1)b|rk|19.6|18.5|18.4|18.6|18.4|18.5|yes|
|repetitive|3000|300|a^n / a^(m−1)b|naive|843.1|831.4|813.1|791.5|940.2|831.4|yes (0 matches)|
|repetitive|3000|300|a^n / a^(m−1)b|kmp|7.7|7.8|7.5|7.5|7.8|7.7|yes|
|repetitive|3000|300|a^n / a^(m−1)b|rk|29.0|29.0|29.1|28.9|29.0|29.0|yes|

**Observations.**
- On **random** text, naive is as fast as or faster than KMP and RK. Most windows fail on the first character, so naive does about n comparisons with a very simple loop. KMP has the same O(n) behaviour plus the prefix-function cost. RK pays a multiply and two modulo operations per window plus about n/101 candidate verifications. The very low naive median at n = 3000 (4.3 µs) is lower than at n = 2000. That is probably JIT compilation state (the method was fully optimized by then), not an algorithmic effect, and it shows why medians from one machine are not a proof of complexity.
- On **repetitive** text, naive grows roughly with n·m: 87 → 296 → 831 µs while n·m grows 1 → 4 → 9 (×10⁵). KMP and RK stay roughly linear in n.
- Some single runs are outliers (e.g. 115.6 µs for RK at n = 2000), probably from OS scheduling or GC. The median reduces their effect.
- **Modulus 101:** with only 101 hash values, about 1 % of random windows are false candidates, and adversarial inputs can make every window a candidate, giving O(n·m) time. This repetitive case happens to give c = 0, but a text built from repeated `dj` blocks searched for a pattern built from `ab` blocks of the same length makes many windows equal-hash candidates that all need full character checks. Production implementations use a large (often 61-bit or double) modulus or randomized bases, where collisions are rare. Results with modulus 101 do not describe their behaviour; it is a deliberately small modulus so collisions are easy to test. The ab/dj collision is tested for correctness above.

## Sources and AI assistance

- Course materials: assignment text, `docs/LECTURE_CLARIFICATIONS_EN_RU.md`, lectures 8–10 (DP patterns, KMP/hashing, suffix arrays/Kasai/Aho–Corasick).
- **AI assistance:** Ai was used for brainstorming, debugging and small assistance in tasks.

