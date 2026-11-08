# Assignment 4 — analysis

## Setup

Language: Java, using only the standard library. The code is compiled with `--release 17`.

Local environment: OpenJDK 25.0.1 (build 25.0.1+8-27), Python 3.14.0, Windows 11 (10.0.26200), Intel Core i3-1115G4, 2 cores / 4 logical processors, about 7.65 GiB RAM.

Commands from the repository root, with JDK 17+ on `PATH`:

```sh
python tools/run.py --setup
python tools/run.py --check
python tests/student/run_tests.py
python tools/run.py --input examples/lcs.jsonl
```

The launcher builds the Java sources and runs `java -Xmx384m -cp Java/build Main`. Each input line is one JSON request and each output line is one JSON result. All task data is created again for each request. Costs, weights and Aho counts use `long`; positions and lengths use `int`.

## Algorithms

### LCS

`dp[i][j]` stores the LCS length for the first `i` characters of `a` and first `j` characters of `b`. The empty-prefix row and column contain zero.

```text
if a[i-1] == b[j-1]: dp[i][j] = dp[i-1][j-1] + 1
otherwise:           dp[i][j] = max(dp[i-1][j], dp[i][j-1])
```

The table is filled row by row, so the smaller states are ready. Equal last characters can extend an optimal shorter subsequence. If they differ, at least one last character must be left out. Taking the better of these two choices gives the optimum.

Reconstruction starts at the bottom-right cell. Equal characters are saved and both indices decrease. Otherwise, the larger neighbouring value is followed; a tie decreases `i`. The saved characters are reversed at the end. This keeps their order in both strings and gives the length stored in the table. Either empty input returns length zero and an empty sequence.

### Ordered merging

`dp[l][r]` is the minimum cost to merge the original blocks from `l` through `r`. A single block costs zero. Prefix sums give the size of an interval in constant time.

```text
dp[l][r] = min(dp[l][k] + dp[k+1][r] + prefix[r+1] - prefix[l])
           for l <= k < r
```

Intervals are processed from shorter to longer. Every final merge has some split `k` into two adjacent intervals. Trying all splits and using the best child solutions therefore finds the minimum cost.

The chosen split is saved. On equal costs, the first (smallest) split is kept. Reconstruction outputs both child plans before their parent merge, so the required blocks exist at that point. There are exactly `n-1` operations. Empty and single-block inputs need no merges. Reconstruction uses recursion, but its depth is at most 200 under the input limit. Prefix sums and costs are `long` to avoid 32-bit overflow.

### Tree independent set

The tree is rooted at vertex 0. An iterative traversal records parents and a parent-before-child order. Processing that order backwards makes the children ready before their parent, without recursive calls on a long chain.

`take[v]` is the best subtree weight when `v` is selected; `skip[v]` is the best when it is excluded. Initially they are `weights[v]` and zero. For every child `u`:

```text
take[v] += skip[u]
skip[v] += max(take[u], skip[u])
```

Selecting a vertex excludes its children. Otherwise, each child can use its better state. Child subtrees have no edges between them, so their results can be added independently. This proves the recurrence from the leaves upwards. The answer is `max(take[0], skip[0])`; changing the root does not change the allowed sets.

For reconstruction, visit parents before children. Select `v` only if its parent is not selected and `take[v] > skip[v]`; ties use `skip`. This follows the DP choices and avoids adjacent or repeated vertices. The empty tree and all-negative weights return zero and an empty set.

### Naive search

Try every start from `0` to `n-m`, including the last one, where `n` is text length and `m` is pattern length. Compare characters until a mismatch or the end of the pattern. The compared prefix always matches; a position is reported only when the whole pattern matches. Moving the start by one includes overlaps and returns positions in increasing order.

An empty pattern matches all `n+1` boundaries. If the pattern is longer than the text, the loop has no valid start and returns an empty list.

### KMP

`pi[i]` is the length of the longest proper prefix of `pattern[0..i]` that is also its suffix. It is built left to right, starting with `pi[0] = 0`. On a mismatch, try the shorter border `pi[matched-1]`; on a match, extend by one. These are the remaining possible borders, so fallback does not lose a valid match.

During text scanning, `matched` is the length of a pattern prefix matching the end of the text seen so far. The same fallback rule preserves this condition. A full match is reported at `i-m+1`, then `matched` becomes `pi[m-1]` to keep overlaps. The text index never moves backwards. Extensions increase the matched length by one, and fallbacks decrease it, giving a linear total number of steps.

Empty patterns return all boundaries before any pattern indexing. Patterns longer than the text return an empty list.

### Rabin–Karp

