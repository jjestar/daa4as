# Assignment 4 — analysis

## Environment and commands

The selected language is **Java**, as recorded in `submission.json`. The implementation targets Java 17 bytecode (`javac --release 17`) and uses only the standard library. Measurements were made with OpenJDK **25.0.1**, build **25.0.1+8-27**, and Python **3.14.0** for the launcher and test scripts. The host runs Windows 11, version **10.0.26200**, with an Intel Core i3-1115G4 processor (2 cores, 4 logical processors) and **8,215,179,264 bytes** of RAM (approximately 7.65 GiB).

Run these commands from the repository root with the selected JDK's `bin` directory on `PATH`:

```sh
java -version
javac -version
python --version
python tools/run.py --language Java --setup
python tools/run.py --language Java --check
python tools/run.py --language Java --input examples/lcs.jsonl
python tests/student/run_tests.py
```

The launcher compiles every `Java/source/*.java` file into `Java/build`, then starts `java -Xmx384m -cp Java/build Main`. To send a longer JSON Lines batch after compilation, use the same Java command and provide requests on standard input. The original launcher, JSON parser and JSON Lines protocol are retained. `Algorithms` dispatches to `DynamicProgramming`, `StringSearch` and `TextIndex`.

Each request owns its arrays, tables, automaton and result collections. Only fixed constants are shared between requests. This permits mixed and repeated requests in one process without retaining a previous result. Output contains one JSON object per request and no diagnostic messages. Character positions are zero-based; printable ASCII characters are compared directly and case-sensitively. Costs, weights and dictionary counts use `long`; indices and DP lengths use `int`.

All complexity bounds below describe the algorithm **after parsing**. Parsed input and returned output are listed separately from auxiliary working storage. JSON parsing and serialization also take space and time proportional to the transported input/output and are included only where explicitly stated in measurements.

## Algorithm explanations

### 1. Longest common subsequence: `lcs`

Let `n = a.length()` and `m = b.length()`. The state `dp[i][j]` is the longest common subsequence length for the half-open prefixes `a[0..i)` and `b[0..j)`. Row zero and column zero are zero because an empty sequence has no nonempty common subsequence. If either complete string is empty, the method returns `{length: 0, sequence: ""}` before allocating the table.

For positive `i,j`, matching last characters give `dp[i][j] = dp[i-1][j-1] + 1`. Otherwise `dp[i][j] = max(dp[i-1][j], dp[i][j-1])`. Rows are filled from top to bottom and columns from left to right, so all three dependencies are ready. If the last characters differ, a common subsequence cannot use both as its final match and must omit at least one. If they agree, an optimal solution can use that last pair, extending an optimum for the smaller prefixes. These observations and induction on prefix sizes prove the recurrence.

Reconstruction begins at `(n,m)`. Equal characters are appended while moving diagonally. On a mismatch, move to a neighbouring state with the same optimal value. Equal alternatives prefer decreasing `i`. The collected characters are reversed at the end, so their positions increase in both input strings. Each diagonal step reduces the optimal value by one; the returned witness therefore has exactly `dp[n][m]` characters. Ties need not produce the lexicographically smallest LCS.

For nonempty strings, time is `O(nm+n+m)` including reconstruction and auxiliary memory is `O(nm)` for the table and temporary reconstruction buffer. A bound covering all sizes is `O((n+1)(m+1))` time and auxiliary space. An empty input takes `O(1)` algorithm time/space. Input storage is `O(n+m+1)` and output storage is `O(ell+1)`, where `ell <= min(n,m)` is the answer length.

Counterexample: `a="abc", b="ac"` must return length 2 and sequence `"ac"`; a longest-common-substring implementation would fail. `a="ab", b="ba"` has two optimal witnesses; this implementation returns `"a"`.

### 2. Ordered adjacent-block merging: `merge`

For a closed interval `[l,r]`, `dp[l][r]` is the minimum cost of merging exactly those original blocks into one. A single block costs zero. Prefix sums give `sum(l,r) = prefix[r+1] - prefix[l]` in constant time. Both the prefix sums and DP costs are `long`.

The last operation must combine adjacent intervals `[l,k]` and `[k+1,r]` for some `l <= k < r`. Thus

