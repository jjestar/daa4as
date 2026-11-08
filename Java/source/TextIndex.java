import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Suffix indexing and dictionary matching for printable ASCII text. */
final class TextIndex {
    private static final int ALPHABET = 95;

    private TextIndex() { }

    static Map<String, Object> suffix(Map<String, Object> input) {
        String text = (String) input.get("text");
        int n = text.length();
        int[] sa = new int[n];
        int[] lcp = new int[n];
        if (n == 0) {
            return Map.of("sa", sa, "lcp", lcp, "repeated", "");
        }

        int[] rank = new int[n];
        int[] nextRank = new int[n];
        int[] temporary = new int[n];
        int[] count = new int[Math.max(n, ALPHABET) + 1];
        for (int i = 0; i < n; i++) {
            sa[i] = i;
            // Zero is reserved for the missing second half of a suffix.
            rank[i] = text.charAt(i) - 31;
        }

        int classes = ALPHABET;
        for (int width = 1; width < n; width *= 2) {
            // Stable radix sorting: second rank first, then first rank.
            countingSort(sa, temporary, rank, width, classes, count);
            countingSort(temporary, sa, rank, 0, classes, count);
            int newClasses = 1;
            nextRank[sa[0]] = newClasses;
            for (int i = 1; i < n; i++) {
                int previous = sa[i - 1];
                int current = sa[i];
                int previousSecond = previous + width < n ? rank[previous + width] : 0;
                int currentSecond = current + width < n ? rank[current + width] : 0;
                if (rank[previous] != rank[current] || previousSecond != currentSecond) {
                    newClasses++;
                }
                nextRank[current] = newClasses;
            }
            int[] swap = rank;
            rank = nextRank;
            nextRank = swap;
            classes = newClasses;
            if (classes == n) {
                break;
            }
        }

        int[] position = new int[n];
        for (int i = 0; i < n; i++) {
            position[sa[i]] = i;
        }
        int matched = 0;
        int longest = 0;
        int repeatedStart = 0;
        for (int start = 0; start < n; start++) {
            int order = position[start];
            if (order == 0) {
                matched = 0;
                continue;
            }
            int previous = sa[order - 1];
            while (start + matched < n && previous + matched < n
                    && text.charAt(start + matched) == text.charAt(previous + matched)) {
                matched++;
            }
            lcp[order] = matched;
            if (matched > longest) {
                longest = matched;
                repeatedStart = start;
            }
            if (matched > 0) {
                matched--;
            }
        }
        return Map.of("sa", sa, "lcp", lcp,
                "repeated", text.substring(repeatedStart, repeatedStart + longest));
    }

    private static void countingSort(int[] source, int[] target, int[] rank,
                                     int offset, int classes, int[] count) {
        Arrays.fill(count, 0, classes + 1, 0);
        int n = rank.length;
        for (int start : source) {
            int key = start + offset < n ? rank[start + offset] : 0;
            count[key]++;
        }
        int total = 0;
        for (int key = 0; key <= classes; key++) {
            int frequency = count[key];
            count[key] = total;
            total += frequency;
        }
        for (int start : source) {
            int key = start + offset < n ? rank[start + offset] : 0;
            target[count[key]++] = start;
        }
    }

    private static final class State {
        final int[] next = new int[ALPHABET];
        int failure;
    }

    static Map<String, Object> aho(Map<String, Object> input) {
        List<?> patterns = (List<?>) input.get("patterns");
        List<?> texts = (List<?>) input.get("texts");
        List<State> states = new ArrayList<>();
        states.add(new State());
        int[] terminal = new int[patterns.size()];
        for (int i = 0; i < patterns.size(); i++) {
            String pattern = (String) patterns.get(i);
            int current = 0;
            for (int j = 0; j < pattern.length(); j++) {
                int letter = pattern.charAt(j) - 32;
                State state = states.get(current);
                if (state.next[letter] == 0) {
                    state.next[letter] = states.size();
                    states.add(new State());
                }
                current = state.next[letter];
            }
            // Repeated dictionary entries refer to the same terminal state.
            terminal[i] = current;
        }

        int[] order = new int[states.size()];
        int head = 0;
        int tail = 0;
        State root = states.get(0);
        for (int child : root.next) {
            if (child != 0) {
                order[tail++] = child;
            }
        }
        while (head < tail) {
            int current = order[head++];
            State state = states.get(current);
            State fallback = states.get(state.failure);
            for (int letter = 0; letter < ALPHABET; letter++) {
                int child = state.next[letter];
                if (child == 0) {
                    state.next[letter] = fallback.next[letter];
                } else {
                    states.get(child).failure = fallback.next[letter];
                    order[tail++] = child;
                }
            }
        }

        List<long[]> counts = new ArrayList<>(texts.size());
        for (Object value : texts) {
            String text = (String) value;
            long[] visits = new long[states.size()];
            int current = 0;
            for (int i = 0; i < text.length(); i++) {
                current = states.get(current).next[text.charAt(i) - 32];
                visits[current]++;
            }
            // Every visited state also matches each suffix on its failure chain.
            for (int i = tail - 1; i >= 0; i--) {
                int state = order[i];
                visits[states.get(state).failure] += visits[state];
            }
            long[] row = new long[patterns.size()];
            for (int i = 0; i < row.length; i++) {
                row[i] = visits[terminal[i]];
            }
            counts.add(row);
        }
        return Map.of("counts", counts);
    }
}