The hash uses ASCII codes, base 31 and modulus 101. The leftmost character has the highest power. First calculate the pattern hash, the first window hash and `power = 31^(m-1) mod 101`. Then roll the window:

```text
outgoing = text[start] * power mod 101
h = (h - outgoing + 101) mod 101
h = (31*h + text[start+m]) mod 101
```

Removing the first term, shifting the remaining powers and adding the new character gives the next window hash. Adding 101 prevents a negative remainder because both subtracted values are already between 0 and 100.

Equal hashes are checked character by character before reporting a match. This rejects collisions while keeping every real occurrence. For example, `ab` and `dj` both hash to 75, but only `ab` should match pattern `ab`. Every window is considered, including overlaps. Empty patterns and patterns longer than the text are handled before forming a window.

For `1 <= m <= n`, time is `O(n+m+c*m)`, where `c` is the number of equal-hash windows. It can be quadratic when many windows need full verification.

### Suffix array and LCP

The suffix array stores starts `0..n-1`, without an empty suffix. Initial character ranks are `1..95`. Rank zero is used only for a missing second half of a suffix.

At width `w`, sort pairs `(rank[i], rank[i+w])`. Two stable counting-sort passes handle the second rank and then the first rank. Equal pairs receive the same new rank. If old ranks correctly describe prefixes of length `w`, the pairs describe prefixes of length `2w`. Doubling `w` therefore gives the correct suffix order. The algorithm stops when all ranks are different or the full lengths are covered. It stores rank arrays, not copies of all suffix strings.

Kasai builds the inverse suffix positions and scans starts in text order. Each suffix is compared with its sorted predecessor. A known common prefix of length `h` leaves at least `h-1` reusable characters at the next start, unless that suffix is first in sorted order. This avoids repeating most comparisons. The match length decreases by at most one per step, so its total increases are also linear. `lcp[0]` is zero.

Suffixes sharing a prefix are consecutive in sorted order. Therefore the maximum adjacent LCP gives the longest repeated substring. Overlaps count. Equal best lengths keep the first found during the text-order scan. Empty text returns empty arrays and an empty repeat; one character gives `[0]`, `[0]` and an empty repeat.

### Aho–Corasick

Build one trie for all patterns. Each state has 95 transitions for printable ASCII. `terminal[i]` stores the end state of dictionary entry `i`; duplicate patterns share this state but keep separate output columns.

A failure link points to the longest proper suffix that is also a trie prefix. Root children link to root. Breadth-first processing sets a child's failure link using the parent's fallback transition, and fills missing transitions from the same fallback state. These shorter states have already been processed.

While reading a text, the current state is the longest suffix seen so far that is also a trie prefix. Count a visit after each character. Then process states in reverse breadth-first order and add each visit count to its failure parent. Matches for suffix patterns are on this chain, so this counts them as well. Shorter states receive all contributions before they are propagated further. No list of individual matches is needed.

The output reads terminal counts in dictionary order. State and visits reset for each text, so repeated texts give the same result. Empty texts give zero counts; an empty dictionary keeps one empty row per text; no texts gives no rows.

## Complexity

These bounds exclude JSON parsing. Auxiliary memory means working storage, separate from the returned output. For two-string tasks, `n,m` are string lengths; for merge/tree/suffix, `n` is the number of blocks, vertices or characters. `z` is the number of search matches, `ell` the LCS length and `s` the number of selected vertices.

| Task | Time | Auxiliary memory | Output memory |
|---|---|---|---|
| LCS | `O((n+1)(m+1))` | `O((n+1)(m+1))` | `O(ell+1)` |
| Merge | `O(n^3)` for n>0 | `O(n^2)` | `O(n+1)` |
| Tree | `O(n+1)` | `O(n+1)` | `O(s+1)` |
| Naive | `O(n*m+n+1)` | `O(1)` | `O(z+1)` |
| KMP | `O(n+m+z+1)` | `O(m+1)` | `O(z+1)` |
| RK | `O(n*m+n+m+1)`; candidate bound above | `O(1)` | `O(z+1)` |
| Suffix + Kasai | `O(n log(n+1))` for n>0; Kasai `O(n)` | `O(n+1)` | `O(n+1)` |
| Aho | Build `O(L+95*S+p)`; each text `O(length+S+p)` | `O(95*S+p)` | `O(q*(p+1)+1)` |

For Aho, `L` is total pattern length including duplicates, `S` is trie states including root, `p` is pattern count and `q` is text count. The full transition arrays and reverse visit propagation give these bounds even with dense matches. Suffix counting sort costs `O(n+classes)` per round, with at most `max(n,95)` classes, giving `O(n log(n+1))` overall.

