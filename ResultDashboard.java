import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Dashboard for Q8 (GA scheduling in CloudSim).
 * Run THIS class: if the results folder is empty it first runs the GAScheduler simulation,
 * then opens a window with all graphs and tables. Uses only the Java standard library (Swing).
 *   java -cp ... ResultsDashboard [resultsDir] [--png]     (--png saves charts to <resultsDir>/charts)
 */
public class ResultDashboard {
    static final String[] ORDER = {"Random", "Round Robin", "Min-Min", "Max-Min", "Greedy-MCT", "GA (proposed)"};
    static final Color[] COL = {new Color(176,183,195), new Color(143,155,176), new Color(111,134,173),
            new Color(79,116,168), new Color(47,98,161), new Color(217,84,30)};
    static final int[] SIZES = {50, 100, 200, 400, 800};
    static String dir = "results";
    static final Map<String, Chart> CHARTS = new LinkedHashMap<>();

    // ------------------------------------------------------------------ data helpers
    static List<String[]> csv(String f) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(dir, f));
        List<String[]> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) if (!lines.get(i).isBlank()) rows.add(lines.get(i).split(","));
        return rows;
    }
    static int idx(String algo) { for (int i = 0; i < ORDER.length; i++) if (ORDER[i].equals(algo)) return i; return -1; }

    /** algorithm -> {makespan mean, makespan std, cpu%, mem%, wastage, imbalance, time ms} from main_runs.csv */
    static double[][] mainAgg() throws IOException {
        double[][] s = new double[ORDER.length][7]; double[] n = new double[ORDER.length]; double[] sq = new double[ORDER.length];
        for (String[] r : csv("main_runs.csv")) {
            int a = idx(r[0]); double mk = Double.parseDouble(r[2]);
            s[a][0] += mk; sq[a] += mk * mk;
            s[a][2] += Double.parseDouble(r[3]) * 100; s[a][3] += Double.parseDouble(r[4]) * 100;
            s[a][4] += Double.parseDouble(r[5]); s[a][5] += Double.parseDouble(r[6]); s[a][6] += Double.parseDouble(r[8]);
            n[a]++;
        }
        for (int a = 0; a < ORDER.length; a++) {
            double m = s[a][0] / n[a]; s[a][1] = Math.sqrt(Math.max(0, sq[a] / n[a] - m * m));
            for (int k = 0; k < 7; k++) if (k != 1) s[a][k] /= n[a];
        }
        return s;
    }
    /** scalability.csv column (2..7) -> [algo][size] */
    static double[][] scale(int col, double mult) throws IOException {
        double[][] v = new double[ORDER.length][SIZES.length];
        for (String[] r : csv("scalability.csv")) {
            int a = idx(r[1]), s = Arrays.binarySearch(SIZES, Integer.parseInt(r[0]));
            v[a][s] = Double.parseDouble(r[col]) * mult;
        }
        return v;
    }
    static double[] col(double[][] m, int c) { double[] o = new double[m.length]; for (int i = 0; i < m.length; i++) o[i] = m[i][c]; return o; }

    // ------------------------------------------------------------------ chart component
    static class Chart extends JPanel {
        String title, xl, yl; boolean line, logX, logY; String[] cats, names; Color[] cols;
        double[][] v, err; double[] xs, lo, hi; double ref = Double.NaN; String refLabel; int dec = 1;
        private double y0, y1, xmn, xmx; private int pL, pT, pw, ph;
        Chart(String t, String xl, String yl) { title = t; this.xl = xl; this.yl = yl; setBackground(Color.WHITE); setPreferredSize(new Dimension(520, 340)); }

        static double[] nice(double mn, double mx, int n) {
            if (mx <= mn) mx = mn + 1;
            double raw = (mx - mn) / n, mag = Math.pow(10, Math.floor(Math.log10(raw))), f = raw / mag;
            double step = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * mag;
            return new double[]{Math.floor(mn / step) * step, Math.ceil(mx / step) * step, step};
        }
        double fy(double val) {
            double t = logY ? (Math.log10(val) - Math.log10(y0)) / (Math.log10(y1) - Math.log10(y0)) : (val - y0) / (y1 - y0);
            return pT + ph * (1 - t);
        }
        double fx(double x) {
            double t = logX ? (Math.log(x) - Math.log(xmn)) / (Math.log(xmx) - Math.log(xmn)) : (x - xmn) / (xmx - xmn);
            return pL + 12 + (pw - 24) * t;
        }
        static String num(double x, double step) { return step >= 1 ? String.format("%.0f", x) : step >= 0.1 ? String.format("%.1f", x) : String.format("%.2f", x); }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int W = getWidth(), H = getHeight();
            boolean leg = names != null && names.length > 1;
            pL = 70; int R = leg ? 150 : 25, T = 42, B = line ? 54 : 92;
            pT = T; pw = W - pL - R; ph = H - T - B;
            g.setFont(new Font("SansSerif", Font.BOLD, 13)); FontMetrics fm = g.getFontMetrics();
            g.setColor(new Color(30, 30, 30)); g.drawString(title, (W - fm.stringWidth(title)) / 2, 24);

            double mn = Double.MAX_VALUE, mx = -Double.MAX_VALUE;
            for (int s = 0; s < v.length; s++) for (int i = 0; i < v[s].length; i++) {
                double e = err != null ? err[s][i] : 0; mn = Math.min(mn, v[s][i] - e); mx = Math.max(mx, v[s][i] + e);
            }
            if (lo != null) for (double d : lo) mn = Math.min(mn, d);
            if (hi != null) for (double d : hi) mx = Math.max(mx, d);
            if (!Double.isNaN(ref)) { mn = Math.min(mn, ref); mx = Math.max(mx, ref); }
            double step = 0;
            if (logY) { y0 = Math.pow(10, Math.floor(Math.log10(mn))); y1 = Math.pow(10, Math.ceil(Math.log10(mx))); if (y1 <= y0) y1 = y0 * 10; }
            else { if (!line) mn = Math.min(0, mn); double[] ns = nice(mn, mx + (mx - mn) * 0.08, 5); y0 = ns[0]; y1 = ns[1]; step = ns[2]; }

            // grid + y ticks
            g.setFont(new Font("SansSerif", Font.PLAIN, 10)); fm = g.getFontMetrics();
            java.util.List<Double> ticks = new ArrayList<>();
            if (logY) for (double p = y0; p <= y1 * 1.0001; p *= 10) ticks.add(p);
            else for (double t = y0; t <= y1 + step * 0.01; t += step) ticks.add(t);
            for (double t : ticks) {
                double y = fy(t); g.setColor(new Color(225, 225, 225)); g.draw(new Line2D.Double(pL, y, pL + pw, y));
                g.setColor(Color.DARK_GRAY); String s = logY ? String.format("%.0f", t) : num(t, step);
                g.drawString(s, pL - 6 - fm.stringWidth(s), (float) y + 4);
            }
            g.setColor(Color.GRAY); g.draw(new Line2D.Double(pL, pT, pL, pT + ph)); g.draw(new Line2D.Double(pL, pT + ph, pL + pw, pT + ph));

            if (!Double.isNaN(ref)) {
                g.setColor(Color.BLACK); g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 4f}, 0f));
                g.draw(new Line2D.Double(pL, fy(ref), pL + pw, fy(ref))); g.setStroke(new BasicStroke(1f));
                if (refLabel != null) { FontMetrics rf = g.getFontMetrics(); String t = "- - -  " + refLabel; int tw = rf.stringWidth(t), tx = pL + pw - tw - 10, ty = pT + 8;
                    g.setColor(new Color(255, 255, 255, 230)); g.fillRect(tx - 4, ty - 2, tw + 8, 18); g.setColor(Color.BLACK); g.drawString(t, tx, ty + 11); }
            }

            if (!line) drawBars(g);
            else drawLines(g);

            // axis titles
            g.setFont(new Font("SansSerif", Font.PLAIN, 11)); fm = g.getFontMetrics(); g.setColor(Color.BLACK);
            if (yl != null) { AffineTransform o = g.getTransform(); g.translate(16, pT + ph / 2.0 + fm.stringWidth(yl) / 2.0); g.rotate(-Math.PI / 2); g.drawString(yl, 0, 0); g.setTransform(o); }
            if (xl != null) g.drawString(xl, pL + (pw - fm.stringWidth(xl)) / 2, H - 8);

            if (leg) {
                int lx = W - R + 12, ly = T + 4;
                for (int s = 0; s < names.length; s++) {
                    g.setColor(cols[s]); g.fillRect(lx, ly + s * 18, 11, 11);
                    g.setColor(Color.BLACK); g.drawString(names[s], lx + 16, ly + s * 18 + 10);
                }
            }
        }

        void drawBars(Graphics2D g) {
            int n = cats.length, S = v.length; double gw = (double) pw / n, bw = gw * 0.72 / S;
            Font small = new Font("SansSerif", Font.PLAIN, 9), axis = new Font("SansSerif", Font.PLAIN, 10);
            for (int i = 0; i < n; i++) {
                for (int s = 0; s < S; s++) {
                    double x = pL + gw * i + gw * 0.14 + bw * s, yy = fy(v[s][i]), base = fy(y0);
                    g.setColor(S == 1 ? cols[i] : cols[s]); g.fill(new Rectangle2D.Double(x, yy, bw, base - yy));
                    double top = yy;
                    if (err != null && err[s][i] > 0) {
                        double a = fy(v[s][i] + err[s][i]), b = fy(Math.max(y0, v[s][i] - err[s][i])), cx = x + bw / 2;
                        g.setColor(Color.DARK_GRAY); g.draw(new Line2D.Double(cx, a, cx, b));
                        g.draw(new Line2D.Double(cx - 3, a, cx + 3, a)); g.draw(new Line2D.Double(cx - 3, b, cx + 3, b)); top = a;
                    }
                    if (S == 1 || bw > 20) {
                        g.setFont(small); String t = String.format("%." + dec + "f", v[s][i]); FontMetrics f = g.getFontMetrics();
                        g.setColor(Color.BLACK); g.drawString(t, (float) (x + bw / 2 - f.stringWidth(t) / 2.0), (float) top - 3);
                    }
                }
                g.setFont(axis); FontMetrics f = g.getFontMetrics(); g.setColor(Color.BLACK);
                AffineTransform o = g.getTransform(); g.translate(pL + gw * (i + 0.5) + 4, pT + ph + 12); g.rotate(Math.toRadians(-35));
                g.drawString(cats[i], -f.stringWidth(cats[i]), 0); g.setTransform(o);
            }
        }

        void drawLines(Graphics2D g) {
            xmn = xs[0]; xmx = xs[xs.length - 1];
            g.setFont(new Font("SansSerif", Font.PLAIN, 10)); FontMetrics fm = g.getFontMetrics(); g.setColor(Color.DARK_GRAY);
            if (xs.length <= 10) {
                boolean isInt = true; for (double x : xs) if (x != Math.rint(x)) isInt = false;
                for (double x : xs) { String s = isInt ? String.format("%.0f", x) : String.format("%.2f", x); g.drawString(s, (float) (fx(x) - fm.stringWidth(s) / 2.0), pT + ph + 16); }
            } else {
                double[] ns = nice(xmn, xmx, 6);
                for (double t = ns[0]; t <= xmx + 0.01; t += ns[2]) { if (t < xmn) continue; String s = String.format("%.0f", t); g.drawString(s, (float) (fx(t) - fm.stringWidth(s) / 2.0), pT + ph + 16); }
            }
            if (lo != null && hi != null) {
                Path2D p = new Path2D.Double(); p.moveTo(fx(xs[0]), fy(hi[0]));
                for (int i = 1; i < xs.length; i++) p.lineTo(fx(xs[i]), fy(hi[i]));
                for (int i = xs.length - 1; i >= 0; i--) p.lineTo(fx(xs[i]), fy(lo[i]));
                p.closePath(); Color c = cols[0]; g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 50)); g.fill(p);
            }
            for (int s = 0; s < v.length; s++) {
                boolean ga = names != null && names[s].startsWith("GA");
                g.setColor(cols[s]); g.setStroke(new BasicStroke(ga ? 2.6f : 1.6f));
                Path2D p = new Path2D.Double();
                for (int i = 0; i < xs.length; i++) { if (i == 0) p.moveTo(fx(xs[i]), fy(v[s][i])); else p.lineTo(fx(xs[i]), fy(v[s][i])); }
                g.draw(p); g.setStroke(new BasicStroke(1f));
                if (xs.length <= 10) for (int i = 0; i < xs.length; i++) {
                    double x = fx(xs[i]), y = fy(v[s][i]);
                    if (err != null) { g.draw(new Line2D.Double(x, fy(v[s][i] + err[s][i]), x, fy(v[s][i] - err[s][i]))); }
                    g.fill(new Ellipse2D.Double(x - 3.5, y - 3.5, 7, 7));
                }
            }
        }
    }

    static Chart reg(String key, Chart c) { CHARTS.put(key, c); return c; }
    static Chart bars(String key, String title, String yl, String[] cats, String[] names, Color[] cols, double[][] v, double[][] e, int dec) {
        Chart c = new Chart(title, null, yl); c.cats = cats; c.names = names; c.cols = cols; c.v = v; c.err = e; c.dec = dec; return reg(key, c);
    }
    static Chart lines(String key, String title, String xl, String yl, double[] xs, String[] names, Color[] cols, double[][] v) {
        Chart c = new Chart(title, xl, yl); c.line = true; c.xs = xs; c.names = names; c.cols = cols; c.v = v; return reg(key, c);
    }
    static JPanel grid(int r, int c, JComponent... comps) { JPanel p = new JPanel(new GridLayout(r, c, 6, 6)); p.setBackground(Color.WHITE); for (JComponent x : comps) p.add(x); return p; }
    static JScrollPane table(String[] head, List<Object[]> rows) {
        DefaultTableModel m = new DefaultTableModel(rows.toArray(new Object[0][]), head) { public boolean isCellEditable(int r, int c) { return false; } };
        JTable t = new JTable(m); t.setRowHeight(22); t.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 12)); t.setFillsViewportHeight(true);
        return new JScrollPane(t);
    }

    // ------------------------------------------------------------------ build all tabs
    static JTabbedPane build() throws IOException {
        double[][] A = mainAgg(); JTabbedPane tabs = new JTabbedPane();
        double lb = 130.652; // printed by GAScheduler for the 100-task scenario
        // 1 Summary
        Chart mk = bars("makespan", "Makespan comparison (100 tasks, mean ± SD over 30 runs)", "Makespan (s)", ORDER, null, COL,
                new double[][]{col(A, 0)}, new double[][]{col(A, 1)}, 1);
        mk.ref = lb; mk.refLabel = "Lower bound = 130.65 s";
        List<Object[]> rows = new ArrayList<>();
        for (int a = 0; a < ORDER.length; a++) rows.add(new Object[]{ORDER[a], String.format("%.1f", A[a][0]), String.format("%.1f", A[a][1]),
                String.format("%.1f", A[a][2]), String.format("%.1f", A[a][3]), String.format("%.3f", A[a][4]), String.format("%.3f", A[a][5]), String.format("%.2f", A[a][6])});
        JPanel sum = new JPanel(new BorderLayout(0, 6)); sum.setBackground(Color.WHITE); sum.add(mk, BorderLayout.CENTER);
        JScrollPane tp = table(new String[]{"Algorithm", "Makespan (s)", "SD", "CPU util (%)", "Mem util (%)", "Wastage", "Imbalance", "Sched. time (ms)"}, rows);
        tp.setPreferredSize(new Dimension(800, 160)); sum.add(tp, BorderLayout.SOUTH); tabs.addTab("Summary", sum);
        // 2 Utilization + imbalance
        Chart ut = bars("utilization", "Resource utilization (100 tasks)", "Utilization (%)", ORDER, new String[]{"CPU", "Memory"},
                new Color[]{new Color(47, 98, 161), new Color(224, 160, 48)}, new double[][]{col(A, 2), col(A, 3)}, null, 1);
        Chart im = bars("imbalance", "Load imbalance (lower is better)", "Degree of imbalance", ORDER, null, COL, new double[][]{col(A, 5)}, null, 3);
        tabs.addTab("Utilization & Imbalance", grid(1, 2, ut, im));
        // 3 VM load
        String[] vms = {"VM1 (4c)", "VM2 (2c)", "VM3 (8c)", "VM4 (6c)", "VM5 (2c)"}; double[][] L = new double[ORDER.length][5];
        for (String[] r : csv("vm_load_main.csv")) L[idx(r[0])][Integer.parseInt(r[1].substring(2)) - 1] = Double.parseDouble(r[2]);
        tabs.addTab("Load per VM", grid(1, 1, bars("vmload", "Tasks assigned per VM (100 tasks)", "Tasks", vms, ORDER, COL, L, null, 0)));
        // 4 Convergence
        List<String[]> cv = csv("convergence.csv"); int G = cv.size(); double[] gx = new double[G], mean = new double[G], mi = new double[G], ma = new double[G];
        for (int i = 0; i < G; i++) { String[] r = cv.get(i); gx[i] = Double.parseDouble(r[0]); mean[i] = Double.parseDouble(r[1]); mi[i] = Double.parseDouble(r[2]); ma[i] = Double.parseDouble(r[3]); }
        Chart cc = lines("convergence", "GA convergence (mean best fitness; band = min–max of 30 runs)", "Generation", "Fitness (lower is better)", gx,
                new String[]{"GA mean best"}, new Color[]{COL[5]}, new double[][]{mean}); cc.lo = mi; cc.hi = ma;
        tabs.addTab("GA Convergence", grid(1, 1, cc));
        // 5 Scalability
        double[] sx = new double[SIZES.length]; for (int i = 0; i < sx.length; i++) sx[i] = SIZES[i];
        Chart s1 = lines("scale_makespan", "Makespan vs number of tasks (log–log)", "Tasks", "Makespan (s)", sx, ORDER, COL, scale(2, 1)); s1.logX = true; s1.logY = true;
        Chart s2 = lines("scale_cpu", "CPU utilization vs number of tasks", "Tasks", "CPU utilization (%)", sx, ORDER, COL, scale(4, 100)); s2.logX = true;
        Chart s3 = lines("scale_imbalance", "Load imbalance vs number of tasks", "Tasks", "Degree of imbalance", sx, ORDER, COL, scale(6, 1)); s3.logX = true;
        Chart s4 = lines("scale_time", "Scheduling time vs number of tasks (log–log)", "Tasks", "Time (ms)", sx, ORDER, COL, scale(7, 1)); s4.logX = true; s4.logY = true;
        tabs.addTab("Scalability", grid(2, 2, s1, s2, s3, s4));
        // 6 Alpha
        List<String[]> al = csv("alpha_tradeoff.csv"); al.removeIf(r -> Double.parseDouble(r[0]) == 0.0); int k = al.size();
        double[] ax = new double[k], am = new double[k], asd = new double[k], aw = new double[k];
        for (int i = 0; i < k; i++) { String[] r = al.get(i); ax[i] = Double.parseDouble(r[0]); am[i] = Double.parseDouble(r[1]); asd[i] = Double.parseDouble(r[2]); aw[i] = Double.parseDouble(r[4]); }
        Chart a1 = lines("alpha_makespan", "Makespan vs weight α (α = 0 omitted: degenerate)", "α (weight on makespan)", "Makespan (s)", ax, new String[]{"GA"}, new Color[]{COL[5]}, new double[][]{am}); a1.err = new double[][]{asd};
        Chart a2 = lines("alpha_wastage", "Resource wastage vs weight α", "α (weight on makespan)", "Mean wastage", ax, new String[]{"GA"}, new Color[]{COL[4]}, new double[][]{aw});
        tabs.addTab("α Trade-off", grid(1, 2, a1, a2));
        // 7 Validation
        List<Object[]> vr = new ArrayList<>();
        for (String[] r : csv("cloudsim_validation.csv")) vr.add(new Object[]{r[0], r[1], r[2], r[3], r[4], r[5] + " %"});
        tabs.addTab("CloudSim Validation", table(new String[]{"Tasks", "Algorithm", "Analytic makespan (s)", "CloudSim makespan (s)", "Abs. diff (s)", "Diff (%)"}, vr));
        return tabs;
    }

    static void savePngs() throws Exception {
        Path out = Paths.get(dir, "charts"); Files.createDirectories(out);
        for (Map.Entry<String, Chart> e : CHARTS.entrySet()) {
            Chart c = e.getValue(); c.setSize(780, 460);
            BufferedImage img = new BufferedImage(780, 460, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, 780, 460); c.paint(g); g.dispose();
            ImageIO.write(img, "png", out.resolve(e.getKey() + ".png").toFile());
        }
        System.out.println("Saved " + CHARTS.size() + " charts to " + out.toAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        boolean png = GraphicsEnvironment.isHeadless();
        for (String a : args) { if (a.equals("--png")) png = true; else dir = a; }
        if (!Files.exists(Paths.get(dir, "scalability.csv"))) {
            System.out.println("No results found in '" + dir + "' - running the simulation first (1-2 minutes)...");
            GAScheduler.main(new String[]{dir});
        }
        JTabbedPane tabs = build();
        if (png) { savePngs(); return; }
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Q8 - GA Scheduling in CloudSim: Results Dashboard");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); f.add(tabs); f.setSize(1150, 740); f.setLocationRelativeTo(null); f.setVisible(true);
        });
    }
}