```text
dp[l][r] = min over k of dp[l][k] + dp[k+1][r] + sum(l,r).
```

Intervals are processed by increasing length, so both child intervals have already been solved. Every legal plan has such a last split, giving a lower bound described by the recurrence. Combining optimal child plans with their final merge attains that bound. Induction on interval length proves optimality. This is an interval-partition cost, not the matrix-chain multiplication cost and not an unrestricted smallest-block greedy rule.

`split[l][r]` stores the best `k`; strict improvement retains the smallest split in a tie. Reconstruction recursively emits the left child's merges, then the right child's merges, then `[l,k,r]`. Consequently, both operands exist as adjacent current blocks when their parent is emitted. Each internal node contributes one operation, giving exactly `n-1` operations for `n>1`. The reconstruction depth is at most the published bound `n <= 200`. Empty and singleton inputs return cost zero and an empty plan.

There are `O(n^2)` states and `O(n)` candidate splits per state: `O(n^3)` time and `O(n^2)` auxiliary space for nonempty inputs. Reconstruction costs `O(n)` time and stack space, already within those bounds. Empty input takes `O(1)`. Parsed sizes occupy `O(n+1)` space and the plan occupies `O(n+1)` output space. Even the loose upper bound `(n-1) * sum(sizes) <= 39,800,000,000,000` fits in `long` but can exceed a 32-bit integer.

Counterexample: sizes `[1,100,1]` have cost 203, with plan `[[1,1,2],[0,0,2]]` under the tie rule. The two size-one blocks are not adjacent and cannot be merged first. For `[0,0,0]`, a zero-cost plan must still contain two chronological operations.

### 3. Maximum-weight independent set on a tree: `tree`

Root the tree at vertex zero. The input is guaranteed to be a connected tree when `n>0`. Degree counts build compact `int[]` neighbour lists. An array used as a queue records a parent-before-child order without recursion. Its reverse is a child-before-parent order suitable for DP, including on a chain of 100,000 vertices.

`take[v]` is the best weight in the subtree of `v` conditional on selecting `v`. `skip[v]` is the best weight conditional on excluding it. Initially `take[v] = weights[v]` and `skip[v] = 0`. Once a child `u` is complete, accumulate

```text
take[v] += skip[u]
skip[v] += max(take[u], skip[u]).
```

For a leaf these initial values are the two possible choices. A selected parent forces every child to be skipped. An excluded parent permits either choice at each child. The disjoint child subtrees have no edges between them, so their independently optimal permitted choices can be added. Induction in the reverse order proves both states; the answer is `max(take[0],skip[0])`. Rooting organizes the computation without changing the undirected feasible sets or the optimum.

Reconstruction uses the forward order. A vertex is selected exactly when its parent is not selected and `take[v] > skip[v]`. Ties choose exclusion. Each vertex is considered once; this prevents duplicates and adjacent selections while attaining the state value chosen by its parent. The empty tree returns zero and an empty list. Negative weights never force selection, so an all-negative tree also returns the empty set.

Each edge is traversed a constant number of times: `O(n+1)` time and auxiliary space, including reconstruction. The adjacency arrays have `2(n-1)` entries and all other arrays are linear. Parsed weights/edges occupy `O(n+1)` space. The selected list uses `O(s+1)` output space for `s <= n` vertices. All accumulated weights are `long`; total positive weight can reach `100,000,000,000,000`.

Counterexample: a three-vertex path with weights `[4,9,6]` selects vertices `[0,2]` for weight 10, exceeding the single largest vertex. A singleton of weight `-5` returns zero and no vertices. Deep-chain tests detect recursive traversal failures.

### 4. Exact baseline search: `naive`

For every start in the inclusive interval `0..n-m`, compare `text[start+j]` to `pattern[j]` for `j=0..m-1`, stopping at the first mismatch. The invariant is that the first `matched` characters of this window agree. A start is reported exactly when all `m` characters agree. Incrementing the start by one rather than by the pattern length preserves overlaps; visiting starts in increasing order sorts the result automatically.

When `m=0`, every attempted comparison loop is empty and all `n+1` boundaries are returned, including boundary `n`. When `m>n`, the outer loop performs no iterations. No built-in search routine is used.

