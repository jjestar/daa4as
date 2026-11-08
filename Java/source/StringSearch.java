import java.util.ArrayList;
import java.util.List;

/** Exact searches; positions include overlapping occurrences. */
final class StringSearch {
    private static final long BASE = 31;
    private static final long MODULUS = 101;

    private StringSearch() {}

    static List<Integer> naive(String text, String pattern) {
        List<Integer> positions = new ArrayList<>();
        int n = text.length(), m = pattern.length();
        for (int start = 0; start <= n - m; start++) {
            int matched = 0;
            while (matched < m && text.charAt(start + matched) == pattern.charAt(matched)) {
                matched++;
            }
            if (matched == m) positions.add(start);
        }
        return positions;
    }

    static List<Integer> kmp(String text, String pattern) {
        int n = text.length(), m = pattern.length();
        if (m == 0) return boundaries(n);
        List<Integer> positions = new ArrayList<>();
        if (m > n) return positions;

        int[] pi = new int[m];
        for (int i = 1; i < m; i++) {
            int matched = pi[i - 1];
            while (matched > 0 && pattern.charAt(i) != pattern.charAt(matched)) {
                matched = pi[matched - 1];
            }
            if (pattern.charAt(i) == pattern.charAt(matched)) matched++;
            pi[i] = matched;
        }

        int matched = 0;
        for (int i = 0; i < n; i++) {
            while (matched > 0 && text.charAt(i) != pattern.charAt(matched)) {
                matched = pi[matched - 1];
            }
            if (text.charAt(i) == pattern.charAt(matched)) matched++;
            if (matched == m) {
                positions.add(i - m + 1);
                matched = pi[matched - 1];
            }
        }
        return positions;
    }

    static List<Integer> rk(String text, String pattern) {
        int n = text.length(), m = pattern.length();
        if (m == 0) return boundaries(n);
        List<Integer> positions = new ArrayList<>();
        if (m > n) return positions;

        long leadingPower = 1, targetHash = 0, windowHash = 0;
        for (int i = 0; i < m; i++) {
            targetHash = (targetHash * BASE + pattern.charAt(i)) % MODULUS;
            windowHash = (windowHash * BASE + text.charAt(i)) % MODULUS;
            if (i < m - 1) leadingPower = leadingPower * BASE % MODULUS;
        }

        for (int start = 0; start <= n - m; start++) {
            if (windowHash == targetHash) {
                int matched = 0;
                while (matched < m && text.charAt(start + matched) == pattern.charAt(matched)) {
                    matched++;
                }
                if (matched == m) positions.add(start);
            }
            if (start < n - m) {
                long outgoing = text.charAt(start) * leadingPower % MODULUS;
                windowHash = (windowHash - outgoing + MODULUS) % MODULUS;
                windowHash = (windowHash * BASE + text.charAt(start + m)) % MODULUS;
            }
        }
        return positions;
    }

    private static List<Integer> boundaries(int length) {
        List<Integer> positions = new ArrayList<>(length + 1);
        for (int i = 0; i <= length; i++) positions.add(i);
        return positions;
    }
}
