import java.util.Map;

/** Request handlers for the eight assignment commands. */
public final class Algorithms {
    private Algorithms() {}

    static Map<String, Object> lcs(Map<String, Object> input) {
        return DynamicProgramming.lcs(input);
    }

    static Map<String, Object> merge(Map<String, Object> input) {
        return DynamicProgramming.merge(input);
    }

    static Map<String, Object> tree(Map<String, Object> input) {
        return DynamicProgramming.tree(input);
    }

    static Map<String, Object> naive(Map<String, Object> input) {
        return Map.of("positions", StringSearch.naive((String) input.get("text"),
                (String) input.get("pattern")));
    }

    static Map<String, Object> kmp(Map<String, Object> input) {
        return Map.of("positions", StringSearch.kmp((String) input.get("text"),
                (String) input.get("pattern")));
    }

    static Map<String, Object> rk(Map<String, Object> input) {
        return Map.of("positions", StringSearch.rk((String) input.get("text"),
                (String) input.get("pattern")));
    }

    static Map<String, Object> suffix(Map<String, Object> input) {
        return TextIndex.suffix(input);
    }

    static Map<String, Object> aho(Map<String, Object> input) {
        return TextIndex.aho(input);
    }
}