LCS with either string empty, empty merge and empty suffix inputs take constant algorithm time and space. Input storage is `O(n+m+1)` for two-string tasks, `O(n+1)` for merge/tree/suffix, and `O(L+T+p+q+1)` for Aho, where `T` is total text length.

## Tests

Recorded runs on 8 November 2026 passed setup, all 44 public cases and the repeated 88-request public batch. The student runner passed **1271 checks**: 37 named cases, 860 small oracle cases, 351 mixed/repeated requests and 23 large cases. Each Java batch has a 384 MiB heap and a 20-second timeout.

The small checks use separate simple solutions: enumerate subsequences for LCS, adjacent merge choices for merge and vertex subsets for tree. Search/Aho compare substrings directly; suffix checks sort small suffix strings. The generator seed is `20261108`. Witnesses are checked for validity and optimum value, so different optimal answers are accepted.

Large tests include a 1000-by-1000 LCS table, 200 blocks of size 10^9, 100,000-vertex chains and stars, search limits, 50,000-character suffix inputs, 100,001 Aho states and one million output counts. They check correct answers as well as completion. These local runs are not the official grading environment.

Below are some of the recorded cases. Exact inputs and all remaining cases are in [cases.json](tests/student/cases.json); full actual results and timings are in [results.json](tests/student/results.json). For LCS, merge and tree, the expected value is the optimum and the returned witness is checked separately.

| Input fixture | Expected | Actual | What it checks |
|---|---|---|---|
| `lcs-both-empty` | `{"length":0}` | `{"length":0,"sequence":""}` | Empty-prefix indexing |
| `lcs-noncontiguous` | `{"length":4}` | `{"length":4,"sequence":"abcd"}` | Confusing substring with subsequence |
| `lcs-multiple-optima` | `{"length":3}` | `{"length":3,"sequence":"ABA"}` | Rejecting an alternative optimal witness |
| `merge-adjacency` | `{"cost":203}` | `{"plan":[[1,1,2],[0,0,2]],"cost":203}` | Merging the two nonadjacent small blocks |
| `merge-zero-ties` | `{"cost":0}` | `{"plan":[[2,2,3],[1,1,3],[0,0,3]],"cost":0}` | Missing chronological child merges when values tie |
| `merge-long-total` | `{"cost":8000000000}` | `{"plan":[[0,0,1],[2,2,3],[0,1,3]],"cost":8000000000}` | 32-bit overflow |
| `tree-negative` | `{"weight":0}` | `{"selected":[],"weight":0}` | Forcing a negative vertex into the set |
| `tree-long-total` | `{"weight":3000000000}` | `{"selected":[0,2,4],"weight":3000000000}` | 32-bit total or adjacent reconstructed vertices |
| `naive-overlap` | `{"positions":[0,1,2,3]}` | `{"positions":[0,1,2,3]}` | Skipping overlapping starts |
| `kmp-empty` | `{"positions":[0,1,2,3]}` | `{"positions":[0,1,2,3]}` | Pattern indexing before empty-pattern handling |
| `kmp-overlap` | `{"positions":[0,2,4]}` | `{"positions":[0,2,4]}` | Resetting the matched prefix after a full match |
| `rk-collision-rejection` | `{"positions":[0,4]}` | `{"positions":[0,4]}` | Reporting dj because its hash equals ab |
| `rk-negative-remainder` | `{"positions":[1,4]}` | `{"positions":[1,4]}` | Failing to normalize subtraction in a rolling update |
| `suffix-empty` | `{"sa":[],"lcp":[],"repeated":""}` | `{"sa":[],"repeated":"","lcp":[]}` | Emitting an empty suffix |
| `suffix-overlap` | `{"sa":[4,3,2,1,0],"lcp":[0,1,2,3,4],"repeated":"aaaa"}` | `{"sa":[4,3,2,1,0],"repeated":"aaaa","lcp":[0,1,2,3,4]}` | Forbidding overlapping repeated substrings |
| `aho-empty-dictionary` | `{"counts":[[],[],[]]}` | `{"counts":[[],[],[]]}` | Dropping empty output rows |
| `aho-suffix-duplicates-reset` | `{"counts":[[2,2,2,2],[0,0,0,0],[2,2,2,2]]}` | `{"counts":[[2,2,2,2],[0,0,0,0],[2,2,2,2]]}` | Missing failure-suffix matches, duplicate columns or reset |

## Search comparison

After building Java, run:

```sh
javac --release 17 -encoding UTF-8 -cp Java/build -d Java/build tests/student/SearchBenchmark.java
java -Xmx384m -Xbatch -cp Java/build SearchBenchmark
```

The measurements below were recorded on the environment listed above on 8 November 2026. [SearchBenchmark.java](tests/student/SearchBenchmark.java) uses the same text/pattern pairs for all three methods:

- Random: uniform `A/C/G/T` text from `java.util.Random(20261108 + n)`. The pattern is the `m` characters starting at `n/3`.
- Repetitive: `n` copies of `a`, with a pattern of `m` copies of `a`.

Before timing, every output is checked against `regionMatches` at every valid start. All six input pairs passed for all three methods. This built-in method is only used in the benchmark oracle, not in the search implementations.

Each method/input gets 2000 untimed warm-up calls. Then three runs each measure 1000 calls, rotating the method order between runs. Times are **microseconds per search**. The timed part includes search preprocessing, result allocation and saving the result to a volatile variable. It excludes input generation, correctness checks, compilation, JVM startup, JSON and file I/O. JIT and garbage collection are enabled; `-Xbatch` makes compilation synchronous. Garbage collection is not forced.

| Case | n | m | Seed | Method | Run 1 | Run 2 | Run 3 | Median | Matches |
|---|---:|---:|---:|---|---:|---:|---:|---:|---:|
| random | 500 | 25 | 20261608 | naive | 1.838 | 1.630 | 1.720 | 1.720 | 1 |
| random | 500 | 25 | 20261608 | kmp | 3.091 | 2.923 | 2.849 | 2.923 | 1 |
| random | 500 | 25 | 20261608 | rk | 6.606 | 6.472 | 7.571 | 6.606 | 1 |
| random | 1500 | 75 | 20262608 | naive | 3.456 | 3.086 | 3.307 | 3.307 | 1 |
| random | 1500 | 75 | 20262608 | kmp | 6.757 | 9.347 | 6.829 | 6.829 | 1 |
| random | 1500 | 75 | 20262608 | rk | 17.512 | 16.180 | 14.638 | 16.180 | 1 |
| random | 3000 | 150 | 20264108 | naive | 6.552 | 8.035 | 7.849 | 7.849 | 1 |
| random | 3000 | 150 | 20264108 | kmp | 14.337 | 12.158 | 14.256 | 14.256 | 1 |
| random | 3000 | 150 | 20264108 | rk | 31.929 | 31.701 | 29.462 | 31.701 | 1 |
| repetitive | 500 | 25 | - | naive | 31.620 | 30.790 | 33.659 | 31.620 | 476 |
| repetitive | 500 | 25 | - | kmp | 19.062 | 5.089 | 5.308 | 5.308 | 476 |
| repetitive | 500 | 25 | - | rk | 47.081 | 49.009 | 66.209 | 49.009 | 476 |
| repetitive | 1500 | 75 | - | naive | 235.311 | 265.069 | 277.248 | 265.069 | 1426 |
| repetitive | 1500 | 75 | - | kmp | 23.818 | 30.402 | 23.110 | 23.818 | 1426 |
| repetitive | 1500 | 75 | - | rk | 614.070 | 740.104 | 567.120 | 614.070 | 1426 |
| repetitive | 3000 | 150 | - | naive | 822.479 | 992.881 | 1717.581 | 992.881 | 2851 |
| repetitive | 3000 | 150 | - | kmp | 50.693 | 50.815 | 1549.963 | 50.815 | 2851 |
| repetitive | 3000 | 150 | - | rk | 2218.971 | 2002.920 | 1941.890 | 2002.920 | 2851 |

The same raw data is saved in [benchmark.csv](tests/student/benchmark.csv) and [benchmark.md](tests/student/benchmark.md).

Naive search was fastest on these random inputs because most starts fail after a few comparisons. On repeated characters, KMP was much faster: at n=3000 its median was 50.815 microseconds, versus 992.881 for naive and 2002.920 for RK. Naive and exact RK check the same long match at many starts, while KMP reuses prefix information. All methods still have to return 2851 positions.

Modulus 101 makes accidental collisions fairly easy to produce. RK must verify those windows. On the repetitive inputs, every candidate really matches, so verification is expensive even without collisions. This small-modulus experiment does not describe every production hash choice.

Timings do not prove complexity. There is visible variation, including a slow final KMP run on repetitive text. JIT, garbage collection and other work on the computer can affect a run. All three values are kept and the median is reported.

## Sources

- [Specification](docs/Assignment_4_EN.md) and [contracts](docs/guide/CONTRACTS.md): required methods, input limits and output rules.
- [Algorithm notes](docs/LECTURE_CLARIFICATIONS_EN_RU.md): merge costs, hash collisions, suffix indexing and Aho counting details.
- [Testing guide](docs/guide/TESTING.md) and [rubric](docs/guide/RUBRIC.md): cases and limits to check.
- The template provides `Main.java`, `Json.java`, the launcher and public validator. The verification commands and recorded results are listed above.
