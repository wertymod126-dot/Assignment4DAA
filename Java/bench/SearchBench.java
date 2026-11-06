import java.util.*;

/**
 * Search comparison for naive / KMP / Rabin-Karp on identical inputs.
 *
 * Build and run from the repository root (not part of the graded program):
 *   javac --release 17 -d Java/build-bench Java/source/*.java Java/bench/SearchBench.java
 *   java -Xmx384m -cp Java/build-bench SearchBench
 *
 * Timing scope: only the Algorithms.naive/kmp/rk call on an already-built request map
 * (no JSON parsing, no output printing). Each method gets WARMUP untimed calls per input
 * (JIT warm-up), then RUNS timed calls; the median is reported. All three outputs are
 * compared before timing.
 */
public final class SearchBench {
    static final long SEED = 20261106L;
    static final int WARMUP = 300, RUNS = 5;
    static final int[][] SIZES = {{1000, 100}, {2000, 200}, {3000, 300}};

    interface Search { Map<String, Object> run(Map<String, Object> in); }

    public static void main(String[] args) {
        Random rng = new Random(SEED);
        String[] names = {"naive", "kmp", "rk"};
        Search[] algs = {Algorithms::naive, Algorithms::kmp, Algorithms::rk};
        System.out.println("case|n|m|algorithm|matches|run1_us|run2_us|run3_us|run4_us|run5_us|median_us");
        for (String kind : new String[]{"random", "repetitive"}) {
            for (int[] nm : SIZES) {
                int n = nm[0], m = nm[1];
                String text, pattern;
                if (kind.equals("random")) {
                    // Uniform letters a-z; pattern is a substring copied from a random text position.
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < n; i++) sb.append((char) ('a' + rng.nextInt(26)));
                    text = sb.toString();
                    int s = rng.nextInt(n - m + 1);
                    pattern = text.substring(s, s + m);
                } else {
                    // Worst case for naive: every window matches m-1 characters then fails.
                    text = "a".repeat(n);
                    pattern = "a".repeat(m - 1) + "b";
                }
                Map<String, Object> in = new HashMap<>();
                in.put("text", text);
                in.put("pattern", pattern);

                Object ref = algs[0].run(in).get("positions");
                for (Search a : algs)
                    if (!a.run(in).get("positions").equals(ref)) throw new AssertionError("outputs differ");
                int matches = ((List<?>) ref).size();

                for (int a = 0; a < algs.length; a++) {
                    for (int w = 0; w < WARMUP; w++) algs[a].run(in);
                    long[] t = new long[RUNS];
                    for (int r = 0; r < RUNS; r++) {
                        long t0 = System.nanoTime();
                        algs[a].run(in);
                        t[r] = System.nanoTime() - t0;
                    }
                    long[] sorted = t.clone();
                    Arrays.sort(sorted);
                    StringBuilder row = new StringBuilder(kind + "|" + n + "|" + m + "|" + names[a] + "|" + matches);
                    for (long x : t) row.append('|').append(String.format(Locale.ROOT, "%.1f", x / 1000.0));
                    row.append('|').append(String.format(Locale.ROOT, "%.1f", sorted[RUNS / 2] / 1000.0));
                    System.out.println(row);
                }
            }
        }
    }
}
