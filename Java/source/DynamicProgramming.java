import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Dynamic programming algorithms and their reconstructed witnesses. */
final class DynamicProgramming {
    private DynamicProgramming() {}

    static Map<String, Object> lcs(Map<String, Object> input) {
        String a = (String) input.get("a");
        String b = (String) input.get("b");
        int n = a.length();
        int m = b.length();
        if (n == 0 || m == 0) {
            return Map.of("length", 0, "sequence", "");
        }

        // dp[i][j] compares the prefixes a[0..i) and b[0..j).
        int[][] dp = new int[n + 1][m + 1];
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                if (a.charAt(i - 1) == b.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        StringBuilder sequence = new StringBuilder(dp[n][m]);
        int i = n;
        int j = m;
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) {
                sequence.append(a.charAt(i - 1));
                i--;
                j--;
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--; // Prefer the shorter prefix of a when both choices are optimal.
            } else {
                j--;
            }
        }
        return Map.of("length", dp[n][m], "sequence", sequence.reverse().toString());
    }

    static Map<String, Object> merge(Map<String, Object> input) {
        List<?> sizes = (List<?>) input.get("sizes");
        int n = sizes.size();
        List<List<Integer>> plan = new ArrayList<>();
        if (n <= 1) {
            return Map.of("cost", 0L, "plan", plan);
        }

        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + ((Number) sizes.get(i)).longValue();
        }
        long[][] dp = new long[n][n];
        int[][] split = new int[n][n];
        for (int length = 2; length <= n; length++) {
            for (int left = 0; left + length <= n; left++) {
                int right = left + length - 1;
                long intervalSize = prefix[right + 1] - prefix[left];
                long best = Long.MAX_VALUE;
                for (int middle = left; middle < right; middle++) {
                    long candidate = dp[left][middle] + dp[middle + 1][right]
                            + intervalSize;
                    if (candidate < best) {
                        best = candidate;
                        split[left][right] = middle;
                    }
                }
                dp[left][right] = best;
            }
        }
        appendMergePlan(0, n - 1, split, plan);
        return Map.of("cost", dp[0][n - 1], "plan", plan);
    }

    // The published merge limit is 200, so this recursion has at most 200 frames.
    private static void appendMergePlan(int left, int right, int[][] split,
                                        List<List<Integer>> plan) {
        if (left == right) {
            return;
        }
        int middle = split[left][right];
        appendMergePlan(left, middle, split, plan);
        appendMergePlan(middle + 1, right, split, plan);
        plan.add(List.of(left, middle, right));
    }

    static Map<String, Object> tree(Map<String, Object> input) {
        int n = ((Number) input.get("n")).intValue();
        List<Integer> selected = new ArrayList<>();
        if (n == 0) {
            return Map.of("weight", 0L, "selected", selected);
        }

        List<?> weights = (List<?>) input.get("weights");
        List<?> edges = (List<?>) input.get("edges");
        int[] degrees = new int[n];
        for (Object item : edges) {
            List<?> edge = (List<?>) item;
            degrees[((Number) edge.get(0)).intValue()]++;
            degrees[((Number) edge.get(1)).intValue()]++;
        }
        int[][] neighbours = new int[n][];
        for (int vertex = 0; vertex < n; vertex++) {
            neighbours[vertex] = new int[degrees[vertex]];
        }
        Arrays.fill(degrees, 0);
        for (Object item : edges) {
            List<?> edge = (List<?>) item;
            int u = ((Number) edge.get(0)).intValue();
            int v = ((Number) edge.get(1)).intValue();
            neighbours[u][degrees[u]++] = v;
            neighbours[v][degrees[v]++] = u;
        }

        // A parent-before-child order acts as a queue; reversing it gives postorder.
        int[] parent = new int[n];
        int[] order = new int[n];
        parent[0] = -1;
        int count = 1;
        for (int cursor = 0; cursor < count; cursor++) {
            int vertex = order[cursor];
            for (int next : neighbours[vertex]) {
                if (next != parent[vertex]) {
                    parent[next] = vertex;
                    order[count++] = next;
                }
            }
        }

        long[] take = new long[n];
        long[] skip = new long[n];
        for (int vertex = 0; vertex < n; vertex++) {
            take[vertex] = ((Number) weights.get(vertex)).longValue();
        }
        for (int cursor = n - 1; cursor > 0; cursor--) {
            int vertex = order[cursor];
            int p = parent[vertex];
            take[p] += skip[vertex];
            skip[p] += Math.max(take[vertex], skip[vertex]);
        }

        boolean[] chosen = new boolean[n];
        for (int vertex : order) {
            int p = parent[vertex];
            chosen[vertex] = (p < 0 || !chosen[p]) && take[vertex] > skip[vertex];
            if (chosen[vertex]) {
                selected.add(vertex);
            }
        }
        return Map.of("weight", Math.max(take[0], skip[0]), "selected", selected);
    }
}