For `1 <= m <= n`, time is `O((n-m+1)m + z+1)` for `z` matches; `O(nm+n+1)` covers all inputs. Auxiliary space is `O(1)` and output space is `O(z+1)`. Parsed text/pattern storage is `O(n+m+1)`.

Counterexample: `text="aaaaa", pattern="aaa"` returns `[0,1,2]`, testing overlaps and the last possible start. `text="ab", pattern=""` returns `[0,1,2]`, testing the final empty-pattern boundary.

### 5. Knuth–Morris–Pratt search: `kmp`

`pi[i]` is the length of the longest proper prefix of `pattern[0..i]` that is also a suffix. The first entry is zero. Compute entries left to right, starting from `pi[i-1]`: on mismatch, repeatedly replace `matched` by `pi[matched-1]`; on agreement, extend by one. The border lengths in this chain enumerate the shorter possible prefix/suffix matches, so discarding the longer mismatching candidate cannot discard a valid border.

During text scanning, `matched` is the length of a pattern prefix matching the suffix of the text processed so far. The same fallback rule maintains that invariant without moving the text cursor backward. At `matched == m`, report `i-m+1`, then fall back to `pi[m-1]`. This keeps a proper matched border for overlapping occurrences. Every full match is discovered at its ending character, and only full matches are reported.

An empty pattern returns all boundaries before pattern indexing. A longer-than-text pattern returns an empty list before building `pi`. The invariant also covers empty text and partial matches at its end.

In each pass, successful extensions increase the matched length by at most one per character; fallback strictly decreases it. The total number of fallback steps is therefore linear. Time is `O(n+m+z+1)`, auxiliary storage is `O(m+1)` for `pi`, and output storage is `O(z+1)`. Parsed input storage is `O(n+m+1)`.

Counterexample: `text="abababa", pattern="aba"` returns `[0,2,4]`. Resetting `matched` to zero after a match would lose overlapping occurrences.

### 6. Exact Rabin–Karp search: `rk`

The hash of an `m`-character string is its ASCII-code polynomial with base 31 and modulus 101, with exponent `m-1` on the leftmost character. The initial pattern/window hashes and `leadingPower = 31^(m-1) mod 101` are computed iteratively. For each valid start, `windowHash` represents exactly that text window.

After checking a window, the rolling update is

```text
outgoing = ASCII(text[start]) * leadingPower mod 101
h = (h - outgoing + 101) mod 101
h = (31*h + ASCII(text[start+m])) mod 101.
```

The outgoing term and hash are already in `0..100`, so adding 101 is sufficient to normalize the subtraction in Java. Removing the leading term, multiplying by the base and appending the new character preserves the hash invariant. Arithmetic uses `long`.

Unequal hashes rule out equality. Equal hashes only trigger direct character comparisons; a position is appended only after all characters match. Every actual occurrence has equal hashes and therefore passes verification, while colliding unequal strings are rejected. Starts advance by one, retaining overlaps. Empty patterns and patterns longer than the text are handled before creating the first window.

For `1 <= m <= n`, time is `O(n+m+c*m)`, where `c` is the number of equal-hash candidate windows; this includes reporting because `z <= c`. A bound for all inputs is `O(nm+n+m+1)`. This is **not** deterministic linear worst-case search: repetitive inputs can require a full verification at nearly every position. The rolling implementation uses `O(1)` auxiliary space, `O(z+1)` output space and `O(n+m+1)` parsed input space.

Counterexample: `text="abdj", pattern="ab"` returns only `[0]`. Both `"ab"` and `"dj"` hash to 75, so trusting hash equality would incorrectly report position 2. Small-modulus collisions are intentional here; their frequency and timing do not characterize every production hash design.

### 7. Prefix-doubling suffix array and Kasai LCP: `suffix`

The suffix array contains exactly the starts `0..n-1` of nonempty suffixes. No sentinel suffix is added. Initially `rank[i] = ASCII(text[i])-31` lies in `1..95`; rank zero represents a missing second half and is smaller than every character rank.

