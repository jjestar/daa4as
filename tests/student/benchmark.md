# Search benchmark measurements

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
