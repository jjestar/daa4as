# Student tests

Run from the repository root with Python 3.12+ and JDK 17+ on `PATH`:

```sh
python tests/student/run_tests.py
```

The runner compiles the Java sources using the supplied launcher. After a build,
`python tests/student/run_tests.py --skip-build` reuses the class files. Every
batch has the launcher's 20-second timeout and a 384 MiB Java heap. Timings in
`results.json` include startup and JSON transport; they are functional test
durations, not algorithm benchmark measurements.

`cases.json` contains readable input, expected values and a description of the
defect each case catches. For LCS, merge and tree, expected optimum values are
specified instead of one arbitrary witness. The supplied contract validator
also verifies the actual subsequence, chronological adjacent merge plan or
independent vertex set. It accepts every valid optimum. Suffix arrays and LCP
are checked exactly, while any maximum-length repeated substring is accepted.

The randomized cases use Python's `random.Random(20261108)` and independent
small-input oracles:

- LCS: enumerate subsequences and test membership in the other string.
- Ordered merge: enumerate the next pair of adjacent current blocks.
- Tree: enumerate vertex subsets and reject adjacent selections.
- Search and Aho: compare slices at every possible starting position.
- Suffix: directly sort small suffix strings and compare adjacent characters.

The runner shuffles these requests, then repeats mixed batches in one process
to check that state cannot leak between commands. The scale cases are generated
in memory. Their optima have direct formulas: reversed character blocks for
LCS, minimum leaf depths for equal merge weights, chains/stars for tree, and
uniform or periodic strings for search and suffix. Aho checks a 100001-state
trie, 10000 duplicate patterns and one million output counts, dense suffix
matches, all 95 printable ASCII characters and empty-dictionary output rows.

`results.json` is written only after every check passes. It records the actual
named-fixture answers, environment, batch durations and case totals. Large
inputs and outputs are deliberately regenerated instead of checked in.

## Search comparison

After building the implementation, run:

```sh
javac --release 17 -encoding UTF-8 -cp Java/build -d Java/build tests/student/SearchBenchmark.java
java -Xmx384m -Xbatch -cp Java/build SearchBenchmark
```

This writes `benchmark.csv` and `benchmark.md`. It checks all three methods
against an independent oracle, warms up each input/method, and records three
measurements of 1000 calls at each of three sizes for random and repetitive
inputs. See the generated report and `ANALYSIS.md` for the exact timing scope,
seed, runtime and interpretation. Rerunning either command overwrites only its
own generated report files.