At width `w`, ranks encode the relative order/equality of prefixes of length at most `w`. Sort each suffix by `(rank[i], rank[i+w])`, using zero if `i+w >= n`. Two stable counting passes sort the second key and then the first. Count arrays cover `0..classes`; subsequent ranks are compressed to consecutive positive classes. Adjacent equal pairs get the same new rank and unequal pairs get increasing ranks. Thus induction shows that each round correctly orders prefixes of length at most `2w`. Double the width until full suffixes are distinguished, or stop early when every suffix has its own class. A shorter suffix sorts before a longer equal-prefix suffix because of the zero missing-half rank.

No complete suffix strings are materialized for sorting. A counting pass costs `O(n+classes)`; `classes <= max(n,95)`. With the fixed alphabet this gives `O(n log(n+1))` doubling time and `O(n+1)` auxiliary memory, rather than the comparison-sort `O(n log^2(n+1))` bound.

Kasai first builds `position[sa[i]] = i`. Process suffix starts in text order. For a suffix with nonzero array position, compare it with its immediate predecessor, starting from the reused lower bound `matched`, and store the resulting length in `lcp[position[start]]`. The first suffix in sorted order has LCP zero and resets the bound. After other iterations decrease a positive match length by one. Removing the first character from two suffixes that share `h` characters leaves a shared prefix of length at least `h-1`; the next suffix's immediate predecessor shares at least that much. This proves the reused bound is safe. The bound decreases by at most one per iteration and can increase only linearly in total, giving `O(n)` Kasai time.

Any substring occurring at two starts prefixes two suffixes. All suffixes with that prefix occupy a contiguous range in sorted order, so some adjacent pair in the range shares at least that prefix. Conversely, every adjacent LCP is a repeated substring. Therefore `max(lcp)` is exactly the longest repeat length. On a strict improvement, save the current start; equal maxima retain the first one encountered during the text-order scan. The final substring is copied only once for the result. Overlapping occurrences are valid.

Empty input returns empty arrays and `""`; a singleton returns `sa=[0]`, `lcp=[0]`, and `""`. Overall time is `O(n log(n+1))` for nonempty input and `O(1)` for empty input. Auxiliary memory is `O(n+1)` for rank, scratch, count and inverse-position arrays. Input storage is `O(n+1)`; output storage is `O(n+1)` for `sa`, `lcp` and the repeated substring.

Counterexample: `"aaaa"` returns `sa=[3,2,1,0]`, `lcp=[0,1,2,3]`, `repeated="aaa"`. It tests shorter-suffix ordering, overlaps, Kasai reuse and omission of the empty suffix.

### 8. Aho–Corasick dictionary matching: `aho`

Build one trie per request. Each state has a 95-entry transition array indexed by `ASCII(character)-32`. The root is state zero. `terminal[i]` records the state of dictionary position `i`; duplicate strings share a terminal state but retain separate output columns. Patterns are guaranteed to be nonempty.

A state's failure link points to the state representing its longest proper suffix that is also a trie prefix. Root children have failure zero. Process the trie in breadth-first order. For an existing edge labelled `c` from state `v` to child `u`, set `failure[u] = next[failure[v]][c]`. For a missing edge, copy that fallback transition instead. All fallback states have smaller depth and have already been processed, so their completed transitions are ready. Root's missing transitions remain at the root. Induction on trie depth proves both the failure links and the completed automaton transitions.

For each independent text, create a fresh `long[] visits` and reset the current state to root. After consuming a character, the state represents the longest suffix of the processed text that is also a trie prefix; increment its visit count. All other dictionary matches ending there are suffixes on its failure chain. Instead of visiting every match separately, process states in reverse breadth-first order and add `visits[v]` to `visits[failure[v]]`. Failure links always decrease depth, so descendants contribute before their ancestors are propagated. Each occurrence contributes once to every applicable suffix state. Finally, read counts at the terminal states in original dictionary order. This counts overlaps, suffix patterns and duplicate columns without quadratically copying output lists.

An empty dictionary yields one empty row per text. An empty texts list yields no rows. An empty text produces a zero in every dictionary column. Repeated identical texts receive separate fresh visit arrays and equal results.

