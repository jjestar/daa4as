import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Direct-call comparison: compilation, JSON and console I/O are outside timing. */
public final class SearchBenchmark {
    private static final long SEED = 20261108L;
    private static final int REPETITIONS = 1000;
    private static final int WARMUP = 2000;
    private static final int RUNS = 3;
    private static final String[] METHODS = {"naive", "kmp", "rk"};
    private static volatile Object sink;

    private static List<Integer> search(int method, String text, String pattern) {
        return switch (method) {
            case 0 -> StringSearch.naive(text, pattern);
            case 1 -> StringSearch.kmp(text, pattern);
            case 2 -> StringSearch.rk(text, pattern);
            default -> throw new IllegalArgumentException("Unknown method");
        };
    }

    private static List<Integer> oracle(String text, String pattern) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i <= text.length() - pattern.length(); i++) {
            if (text.regionMatches(i, pattern, 0, pattern.length())) result.add(i);
        }
        return result;
    }

    private static String randomText(Random random, int n) {
        char[] text = new char[n];
        String alphabet = "ACGT";
        for (int i = 0; i < n; i++) text[i] = alphabet.charAt(random.nextInt(4));
        return new String(text);
    }

    public static void main(String[] args) throws IOException {
        Locale.setDefault(Locale.ROOT);
        Path directory = Path.of("tests", "student");
        StringBuilder csv = new StringBuilder(
                "case,n,m,seed,algorithm,run1_us,run2_us,run3_us,median_us,matches,repetitions\n");
        StringBuilder report = new StringBuilder("# Search benchmark measurements\n\n");
        report.append("Runtime: ").append(System.getProperty("java.runtime.version"))
                .append("; VM: ").append(System.getProperty("java.vm.name"))
                .append("; OS: ").append(System.getProperty("os.name"))
                .append(" ").append(System.getProperty("os.version"))
                .append(" ").append(System.getProperty("os.arch")).append(".\n\n")
                .append("Unit: microseconds per search, averaged over ").append(REPETITIONS)
                .append(" calls per measured run. Three runs; method order rotates each run. ")
                .append("Each method/input receives ").append(WARMUP)
                .append(" untimed warm-up calls. JVM flags: -Xmx384m -Xbatch; JIT and GC are enabled; no forced GC. ")
                .append("Each result escapes through a volatile sink. Input generation, oracle checks, ")
                .append("compilation, JVM startup, JSON and file I/O are excluded. Search preprocessing, ")
                .append("result allocation, search and sink assignment are included.\n\n")
                .append("Random: java.util.Random(seed = 20261108 + n), uniform A/C/G/T text; ")
                .append("pattern is text[n/3 .. n/3+m). Repetitive: text = a^n, pattern = a^m. ")
                .append("Every method is checked against regionMatches at each valid start before timing.\n\n")
                .append("| Case | n | m | Seed | Method | Run 1 | Run 2 | Run 3 | Median | Matches |\n")
                .append("|---|---:|---:|---:|---|---:|---:|---:|---:|---:|\n");

        for (String distribution : List.of("random", "repetitive")) {
            for (int n : new int[]{500, 1500, 3000}) {
                int m = n / 20;
                long seed = SEED + n;
                String text = distribution.equals("random")
                        ? randomText(new Random(seed), n) : "a".repeat(n);
                String pattern = distribution.equals("random")
                        ? text.substring(n / 3, n / 3 + m) : "a".repeat(m);
                List<Integer> expected = oracle(text, pattern);
                for (int method = 0; method < METHODS.length; method++) {
                    if (!search(method, text, pattern).equals(expected)) {
                        throw new AssertionError(distribution + " " + METHODS[method]);
                    }
                }
                for (int warmup = 0; warmup < WARMUP; warmup++) {
                    for (int method = 0; method < METHODS.length; method++) {
                        sink = search(method, text, pattern);
                    }
                }
                double[][] times = new double[METHODS.length][RUNS];
                for (int run = 0; run < RUNS; run++) {
                    for (int offset = 0; offset < METHODS.length; offset++) {
                        int method = (run + offset) % METHODS.length;
                        long start = System.nanoTime();
                        for (int repetition = 0; repetition < REPETITIONS; repetition++) {
                            sink = search(method, text, pattern);
                        }
                        times[method][run] = (System.nanoTime() - start) / (1000.0 * REPETITIONS);
                    }
                }
                for (int method = 0; method < METHODS.length; method++) {
                    double[] sorted = times[method].clone();
                    Arrays.sort(sorted);
                    String displayedSeed = distribution.equals("random") ? Long.toString(seed) : "-";
                    csv.append(String.format("%s,%d,%d,%s,%s,%.3f,%.3f,%.3f,%.3f,%d,%d%n",
                            distribution, n, m, displayedSeed, METHODS[method], times[method][0],
                            times[method][1], times[method][2], sorted[1], expected.size(), REPETITIONS));
                    report.append(String.format("| %s | %d | %d | %s | %s | %.3f | %.3f | %.3f | %.3f | %d |%n",
                            distribution, n, m, displayedSeed, METHODS[method], times[method][0],
                            times[method][1], times[method][2], sorted[1], expected.size()));
                }
            }
        }
        Files.writeString(directory.resolve("benchmark.csv"), csv, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("benchmark.md"), report, StandardCharsets.UTF_8);
        System.out.print(report);
    }
}
