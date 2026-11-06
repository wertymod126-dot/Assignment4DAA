import java.util.*;

/** Implementations of the eight A4 commands. docs/guide/CONTRACTS.md defines each field and limit. */
public final class Algorithms {
    private Algorithms() {}

    // ---------------------------------------------------------------- helpers

    private static String str(Map<String, Object> in, String key) { return (String) in.get(key); }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> in, String key) { return (List<Object>) in.get(key); }

    private static List<Integer> boxed(int[] a) {
        List<Integer> out = new ArrayList<>(a.length);
        for (int v : a) out.add(v);
        return out;
    }

    private static Map<String, Object> result(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    /** Every boundary 0..n is a match of the empty pattern. */
    private static List<Integer> allBoundaries(int n) {
        List<Integer> out = new ArrayList<>(n + 1);
        for (int i = 0; i <= n; i++) out.add(i);
        return out;
    }

    // ---------------------------------------------------------------- Part 1: LCS

    /**
     * dp[i][j] = LCS length of a[0..i) and b[0..j).
     * dp[i][j] = dp[i-1][j-1]+1 if a[i-1]==b[j-1], else max(dp[i-1][j], dp[i][j-1]).
     */
    static Map<String, Object> lcs(Map<String, Object> input) {
        String a = str(input, "a"), b = str(input, "b");
        int n = a.length(), m = b.length();
        if (n == 0 || m == 0) return result("length", 0, "sequence", "");
        int w = m + 1;
        int[] dp = new int[(n + 1) * w]; // row 0 and column 0 are the empty-prefix base cases (0)
        for (int i = 1; i <= n; i++) {
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                if (ca == b.charAt(j - 1)) dp[i * w + j] = dp[(i - 1) * w + j - 1] + 1;
                else dp[i * w + j] = Math.max(dp[(i - 1) * w + j], dp[i * w + j - 1]);
            }
        }
        int len = dp[n * w + m];
        char[] seq = new char[len];
        int i = n, j = m, k = len;
        while (i > 0 && j > 0) {
            if (a.charAt(i - 1) == b.charAt(j - 1)) { seq[--k] = a.charAt(i - 1); i--; j--; }
            else if (dp[(i - 1) * w + j] >= dp[i * w + j - 1]) i--;
            else j--;
        }
        return result("length", len, "sequence", new String(seq));
    }

    // ---------------------------------------------------------------- Part 2: merge (interval DP)

    /**
     * cost[l][r] = minimum cost to merge original blocks l..r into one block.
     * cost[l][r] = min over k in [l, r) of cost[l][k] + cost[k+1][r] + sum(l..r).
     */
    static Map<String, Object> merge(Map<String, Object> input) {
        List<Object> raw = list(input, "sizes");
        int n = raw.size();
        if (n <= 1) return result("cost", 0L, "plan", new ArrayList<>());
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) prefix[i + 1] = prefix[i] + (Long) raw.get(i);
        long[][] cost = new long[n][n];
        int[][] split = new int[n][n];
        for (int len = 2; len <= n; len++) {
            for (int l = 0; l + len - 1 < n; l++) {
                int r = l + len - 1;
                long best = Long.MAX_VALUE;
                int bestK = l;
                for (int k = l; k < r; k++) {
                    long c = cost[l][k] + cost[k + 1][r];
                    if (c < best) { best = c; bestK = k; }
                }
                cost[l][r] = best + prefix[r + 1] - prefix[l];
                split[l][r] = bestK;
            }
        }
        // Postorder over the split tree: both children are fully merged before their parent.
        List<List<Integer>> plan = new ArrayList<>(n - 1);
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{0, n - 1, 0});
        while (!stack.isEmpty()) {
            int[] f = stack.peek();
            int l = f[0], r = f[1];
            if (l == r) { stack.pop(); continue; }
            int k = split[l][r];
            if (f[2] == 0) { f[2] = 1; stack.push(new int[]{l, k, 0}); }
            else if (f[2] == 1) { f[2] = 2; stack.push(new int[]{k + 1, r, 0}); }
            else { stack.pop(); plan.add(List.of(l, k, r)); }
        }
        return result("cost", cost[0][n - 1], "plan", plan);
    }

    // ---------------------------------------------------------------- Part 3: tree (MWIS)

    /**
     * take[v] = best weight in v's subtree with v selected = w[v] + sum skip[c].
     * skip[v] = best weight in v's subtree with v not selected = sum max(take[c], skip[c]).
     * Iterative BFS order replaces recursion so deep chains cannot overflow the stack.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Object> tree(Map<String, Object> input) {
        int n = (int) (long) (Long) input.get("n");
        if (n == 0) return result("weight", 0L, "selected", new ArrayList<>());
        List<Object> ws = list(input, "weights");
        List<Object> edges = list(input, "edges");
        long[] w = new long[n];
        for (int i = 0; i < n; i++) w[i] = (Long) ws.get(i);

        // Compact adjacency lists (CSR).
        int[] deg = new int[n + 1];
        int[] eu = new int[edges.size()], ev = new int[edges.size()];
        for (int e = 0; e < edges.size(); e++) {
            List<Object> pair = (List<Object>) edges.get(e);
            eu[e] = (int) (long) (Long) pair.get(0);
            ev[e] = (int) (long) (Long) pair.get(1);
            deg[eu[e]]++; deg[ev[e]]++;
        }
        int[] start = new int[n + 1];
        for (int i = 0; i < n; i++) start[i + 1] = start[i] + deg[i];
        int[] fill = Arrays.copyOf(start, n);
        int[] adj = new int[2 * edges.size()];
        for (int e = 0; e < eu.length; e++) { adj[fill[eu[e]]++] = ev[e]; adj[fill[ev[e]]++] = eu[e]; }

        // BFS from root 0: order[] lists every parent before its children.
        int[] order = new int[n], parent = new int[n];
        Arrays.fill(parent, -1);
        boolean[] seen = new boolean[n];
        int head = 0, tail = 0;
        order[tail++] = 0; seen[0] = true;
        while (head < tail) {
            int v = order[head++];
            for (int p = start[v]; p < start[v + 1]; p++) {
                int u = adj[p];
                if (!seen[u]) { seen[u] = true; parent[u] = v; order[tail++] = u; }
            }
        }

        // Reverse BFS order = children before parents (postorder dependency).
        long[] take = new long[n], skip = new long[n];
        for (int idx = n - 1; idx >= 0; idx--) {
            int v = order[idx];
            take[v] += w[v];
            int p = parent[v];
            if (p >= 0) { take[p] += skip[v]; skip[p] += Math.max(take[v], skip[v]); }
        }

        // Top-down reconstruction: a selected parent forces its children to be skipped.
        boolean[] chosen = new boolean[n];
        List<Integer> selected = new ArrayList<>();
        for (int idx = 0; idx < n; idx++) {
            int v = order[idx];
            int p = parent[v];
            boolean pick = (p < 0 || !chosen[p]) && take[v] > skip[v];
            chosen[v] = pick;
            if (pick) selected.add(v);
        }
        return result("weight", Math.max(take[0], skip[0]), "selected", selected);
    }

    // ---------------------------------------------------------------- Part 4: naive search

    static Map<String, Object> naive(Map<String, Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return result("positions", allBoundaries(n));
        List<Integer> pos = new ArrayList<>();
        for (int s = 0; s + m <= n; s++) { // last valid start is n-m
            int j = 0;
            while (j < m && t.charAt(s + j) == p.charAt(j)) j++;
            if (j == m) pos.add(s);
        }
        return result("positions", pos);
    }

    // ---------------------------------------------------------------- Part 4: KMP

    /** pi[i] = length of the longest proper prefix of p[0..i] that is also a suffix of it. */
    static int[] prefixFunction(String p) {
        int m = p.length();
        int[] pi = new int[m];
        for (int i = 1; i < m; i++) {
            int k = pi[i - 1];
            while (k > 0 && p.charAt(i) != p.charAt(k)) k = pi[k - 1];
            if (p.charAt(i) == p.charAt(k)) k++;
            pi[i] = k;
        }
        return pi;
    }

    static Map<String, Object> kmp(Map<String, Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return result("positions", allBoundaries(n));
        List<Integer> pos = new ArrayList<>();
        if (m > n) return result("positions", pos);
        int[] pi = prefixFunction(p);
        int j = 0; // number of pattern characters currently matched
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            while (j > 0 && c != p.charAt(j)) j = pi[j - 1];
            if (c == p.charAt(j)) j++;
            if (j == m) { pos.add(i - m + 1); j = pi[m - 1]; } // fallback keeps overlaps
        }
        return result("positions", pos);
    }

    // ---------------------------------------------------------------- Part 4: Rabin-Karp

    static final long RK_BASE = 31, RK_MOD = 101;

    static Map<String, Object> rk(Map<String, Object> input) {
        String t = str(input, "text"), p = str(input, "pattern");
        int n = t.length(), m = p.length();
        if (m == 0) return result("positions", allBoundaries(n));
        List<Integer> pos = new ArrayList<>();
        if (m > n) return result("positions", pos);
        long high = 1; // BASE^(m-1) mod MOD: weight of the leftmost character
        for (int i = 1; i < m; i++) high = high * RK_BASE % RK_MOD;
        long hp = 0, hw = 0;
        for (int i = 0; i < m; i++) {
            hp = (hp * RK_BASE + p.charAt(i)) % RK_MOD;
            hw = (hw * RK_BASE + t.charAt(i)) % RK_MOD;
        }
        for (int s = 0; ; s++) {
            if (hw == hp) { // candidate only: verify characters
                int j = 0;
                while (j < m && t.charAt(s + j) == p.charAt(j)) j++;
                if (j == m) pos.add(s);
            }
            if (s + m >= n) break;
            hw = ((hw - t.charAt(s) * high) % RK_MOD + RK_MOD) % RK_MOD; // remove left char, normalize
            hw = (hw * RK_BASE + t.charAt(s + m)) % RK_MOD;               // add right char
        }
        return result("positions", pos);
    }

    // ---------------------------------------------------------------- Part 5: suffix array + Kasai

    /** Prefix doubling with two-pass counting (radix) sort per round: O(n log n). */
    static int[] suffixArray(String text) {
        int n = text.length();
        int[] sa = new int[n], rank = new int[n], tmp = new int[n], buf = new int[n];
        int classes = 128;
        int[] cnt = new int[Math.max(classes, n) + 1];
        for (int i = 0; i < n; i++) cnt[text.charAt(i)]++;
        for (int c = 1; c < classes; c++) cnt[c] += cnt[c - 1];
        for (int i = n - 1; i >= 0; i--) sa[--cnt[text.charAt(i)]] = i;
        rank[sa[0]] = 0;
        classes = 1;
        for (int i = 1; i < n; i++) {
            if (text.charAt(sa[i]) != text.charAt(sa[i - 1])) classes++;
            rank[sa[i]] = classes - 1;
        }
        for (int k = 1; classes < n; k <<= 1) {
            // Order by second key rank[i+k]; suffixes with i+k >= n have an empty second half (smallest).
            int idx = 0;
            for (int i = n - k; i < n; i++) if (i >= 0) buf[idx++] = i;
            for (int i = 0; i < n; i++) if (sa[i] >= k) buf[idx++] = sa[i] - k;
            // Stable counting sort by first key rank[i].
            Arrays.fill(cnt, 0, classes, 0);
            for (int i = 0; i < n; i++) cnt[rank[i]]++;
            for (int c = 1; c < classes; c++) cnt[c] += cnt[c - 1];
            for (int i = n - 1; i >= 0; i--) sa[--cnt[rank[buf[i]]]] = buf[i];
            // New classes: equal iff both halves have equal ranks.
            tmp[sa[0]] = 0;
            classes = 1;
            for (int i = 1; i < n; i++) {
                int a = sa[i - 1], b = sa[i];
                int a2 = a + k < n ? rank[a + k] : -1, b2 = b + k < n ? rank[b + k] : -1;
                if (rank[a] != rank[b] || a2 != b2) classes++;
                tmp[b] = classes - 1;
            }
            int[] swap = rank; rank = tmp; tmp = swap;
        }
        return sa;
    }

    /** Kasai: lcp[r] = LCP(sa[r-1], sa[r]); h drops by at most 1 when moving from suffix i to i+1. */
    static int[] kasai(String text, int[] sa) {
        int n = text.length();
        int[] rank = new int[n], lcp = new int[n];
        for (int r = 0; r < n; r++) rank[sa[r]] = r;
        int h = 0;
        for (int i = 0; i < n; i++) {
            if (rank[i] == 0) { h = 0; continue; }
            int j = sa[rank[i] - 1];
            while (i + h < n && j + h < n && text.charAt(i + h) == text.charAt(j + h)) h++;
            lcp[rank[i]] = h;
            if (h > 0) h--;
        }
        return lcp;
    }

    static Map<String, Object> suffix(Map<String, Object> input) {
        String text = str(input, "text");
        int n = text.length();
        if (n == 0) return result("sa", new ArrayList<>(), "lcp", new ArrayList<>(), "repeated", "");
        int[] sa = suffixArray(text);
        int[] lcp = kasai(text, sa);
        int best = 0;
        for (int r = 1; r < n; r++) if (lcp[r] > lcp[best]) best = r;
        String repeated = lcp[best] == 0 ? "" : text.substring(sa[best], sa[best] + lcp[best]);
        return result("sa", boxed(sa), "lcp", boxed(lcp), "repeated", repeated);
    }

    // ---------------------------------------------------------------- Part 6: Aho-Corasick

    static final int SIGMA = 95, FIRST = 32; // printable ASCII 32..126

    @SuppressWarnings("unchecked")
    static Map<String, Object> aho(Map<String, Object> input) {
        List<Object> patterns = list(input, "patterns");
        List<Object> texts = list(input, "texts");
        int p = patterns.size();
        List<List<Long>> counts = new ArrayList<>(texts.size());
        if (p == 0) {
            for (int q = 0; q < texts.size(); q++) counts.add(new ArrayList<>());
            return result("counts", counts);
        }

        int maxStates = 1;
        for (Object o : patterns) maxStates += ((String) o).length();
        int[] go = new int[maxStates * SIGMA];
        Arrays.fill(go, -1);
        int states = 1;

        // Trie; duplicate patterns share one terminal state.
        int[] terminal = new int[p];
        for (int i = 0; i < p; i++) {
            String s = (String) patterns.get(i);
            int v = 0;
            for (int c = 0; c < s.length(); c++) {
                int a = s.charAt(c) - FIRST;
                if (go[v * SIGMA + a] < 0) go[v * SIGMA + a] = states++;
                v = go[v * SIGMA + a];
            }
            terminal[i] = v;
        }

        // BFS: failure links and full goto transitions (the DFA).
        int[] fail = new int[states], bfs = new int[states];
        int head = 0, tail = 0;
        for (int a = 0; a < SIGMA; a++) {
            int u = go[a];
            if (u < 0) go[a] = 0;
            else { fail[u] = 0; bfs[tail++] = u; }
        }
        while (head < tail) {
            int v = bfs[head++];
            for (int a = 0; a < SIGMA; a++) {
                int u = go[v * SIGMA + a];
                int f = go[fail[v] * SIGMA + a];
                if (u < 0) go[v * SIGMA + a] = f;
                else { fail[u] = f; bfs[tail++] = u; }
            }
        }

        // Per text: count state visits, then push counts up failure links in reverse BFS order.
        long[] visits = new long[states];
        for (Object o : texts) {
            String t = (String) o;
            Arrays.fill(visits, 0);
            int v = 0;
            for (int i = 0; i < t.length(); i++) {
                v = go[v * SIGMA + (t.charAt(i) - FIRST)];
                visits[v]++;
            }
            for (int idx = tail - 1; idx >= 0; idx--) {
                int u = bfs[idx];
                visits[fail[u]] += visits[u];
            }
            List<Long> row = new ArrayList<>(p);
            for (int i = 0; i < p; i++) row.add(visits[terminal[i]]);
            counts.add(row);
        }
        return result("counts", counts);
    }
}