Let `L` be total pattern length including duplicates, `p` the number of patterns, `S` trie states including root, `q` the number of texts, and `T` their total length. Construction takes `O(L+95*S+p)` time. Each text takes `O(length+S+p)` time, including count-array initialization and reverse propagation. Total time is `O(L+95*S+p+T+q*(S+p))`. Auxiliary space is `O(95*S+p)` for the automaton, failure order, terminals and one visit array. Parsed input storage is `O(L+T+p+q+1)`. Returned counts occupy `O(q*(p+1)+1)` space, preserving empty rows. Dense matches do not cause a separate record or traversal for every dictionary-entry occurrence.

Counterexample: patterns `["he","she","he"]` and texts `["sheshe","sheshe",""]` return `[[2,2,2],[2,2,2],[0,0,0]]`. This simultaneously checks failure-suffix counts, duplicate columns and per-text resets.

## Own tests and observed results

Executed on 8 November 2026:

- `python tools/run.py --setup`: PASS (compilation and JSON startup).
- `python tools/run.py --check`: PASS for all 44 public cases and the repeated 88-request mixed batch.
- `python tests/student/run_tests.py`: PASS for all **1271 checks**: 37 named fixtures, 860 deterministic independent-oracle cases, 351 mixed/repeated requests and 23 scale cases.

The complete inputs, expected values and defect descriptions are in [cases.json](tests/student/cases.json). [results.json](tests/student/results.json) records actual named-fixture responses, runtime information and every batch duration. The test runner regenerates large inputs instead of storing huge fixtures. Each batch uses a 384 MiB heap and a 20-second process timeout; these local checks do not reproduce the official isolated CPU environment.

Small LCS cases enumerate subsequences; merge cases enumerate adjacent merge choices; tree cases enumerate independent vertex subsets. Search and dictionary oracles compare every substring position; small suffix cases directly sort suffix strings. Seed `20261108` fixes the generated cases and their shuffle. The provided validator checks schemas and witnesses, accepting alternative optima. Scale expectations use independent formulas for uniform/periodic strings, paths, stars, equal-size merge trees and known dictionary counts. Thus merely agreeing with another optimized implementation is insufficient to pass.

The scale suite covers a 1000-by-1000 LCS table, 200 blocks of size 10^9 (cost 1,544,000,000,000), 100,000-vertex chains and stars, the full search text/pattern/product bounds, 50,000-character suffix inputs, an Aho trie with 100,001 states, 10,000 duplicate patterns, 100 independent texts and one million returned counts. It validates answers as well as completion. The largest star returns weight 99,999,000,000,000; deep chains use no recursion. The widest trie tests all 95 printable ASCII characters.

In the table, `expected` gives the optimum for reconstruction tasks; the actual witness is additionally checked for validity and optimality. Every row passed. Its exact input is the named fixture in `cases.json`.

| Fixture / input reference | Expected | Actual | Defect detected |
|---|---|---|---|
| `lcs-both-empty` | `{"length":0}` | `{"length":0,"sequence":""}` | Empty-prefix indexing |
| `lcs-right-empty` | `{"length":0}` | `{"length":0,"sequence":""}` | Empty second dimension |
| `lcs-noncontiguous` | `{"length":4}` | `{"length":4,"sequence":"abcd"}` | Confusing substring with subsequence |
| `lcs-multiple-optima` | `{"length":3}` | `{"length":3,"sequence":"ABA"}` | Rejecting an alternative optimal witness |
| `merge-empty` | `{"cost":0}` | `{"plan":[],"cost":0}` | Empty interval table |
| `merge-single` | `{"cost":0}` | `{"plan":[],"cost":0}` | Charging for an unmerged block |
| `merge-adjacency` | `{"cost":203}` | `{"plan":[[1,1,2],[0,0,2]],"cost":203}` | Merging the two nonadjacent small blocks |
| `merge-zero-ties` | `{"cost":0}` | `{"plan":[[2,2,3],[1,1,3],[0,0,3]],"cost":0}` | Missing chronological child merges when values tie |
| `merge-long-total` | `{"cost":8000000000}` | `{"plan":[[0,0,1],[2,2,3],[0,1,3]],"cost":8000000000}` | 32-bit overflow |
| `tree-empty` | `{"weight":0}` | `{"selected":[],"weight":0}` | Root access for an empty tree |
| `tree-negative` | `{"weight":0}` | `{"selected":[],"weight":0}` | Forcing a negative vertex into the set |
| `tree-zero-ties` | `{"weight":0}` | `{"selected":[],"weight":0}` | Invalid reconstruction on ties |
| `tree-long-total` | `{"weight":3000000000}` | `{"selected":[0,2,4],"weight":3000000000}` | 32-bit total or adjacent reconstructed vertices |
| `naive-empty` | `{"positions":[0]}` | `{"positions":[0]}` | Missing the sole empty-text boundary |
| `kmp-empty` | `{"positions":[0,1,2,3]}` | `{"positions":[0,1,2,3]}` | Pattern indexing before empty-pattern handling |
| `rk-empty` | `{"positions":[0]}` | `{"positions":[0]}` | Forming a negative-length initial window |
| `naive-longer` | `{"positions":[]}` | `{"positions":[]}` | Invalid last start |
| `kmp-longer` | `{"positions":[]}` | `{"positions":[]}` | Empty-text indexing |
| `rk-longer` | `{"positions":[]}` | `{"positions":[]}` | Initial window extending beyond the text |
| `naive-overlap` | `{"positions":[0,1,2,3]}` | `{"positions":[0,1,2,3]}` | Skipping overlapping starts |
| `kmp-overlap` | `{"positions":[0,2,4]}` | `{"positions":[0,2,4]}` | Resetting the matched prefix after a full match |
| `rk-overlap` | `{"positions":[0,1,2,3]}` | `{"positions":[0,1,2,3]}` | Incorrect rolling power |
| `naive-punctuation` | `{"positions":[0,10]}` | `{"positions":[0,10]}` | Ignoring case, spaces or ASCII boundaries |
| `kmp-punctuation` | `{"positions":[0,10]}` | `{"positions":[0,10]}` | Ignoring case, spaces or ASCII boundaries |
| `rk-punctuation` | `{"positions":[0,10]}` | `{"positions":[0,10]}` | Ignoring case, spaces or ASCII boundaries |
| `rk-collision-rejection` | `{"positions":[0,4]}` | `{"positions":[0,4]}` | Reporting dj because its hash equals ab |
| `rk-negative-remainder` | `{"positions":[1,4]}` | `{"positions":[1,4]}` | Failing to normalize subtraction in a rolling update |
| `suffix-empty` | `{"sa":[],"lcp":[],"repeated":""}` | `{"sa":[],"repeated":"","lcp":[]}` | Emitting an empty suffix |
| `suffix-ascii-min` | `{"sa":[0],"lcp":[0],"repeated":""}` | `{"sa":[0],"repeated":"","lcp":[0]}` | Confusing a printable character with a sentinel |
| `suffix-all-distinct` | `{"sa":[2,1,3,0],"lcp":[0,0,0,0],"repeated":""}` | `{"sa":[2,1,3,0],"repeated":"","lcp":[0,0,0,0]}` | Incorrect ASCII ordering or nonzero initial LCP |
| `suffix-overlap` | `{"sa":[4,3,2,1,0],"lcp":[0,1,2,3,4],"repeated":"aaaa"}` | `{"sa":[4,3,2,1,0],"repeated":"aaaa","lcp":[0,1,2,3,4]}` | Forbidding overlapping repeated substrings |
| `suffix-tied-repeat` | `{"sa":[2,5,8,0,3,1,4,9,6,10,7],"lcp":[0,0,0,0,2,0,1,0,2,0,1],"repeated":"ab"}` | `{"sa":[2,5,8,0,3,1,4,9,6,10,7],"repeated":"ab","lcp":[0,0,0,0,2,0,1,0,2,0,1]}` | Requiring one specific longest repeated witness |
| `aho-empty-dictionary` | `{"counts":[[],[],[]]}` | `{"counts":[[],[],[]]}` | Dropping empty output rows |
| `aho-no-texts` | `{"counts":[]}` | `{"counts":[]}` | Producing a row without a text |
| `aho-suffix-duplicates-reset` | `{"counts":[[2,2,2,2],[0,0,0,0],[2,2,2,2]]}` | `{"counts":[[2,2,2,2],[0,0,0,0],[2,2,2,2]]}` | Missing failure-suffix matches, duplicate columns or reset |
| `aho-overlap-chain` | `{"counts":[[3,5,4,3]]}` | `{"counts":[[3,5,4,3]]}` | Losing nested overlapping matches |
| `aho-printable-boundaries` | `{"counts":[[3,2,2,2]]}` | `{"counts":[[3,2,2,2]]}` | Alphabet offsets at ASCII 32 and 126 |

## Reproducible search comparison

After building the implementation, execute:

```sh
javac --release 17 -encoding UTF-8 -cp Java/build -d Java/build tests/student/SearchBenchmark.java
java -Xmx384m -Xbatch -cp Java/build SearchBenchmark
```

[SearchBenchmark.java](tests/student/SearchBenchmark.java) generates identical input pairs for each method and verifies complete position lists against a separate `regionMatches`-at-every-start oracle before any timing. The algorithm implementations themselves never use built-in substring search. The known `ab`/`dj` collision and rolling-subtraction counterexample are also tested by the student fixtures. All six benchmark inputs passed output comparison. Raw measurements below were recorded on 8 November 2026; [benchmark.csv](tests/student/benchmark.csv) and [benchmark.md](tests/student/benchmark.md) contain the same data. Hardware is specified in Environment above.

Runtime: 25.0.1+8-27; VM: OpenJDK 64-Bit Server VM; OS: Windows 11 10.0 amd64.

Unit: microseconds per search, averaged over 1000 calls per measured run. Three runs; method order rotates each run. Each method/input receives 2000 untimed warm-up calls. JVM flags: -Xmx384m -Xbatch; JIT and GC are enabled; no forced GC. Each result escapes through a volatile sink. Input generation, oracle checks, compilation, JVM startup, JSON and file I/O are excluded. Search preprocessing, result allocation, search and sink assignment are included.

Random: java.util.Random(seed = 20261108 + n), uniform A/C/G/T text; pattern is text[n/3 .. n/3+m). Repetitive: text = a^n, pattern = a^m. Every method is checked against regionMatches at each valid start before timing.

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

### Observations

On the random four-character text, the naive implementation rejects most candidate starts after a few comparisons. At n=3000 its median was 7.849 microseconds, compared with 14.256 for KMP and 31.701 for exact RK. Short-input constants, prefix-table construction and modular arithmetic matter here; naive's smaller measurement does not establish a better worst-case bound.

On repetitive text, every valid window matches. As n/m increase from 500/25 to 3000/150, the naive median increases from 31.620 to 992.881 microseconds and RK from 49.009 to 2002.920. Both repeatedly verify long matching windows. KMP reuses prefix information, with medians 5.308 and 50.815 microseconds respectively, while still allocating and returning every occurrence. The output size grows from 476 to 2851 positions.

The deliberately small modulus 101 admits accidental candidate collisions on random text; RK must verify them. On the repetitive inputs every candidate is a real match, so even a collision-free fingerprint would not remove character verification in this exact implementation. These results describe this laboratory hash and these distributions, not every production hashing strategy.

Three samples do not prove an asymptotic bound. JVM compilation, garbage collection, CPU scheduling and shared-machine load can affect timings despite warm-up; for example the final repetitive KMP run is a visible outlier. The median and all raw samples are retained rather than silently dropping an inconvenient run. `-Xbatch` makes JIT compilation synchronous but does not guarantee perfect steady state. Functional test durations include startup/JSON and are intentionally kept separate from this direct-call comparison.


## Sources

- [Assignment specification](docs/Assignment_4_EN.md) and [request contracts](docs/guide/CONTRACTS.md): command schemas, permitted input domains, required algorithms, witness rules and complexity targets.
- [Algorithm clarifications](docs/LECTURE_CLARIFICATIONS_EN_RU.md): interval-merge cost, empty-pattern boundaries, exact hash verification, suffix/sentinel conventions and suffix-pattern counting.
- [Testing guide](docs/guide/TESTING.md) and [rubric](docs/guide/RUBRIC.md): boundary cases, resource limits and validation requirements.
- The repository starter supplies `Main.java`, `Json.java`, the launcher and public validator. The algorithm implementations, student tests and comparison are separate from this supplied transport/checking code. The verification commands and their observed results are documented above; benchmark measurements are empirical observations, not proofs of asymptotic complexity.
